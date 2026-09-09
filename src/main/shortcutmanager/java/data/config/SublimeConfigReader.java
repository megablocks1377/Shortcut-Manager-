package shortcutmanager.data.config;

import com.google.gson.*;
import shortcutmanager.model.Shortcut;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Reads Sublime Text's user keymap file.
 *
 * Format (JSONC — allows comments):
 *   [
 *     { "keys": ["ctrl+shift+p"], "command": "show_overlay",
 *       "args": { "overlay": "command_palette" } },
 *     { "keys": ["ctrl+p"],       "command": "show_overlay",
 *       "args": { "overlay": "goto_anything" } }
 *   ]
 *
 * Keys are already in a fairly readable format. Commands are snake_case.
 */
public class SublimeConfigReader implements ConfigReader {

    private static final String HOME = System.getProperty("user.home");
    private static final String[] CONFIG_PATHS = {
            HOME + "/.config/sublime-text/Packages/User/Default (Linux).sublime-keymap",
            HOME + "/.config/sublime-text-3/Packages/User/Default (Linux).sublime-keymap",
            HOME + "/Library/Application Support/Sublime Text/Packages/User/Default (OSX).sublime-keymap",
            HOME + "/Library/Application Support/Sublime Text 3/Packages/User/Default (OSX).sublime-keymap",
            System.getenv("APPDATA") != null
                    ? System.getenv("APPDATA") + "\\Sublime Text\\Packages\\User\\Default (Windows).sublime-keymap" : "",
    };

    @Override
    public String getReaderId() { return "sublime"; }

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
            raw = stripJsonComments(raw);
            if (raw.isBlank()) return result;

            JsonArray arr = JsonParser.parseString(raw).getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();

                if (!obj.has("keys") || !obj.has("command")) continue;
                JsonArray keys = obj.getAsJsonArray("keys");
                String command = obj.get("command").getAsString();
                JsonObject args = obj.has("args") ? obj.getAsJsonObject("args") : null;

                String keyStr = normalizeKey(keys.get(0).getAsString());
                String action = commandToReadable(command, args);
                String category = deriveCategory(command, args);

                result.add(new Shortcut(action, keyStr, category, true));
            }
        } catch (Exception e) {
            System.err.println("[SublimeConfigReader] Failed to parse: " + e.getMessage());
        }
        return result;
    }

    @Override
    public boolean writeShortcut(Shortcut shortcut) {
        // Writing back to Sublime keymap not implemented
        return false;
    }

    private Path findConfigFile() {
        for (String p : CONFIG_PATHS) {
            if (p == null || p.isEmpty()) continue;
            Path path = Path.of(p);
            if (Files.exists(path)) return path;
        }
        return null;
    }

    /** Strip // and /* style comments from JSONC */
    private String stripJsonComments(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false, inLine = false, inBlock = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            char next = i + 1 < json.length() ? json.charAt(i + 1) : 0;
            if (inLine) { if (c == '\n') { inLine = false; result.append(c); } continue; }
            if (inBlock) { if (c == '*' && next == '/') { inBlock = false; i++; } continue; }
            if (!inString && c == '/' && next == '/') { inLine = true; i++; continue; }
            if (!inString && c == '/' && next == '*') { inBlock = true; i++; continue; }
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            result.append(c);
        }
        return result.toString();
    }

    /**
     * Sublime keys are mostly already human-readable ("ctrl+shift+p").
     * Just normalize capitalization.
     */
    private String normalizeKey(String key) {
        String[] tokens = key.split("\\+");
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
            case "super": case "cmd": return "Cmd";
            case "up": return "↑";
            case "down": return "↓";
            case "left": return "←";
            case "right": return "→";
            case "enter": case "return": return "Enter";
            case "escape": case "esc": return "Esc";
            case "backspace": return "Backspace";
            case "delete": return "Delete";
            case "tab": return "Tab";
            case "space": return "Space";
            default: return t.length() == 1 ? t.toUpperCase() : t;
        }
    }

    /** Convert snake_case command + args to a readable name */
    private String commandToReadable(String command, JsonObject args) {
        // Some commands are more meaningful from their args
        if (command.equals("show_overlay") && args != null) {
            String overlay = args.has("overlay") ? args.get("overlay").getAsString() : "";
            switch (overlay) {
                case "command_palette": return "Command Palette";
                case "goto_anything": return "Go to Anything";
                case "goto_symbol": return "Go to Symbol";
                case "goto_symbol_in_project": return "Go to Symbol in Project";
            }
        }
        if (command.equals("show_panel") && args != null) {
            String panel = args.has("panel") ? args.get("panel").getAsString() : "";
            if (panel.equals("find")) return "Find";
            if (panel.equals("replace")) return "Find and Replace";
            if (panel.equals("console")) return "Toggle Console";
        }

        // Generic: snake_case → Title Case
        return Arrays.stream(command.split("_"))
                .map(w -> w.isEmpty() ? w : w.substring(0, 1).toUpperCase() + w.substring(1))
                .reduce("", (a, b) -> a + (a.isEmpty() ? "" : " ") + b);
    }

    private String deriveCategory(String command, JsonObject args) {
        if (command.contains("find") || command.contains("replace") || command.equals("show_overlay")) return "Search";
        if (command.contains("build") || command.contains("exec")) return "Build";
        if (command.contains("fold") || command.contains("indent")) return "Editing";
        if (command.contains("goto") || command.contains("jump") || command.contains("navigate")) return "Navigation";
        if (command.contains("panel") || command.contains("view") || command.contains("layout")) return "View";
        return "Custom";
    }
}
