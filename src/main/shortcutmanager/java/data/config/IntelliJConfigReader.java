package shortcutmanager.data.config;

import shortcutmanager.model.Shortcut;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Reads IntelliJ IDEA (and other JetBrains IDE) custom keymap XML files.
 *
 * JetBrains IDEs store user custom keymaps at:
 *   ~/.config/JetBrains/<product><version>/keymaps/<name>.xml
 *
 * The XML format looks like:
 *   <keymap name="My Keymap" parent="$default">
 *     <action id="CopyElement">
 *       <keyboard-shortcut first-keystroke="ctrl shift c"/>
 *     </action>
 *   </keymap>
 *
 * Action IDs are JetBrains internal identifiers. We map common ones to
 * human-readable names; unmapped ones are title-cased from the ID.
 */
public class IntelliJConfigReader implements ConfigReader {

    private static final String HOME = System.getProperty("user.home");

    // Known JetBrains config directory name patterns (without version suffix)
    private static final String[] PRODUCT_PREFIXES = {
            "IntelliJIdea", "WebStorm", "PyCharm", "CLion",
            "GoLand", "Rider", "DataGrip", "RubyMine", "PhpStorm"
    };

    // Mapping of common IntelliJ action IDs → human readable names
    private static final Map<String, String> ACTION_NAMES = new HashMap<>();
    static {
        ACTION_NAMES.put("SearchEverywhere", "Search Everywhere");
        ACTION_NAMES.put("GotoFile", "Go to File");
        ACTION_NAMES.put("GotoClass", "Go to Class");
        ACTION_NAMES.put("GotoSymbol", "Go to Symbol");
        ACTION_NAMES.put("GotoDeclaration", "Go to Declaration");
        ACTION_NAMES.put("GotoImplementation", "Go to Implementation");
        ACTION_NAMES.put("RecentFiles", "Recent Files");
        ACTION_NAMES.put("RecentLocations", "Recent Locations");
        ACTION_NAMES.put("CodeCompletion", "Code Completion");
        ACTION_NAMES.put("SmartTypeCompletion", "Smart Completion");
        ACTION_NAMES.put("RenameElement", "Rename");
        ACTION_NAMES.put("Refactorings.QuickListPopupAction", "Refactor This");
        ACTION_NAMES.put("ExtractMethod", "Extract Method");
        ACTION_NAMES.put("ExtractVariable", "Extract Variable");
        ACTION_NAMES.put("OptimizeImports", "Optimize Imports");
        ACTION_NAMES.put("ReformatCode", "Format Code");
        ACTION_NAMES.put("Run", "Run");
        ACTION_NAMES.put("Debug", "Debug");
        ACTION_NAMES.put("ToggleLineBreakpoint", "Toggle Breakpoint");
        ACTION_NAMES.put("StepOver", "Step Over");
        ACTION_NAMES.put("StepInto", "Step Into");
        ACTION_NAMES.put("StepOut", "Step Out");
        ACTION_NAMES.put("Resume", "Resume Program");
        ACTION_NAMES.put("FindUsages", "Find Usages");
        ACTION_NAMES.put("FindInPath", "Find in Path");
        ACTION_NAMES.put("Replace", "Replace");
        ACTION_NAMES.put("ReplaceInPath", "Replace in Path");
        ACTION_NAMES.put("CommentByLineComment", "Comment Line");
        ACTION_NAMES.put("CommentByBlockComment", "Block Comment");
        ACTION_NAMES.put("MoveLineDown", "Move Line Down");
        ACTION_NAMES.put("MoveLineUp", "Move Line Up");
        ACTION_NAMES.put("DuplicatesForm", "Find Duplicates");
        ACTION_NAMES.put("ShowIntentionActions", "Show Intention Actions");
        ACTION_NAMES.put("QuickJavaDoc", "Quick Documentation");
        ACTION_NAMES.put("ParameterInfo", "Parameter Info");
        ACTION_NAMES.put("TypeHierarchy", "Type Hierarchy");
        ACTION_NAMES.put("ActivateTerminalToolWindow", "Open Terminal");
        ACTION_NAMES.put("HideAllWindows", "Hide All Tool Windows");
        ACTION_NAMES.put("MaximizeToolWindow", "Maximize Tool Window");
        ACTION_NAMES.put("Vcs.UpdateProject", "Update Project (VCS)");
        ACTION_NAMES.put("Vcs.Push", "Push (VCS)");
        ACTION_NAMES.put("CheckinProject", "Commit (VCS)");
        ACTION_NAMES.put("Git.Log", "Show Git Log");
    }

    @Override
    public String getReaderId() { return "intellij"; }

    @Override
    public boolean isInstalled() {
        return findKeymapDirectory() != null;
    }

