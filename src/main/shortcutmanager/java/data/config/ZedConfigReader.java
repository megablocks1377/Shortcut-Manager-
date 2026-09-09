package shortcutmanager.data.config;

import com.google.gson.*;
import shortcutmanager.model.Shortcut;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads Zed editor's keymap.json.
 *
 * Format:
 *   [
 *     {
 *       "context": "Editor",
 *       "bindings": {
 *         "ctrl-shift-p": "command_palette::Toggle",
 *         "ctrl-p":       "file_finder::Toggle"
 *       }
 *     }
 *   ]
 *
 * Keys use hyphens as separators. Commands are namespaced with "::".
 */
public class ZedConfigReader implements ConfigReader {

    private static final String HOME = System.getProperty("user.home");
    private static final String[] CONFIG_PATHS = {
            HOME + "/.config/zed/keymap.json",
            HOME + "/Library/Application Support/Zed/keymap.json",
    };

    @Override
    public String getReaderId() { return "zed"; }

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
            if (raw.isBlank()) return result;

            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject section = el.getAsJsonObject();
                String context = section.has("context") ? section.get("context").getAsString() : "General";
                String category = contextToCategory(context);

                if (!section.has("bindings")) continue;
                JsonObject bindings = section.getAsJsonObject("bindings");

                for (Map.Entry<String, JsonElement> entry : bindings.entrySet()) {
                    String key = normalizeKey(entry.getKey());
                    String command = entry.getValue().getAsString();
                    String action = commandToReadable(command);
                    result.add(new Shortcut(action, key, category, true));
                }
            }
        } catch (Exception e) {
            System.err.println("[ZedConfigReader] Failed to parse: " + e.getMessage());
        }
        return result;
    }

    @Override
    public boolean writeShortcut(Shortcut shortcut) {
        Path config = findConfigFile();
        if (config == null) return false;
        // For now, writing back to Zed keymap is not implemented
        // (would need to find the right context block or create one)
        return false;
    }

    private Path findConfigFile() {
        for (String p : CONFIG_PATHS) {
            Path path = Path.of(p);
            if (Files.exists(path)) return path;
        }
        return null;
    }

    /** Convert Zed key syntax (ctrl-shift-p) to display format (Ctrl+Shift+P) */
    private String normalizeKey(String key) {
        String[] tokens = key.split("-(?=[^-]|$)");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            sb.append(capitalizeToken(tokens[i].trim()));
            if (i < tokens.length - 1) sb.append("+");
        }
        return sb.toString();
    }

    private String capitalizeToken(String t) {
        switch (t.toLowerCase()) {
            case "ctrl": case "control": return "Ctrl";
            case "shift": return "Shift";
            case "alt": return "Alt";
            case "cmd": case "meta": return "Cmd";
            case "up": return "↑";
            case "down": return "↓";
            case "left": return "←";
            case "right": return "→";
            case "enter": return "Enter";
            case "escape": case "esc": return "Esc";
            case "backspace": return "Backspace";
            case "delete": return "Delete";
            case "tab": return "Tab";
            case "space": return "Space";
            default:
                return t.length() == 1 ? t.toUpperCase() : t;
        }
    }

    /** Convert "command_palette::Toggle" → "Command Palette Toggle" */
    private String commandToReadable(String command) {
        String[] parts = command.split("::");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String spaced = part.replaceAll("_", " ");
            // Title case
            for (String word : spaced.split(" ")) {
                if (!word.isEmpty()) {
                    sb.append(word.substring(0, 1).toUpperCase())
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
                }
            }
        }
        return sb.toString().trim();
    }

    private String contextToCategory(String context) {
        switch (context) {
            case "Editor": return "Editor";
            case "Workspace": return "General";
            case "Terminal": return "Terminal";
            case "ProjectPanel": return "File Tree";
            default: return context.isEmpty() ? "General" : context;
        }
    }
}
