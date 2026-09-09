package shortcutmanager.data.config;

import com.google.gson.*;
import shortcutmanager.model.Shortcut;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads VS Code's keybindings.json — the user's personal key overrides.
 *
 * VS Code separates defaults (buried in the app bundle) from user overrides
 * (in keybindings.json). We read the user file and show it as a "Custom"
 * category on top of whatever defaults we have in the database.
 *
 * Format:
 *   [ { "key": "ctrl+shift+p", "command": "workbench.action.showCommands" }, ... ]
 * Negative commands (disable a binding) start with "-" and are skipped.
 */
public class VSCodeConfigReader implements ConfigReader {

    private static final String[][] CONFIG_PATHS = {
            // Linux
            { System.getProperty("user.home") + "/.config/Code/User/keybindings.json" },
            { System.getProperty("user.home") + "/.config/Code - Insiders/User/keybindings.json" },
            { System.getProperty("user.home") + "/.config/VSCodium/User/keybindings.json" },
            // macOS
            { System.getProperty("user.home") + "/Library/Application Support/Code/User/keybindings.json" },
            { System.getProperty("user.home") + "/Library/Application Support/Code - Insiders/User/keybindings.json" },
            // Windows
            { System.getenv("APPDATA") != null
                    ? System.getenv("APPDATA") + "\\Code\\User\\keybindings.json" : "" },
    };

    @Override
    public String getReaderId() { return "vscode"; }

    @Override
    public boolean isInstalled() {
        return findConfigFile() != null;
    }

    @Override
    public List<Shortcut> readShortcuts() {
        Path config = findConfigFile();
        if (config == null) return Collections.emptyList();

        List<Shortcut> result = new ArrayList<>();
        try {
            String raw = Files.readString(config);
            // VS Code allows comments in JSON (JSONC), strip them first
            raw = stripJsonComments(raw);

            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String key = obj.has("key") ? obj.get("key").getAsString() : "";
                String command = obj.has("command") ? obj.get("command").getAsString() : "";

                // Skip "disable" entries (command starts with "-")
                if (command.startsWith("-") || key.isEmpty() || command.isEmpty()) continue;

                // Convert VS Code key syntax to readable form
                String normalizedKey = normalizeKey(key);
                String readableAction = commandToReadable(command);
                String when = obj.has("when") ? obj.get("when").getAsString() : "";
                String category = deriveCategory(command, when);

                result.add(new Shortcut(readableAction, normalizedKey, category, true));
            }
        } catch (Exception e) {
            System.err.println("[VSCodeConfigReader] Failed to parse " + config + ": " + e.getMessage());
        }
        return result;
    }

    @Override
    public boolean writeShortcut(Shortcut shortcut) {
        Path config = findConfigFile();
        if (config == null) return false;
        try {
            String raw = Files.readString(config);
            raw = stripJsonComments(raw);
            JsonArray arr;
            try {
                arr = JsonParser.parseString(raw).getAsJsonArray();
            } catch (Exception e) {
                arr = new JsonArray();
            }

            // Find existing entry by action name and update, or append
            String commandId = readableToCommandHint(shortcut.getAction());
            boolean found = false;
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("command") && obj.get("command").getAsString().equals(commandId)) {
                    obj.addProperty("key", shortcut.getKeys().toLowerCase().replace("+", "+"));
                    found = true;
                    break;
                }
            }
            if (!found) {
                JsonObject newEntry = new JsonObject();
                newEntry.addProperty("key", shortcut.getKeys().toLowerCase());
                newEntry.addProperty("command", commandId);
                arr.add(newEntry);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(config, gson.toJson(arr));
            return true;
        } catch (Exception e) {
            System.err.println("[VSCodeConfigReader] Failed to write: " + e.getMessage());
            return false;
        }
    }

    public Path findConfigFile() {
        for (String[] paths : CONFIG_PATHS) {
            for (String p : paths) {
                if (p == null || p.isEmpty()) continue;
                Path path = Path.of(p);
                if (Files.exists(path)) return path;
            }
        }
        return null;
    }

    /** Strip // and /* comments from JSONC */
    private String stripJsonComments(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char next = i + 1 < json.length() ? json.charAt(i + 1) : 0;
            if (inLineComment) {
                if (c == '\n') { inLineComment = false; result.append(c); }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (!inString && c == '/' && next == '/') { inLineComment = true; i++; continue; }
            if (!inString && c == '/' && next == '*') { inBlockComment = true; i++; continue; }
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            result.append(c);
        }
        return result.toString();
    }

    /** Convert VS Code key syntax (ctrl+shift+p) to display format (Ctrl+Shift+P) */
    private String normalizeKey(String key) {
        String[] parts = key.split("\\s+"); // handle chord sequences
        String primary = parts[0]; // use first chord for now
        String[] tokens = primary.split("\\+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i].trim();
            if (t.isEmpty()) continue;
            sb.append(capitalize(t));
            if (i < tokens.length - 1) sb.append("+");
        }
        return sb.toString();
    }

    private String capitalize(String s) {
        if (s.length() == 1) return s.toUpperCase();
        switch (s.toLowerCase()) {
            case "ctrl": return "Ctrl";
            case "shift": return "Shift";
            case "alt": return "Alt";
            case "cmd": case "meta": return "Cmd";
            case "up": return "↑";
            case "down": return "↓";
            case "left": return "←";
            case "right": return "→";
            case "escape": case "esc": return "Esc";
            case "backspace": return "Backspace";
            case "delete": return "Delete";
            case "enter": return "Enter";
            case "tab": return "Tab";
            case "space": return "Space";
            default: return s.substring(0, 1).toUpperCase() + s.substring(1);
        }
    }

    /** Convert a VS Code command ID to a human-readable action name */
    private String commandToReadable(String command) {
        // Strip known namespace prefixes
        String stripped = command
                .replaceFirst("^workbench\\.action\\.", "")
                .replaceFirst("^editor\\.action\\.", "")
                .replaceFirst("^workbench\\.debug\\.action\\.", "")
                .replaceFirst("^workbench\\.extensions\\.action\\.", "")
                .replaceFirst("^git\\.", "Git: ")
                .replaceFirst("^terminal\\.", "Terminal: ")
                .replaceFirst("^explorer\\.", "Explorer: ");

        // Convert camelCase to Title Case with spaces
        String spaced = stripped.replaceAll("([A-Z])", " $1").trim();
        // Capitalize first letter
        if (!spaced.isEmpty()) {
            spaced = spaced.substring(0, 1).toUpperCase() + spaced.substring(1);
        }
        return spaced.isEmpty() ? command : spaced;
    }

    private String readableToCommandHint(String action) {
        // Best-effort reverse mapping for writes
        return action.toLowerCase().replaceAll("\\s+", ".");
    }

    /** Derive a display category from the command namespace and when-clause */
    private String deriveCategory(String command, String when) {
        if (command.startsWith("workbench.debug")) return "Debug";
        if (command.startsWith("editor.action.refactor") || command.contains("rename")) return "Refactoring";
        if (command.startsWith("git.")) return "Git";
        if (command.startsWith("terminal.")) return "Terminal";
        if (command.startsWith("workbench.action.find") || command.contains("search")) return "Search";
        if (command.startsWith("editor.action.format")) return "Editing";
        if (command.startsWith("workbench.action.open") || command.contains("file")) return "File";
        if (command.startsWith("editor.")) return "Editing";
        if (command.startsWith("workbench.")) return "General";
        return "Custom";
    }
}