    @Override
    public List<Shortcut> readShortcuts() {
        Path keymapDir = findKeymapDirectory();
        if (keymapDir == null) return Collections.emptyList();

        List<Shortcut> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(keymapDir, "*.xml")) {
            for (Path xmlFile : stream) {
                result.addAll(parseKeymapXml(xmlFile));
            }
        } catch (Exception e) {
            System.err.println("[IntelliJConfigReader] Error reading keymap dir: " + e.getMessage());
        }
        return result;
    }

    @Override
    public boolean writeShortcut(Shortcut shortcut) {
        // Writing back to IntelliJ XML is complex and risky — not implemented for now
        return false;
    }

    private List<Shortcut> parseKeymapXml(Path xmlFile) {
        List<Shortcut> shortcuts = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile.toFile());
            doc.getDocumentElement().normalize();

            NodeList actions = doc.getElementsByTagName("action");
            for (int i = 0; i < actions.getLength(); i++) {
                Node actionNode = actions.item(i);
                if (actionNode.getNodeType() != Node.ELEMENT_NODE) continue;
                Element action = (Element) actionNode;
                String actionId = action.getAttribute("id");

                NodeList kbShortcuts = action.getElementsByTagName("keyboard-shortcut");
                for (int j = 0; j < kbShortcuts.getLength(); j++) {
                    Element kb = (Element) kbShortcuts.item(j);
                    String firstKeystroke = kb.getAttribute("first-keystroke");
                    if (firstKeystroke.isEmpty()) continue;

                    String normalizedKey = normalizeIntelliJKey(firstKeystroke);
                    String readableName = ACTION_NAMES.getOrDefault(actionId, actionIdToReadable(actionId));
                    String category = deriveCategory(actionId);

                    shortcuts.add(new Shortcut(readableName, normalizedKey, category, true));
                }
            }
        } catch (Exception e) {
            System.err.println("[IntelliJConfigReader] Failed to parse " + xmlFile + ": " + e.getMessage());
        }
        return shortcuts;
    }

    /** Find the JetBrains keymap directory for any installed product */
    private Path findKeymapDirectory() {
        List<Path> configRoots = getConfigRoots();
        for (Path root : configRoots) {
            if (!Files.exists(root)) continue;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                // Gather matching product dirs, sorted descending (newest version first)
                List<Path> matches = new ArrayList<>();
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    for (String prefix : PRODUCT_PREFIXES) {
                        if (name.startsWith(prefix)) {
                            matches.add(p);
                            break;
                        }
                    }
                }
                matches.sort(Comparator.comparing(Path::toString).reversed());
                for (Path match : matches) {
                    Path keymapDir = match.resolve("keymaps");
                    if (Files.exists(keymapDir)) return keymapDir;
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private List<Path> getConfigRoots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of(HOME, ".config", "JetBrains"));           // Linux
        roots.add(Path.of(HOME, "Library", "Application Support", "JetBrains")); // macOS
        String appData = System.getenv("APPDATA");
        if (appData != null) roots.add(Path.of(appData, "JetBrains")); // Windows
        return roots;
    }

    /** Convert IntelliJ keystroke syntax to display format */
    private String normalizeIntelliJKey(String keystroke) {
        // IntelliJ uses "ctrl shift c" (space-separated, lowercase)
        String[] tokens = keystroke.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            sb.append(capitalizeToken(tokens[i]));
            if (i < tokens.length - 1) sb.append("+");
        }
        return sb.toString();
    }

    private String capitalizeToken(String t) {
        switch (t.toLowerCase()) {
            case "ctrl": case "control": return "Ctrl";
            case "shift": return "Shift";
            case "alt": return "Alt";
            case "meta": case "cmd": return "Cmd";
            case "up": return "↑";
            case "down": return "↓";
            case "left": return "←";
            case "right": return "→";
            case "enter": return "Enter";
            case "escape": case "esc": return "Esc";
            case "backspace": return "Backspace";
            case "delete": case "del": return "Delete";
            case "tab": return "Tab";
            case "space": return "Space";
            case "f1": case "f2": case "f3": case "f4": case "f5": case "f6":
            case "f7": case "f8": case "f9": case "f10": case "f11": case "f12":
                return t.toUpperCase();
            default:
                return t.length() == 1 ? t.toUpperCase() : t;
        }
    }

    private String actionIdToReadable(String id) {
        // Split on dots and camelCase
        String last = id.contains(".") ? id.substring(id.lastIndexOf('.') + 1) : id;
        return last.replaceAll("([A-Z])", " $1").trim();
    }

    private String deriveCategory(String actionId) {
        String lower = actionId.toLowerCase();
        if (lower.contains("debug") || lower.contains("step") || lower.contains("breakpoint") || lower.contains("resume")) return "Debug";
        if (lower.contains("refactor") || lower.contains("rename") || lower.contains("extract")) return "Refactoring";
        if (lower.contains("find") || lower.contains("search") || lower.contains("replace") || lower.contains("goto")) return "Navigation";
        if (lower.contains("vcs") || lower.contains("git") || lower.contains("commit") || lower.contains("push")) return "VCS";
        if (lower.contains("run") || lower.contains("build") || lower.contains("compile")) return "Run";
        if (lower.contains("format") || lower.contains("comment") || lower.contains("indent") || lower.contains("completion")) return "Editing";
        if (lower.contains("terminal") || lower.contains("tool") || lower.contains("window")) return "View";
        return "Custom";
    }
}