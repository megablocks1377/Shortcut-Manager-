package shortcutmanager.data;

import shortcutmanager.data.config.*;
import shortcutmanager.model.AppShortcuts;
import shortcutmanager.model.AppSettings;
import shortcutmanager.model.Shortcut;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Central data store for the application.
 *
 * Loading priority (highest to lowest):
 *   1. Config file reader  — reads from the app's own config (ground truth for user's setup)
 *   2. User custom edits   — changes the user made inside Shortcut Manager (~/.shortcut-manager/)
 *   3. Built-in defaults   — hardcoded baseline so every app has something to show
 *
 * Apps without a config reader fall back directly to 2 + 3.
 */
public class ShortcutDataStore {

    private static final String DATA_DIR = System.getProperty("user.home") + "/.shortcut-manager";
    private static final String CUSTOM_FILE = DATA_DIR + "/custom-shortcuts.json";
    private static final String SETTINGS_FILE = DATA_DIR + "/settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, AppShortcuts> appsMap = new LinkedHashMap<>();
    private final Map<String, ConfigReader> configReaders = new HashMap<>();
    private AppSettings settings;

    public ShortcutDataStore() {
        ensureDataDir();
        registerConfigReaders();
        loadBuiltinData();
        mergeConfigFileShortcuts();
        mergeUserCustomShortcuts();
        loadSettings();
    }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    private void ensureDataDir() {
        new File(DATA_DIR).mkdirs();
    }

    private void registerConfigReaders() {
        registerReader(new VSCodeConfigReader());
        registerReader(new IntelliJConfigReader());
        registerReader(new ZedConfigReader());
        registerReader(new SublimeConfigReader());
    }

    private void registerReader(ConfigReader reader) {
        // Map by reader ID and also by the app names that use it
        configReaders.put(reader.getReaderId(), reader);
    }

    /** Returns the config reader for a given app name, or null. */
    public ConfigReader getConfigReader(String appName) {
        switch (appName) {
            case "VS Code":       return configReaders.get("vscode");
            case "IntelliJ IDEA": case "WebStorm": case "PyCharm":
            case "CLion": case "GoLand":
                return configReaders.get("intellij");
            case "Zed":           return configReaders.get("zed");
            case "Sublime Text":  return configReaders.get("sublime");
            default:              return null;
        }
    }

    /**
     * Overlay config-file shortcuts on top of defaults.
     * Config file shortcuts are marked custom=true so the UI can distinguish them.
     * If the config file reader finds a matching action, its keys override the default.
     */
    private void mergeConfigFileShortcuts() {
        for (Map.Entry<String, AppShortcuts> entry : appsMap.entrySet()) {
            ConfigReader reader = getConfigReader(entry.getKey());
            if (reader == null || !reader.isInstalled()) continue;

            List<Shortcut> fromConfig = reader.readShortcuts();
            if (fromConfig.isEmpty()) continue;

            AppShortcuts app = entry.getValue();
            // Replace matching defaults with config-file versions; append new ones
            for (Shortcut configShortcut : fromConfig) {
                boolean replaced = false;
                for (Shortcut existing : app.getShortcuts()) {
                    if (existing.getAction().equalsIgnoreCase(configShortcut.getAction())) {
                        existing.setKeys(configShortcut.getKeys());
                        existing.setCustom(true);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    app.addShortcut(configShortcut);
                }
            }
        }
    }

    private void mergeUserCustomShortcuts() {
        File file = new File(CUSTOM_FILE);
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, List<Shortcut>>>() {}.getType();
            Map<String, List<Shortcut>> customMap = GSON.fromJson(reader, type);
            if (customMap == null) return;
            for (Map.Entry<String, List<Shortcut>> entry : customMap.entrySet()) {
                String appName = entry.getKey();
                if (!appsMap.containsKey(appName)) {
                    appsMap.put(appName, new AppShortcuts(appName, "⚙️"));
                }
                AppShortcuts app = appsMap.get(appName);
                for (Shortcut s : entry.getValue()) {
                    // User edits always win
                    app.getShortcuts().removeIf(existing -> existing.getAction().equals(s.getAction()));
                    app.addShortcut(s);
                }
            }
        } catch (Exception e) {
            System.err.println("[ShortcutDataStore] Failed to load custom shortcuts: " + e.getMessage());
        }
    }

    private void loadSettings() {
        File file = new File(SETTINGS_FILE);
        if (!file.exists()) { settings = new AppSettings(); return; }
        try (Reader reader = new FileReader(file)) {
            settings = GSON.fromJson(reader, AppSettings.class);
            if (settings == null) settings = new AppSettings();
        } catch (Exception e) {
            settings = new AppSettings();
        }
    }

    // -------------------------------------------------------------------------
    // Built-in default data
    // -------------------------------------------------------------------------

    private void loadBuiltinData() {
        appsMap.put("VS Code",        buildVSCode());
        appsMap.put("IntelliJ IDEA",  buildIntelliJ());
        appsMap.put("WebStorm",       buildWebStorm());
        appsMap.put("PyCharm",        buildPyCharm());
        appsMap.put("Zed",            buildZed());
        appsMap.put("Sublime Text",   buildSublime());
        appsMap.put("Chrome",         buildChrome());
        appsMap.put("Firefox",        buildFirefox());
        appsMap.put("Slack",          buildSlack());
        appsMap.put("Spotify",        buildSpotify());
        appsMap.put("Notion",         buildNotion());
        appsMap.put("Figma",          buildFigma());
        appsMap.put("Discord",        buildDiscord());
        appsMap.put("Terminal",       buildTerminal());
        appsMap.put("Postman",        buildPostman());
        appsMap.put("Obsidian",       buildObsidian());
        appsMap.put("Opera",          buildOpera());
        appsMap.put("Steam",          buildSteam());
        appsMap.put("OBS Studio",     buildOBS());
        appsMap.put("Paint.NET",      buildPaintNet());
        appsMap.put("GIMP",           buildGIMP());
        appsMap.put("Blender",        buildBlender());
        appsMap.put("FL Studio",      buildFLStudio());
        appsMap.put("WhatsApp",       buildWhatsApp());
        appsMap.put("Microsoft Word", buildWord());
        appsMap.put("Cursor",         buildCursor());
        appsMap.put("AnyDesk",        buildAnyDesk());
        appsMap.put("CapCut",         buildCapCut());
        appsMap.put("Photoshop",      buildPhotoshop());
        appsMap.put("darktable",      buildDarktable());
        appsMap.put("Zoom",           buildZoom());
        appsMap.put("Audacity",       buildAudacity());
    }

    private AppShortcuts buildVSCode() {
        AppShortcuts app = new AppShortcuts("VS Code", "💻");
        app.addShortcut(new Shortcut("Open Command Palette", "Ctrl+Shift+P", "General"));
        app.addShortcut(new Shortcut("Quick Open File", "Ctrl+P", "General"));
        app.addShortcut(new Shortcut("Toggle Terminal", "Ctrl+`", "General"));
        app.addShortcut(new Shortcut("Toggle Sidebar", "Ctrl+B", "General"));
        app.addShortcut(new Shortcut("Open Settings", "Ctrl+,", "General"));
        app.addShortcut(new Shortcut("New File", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Save File", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save All", "Ctrl+K S", "File"));
        app.addShortcut(new Shortcut("Close Editor", "Ctrl+W", "File"));
        app.addShortcut(new Shortcut("Reopen Closed Editor", "Ctrl+Shift+T", "File"));
        app.addShortcut(new Shortcut("Find in File", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Find & Replace", "Ctrl+H", "Search"));
        app.addShortcut(new Shortcut("Find in All Files", "Ctrl+Shift+F", "Search"));
        app.addShortcut(new Shortcut("Go to Line", "Ctrl+G", "Search"));
        app.addShortcut(new Shortcut("Go to Symbol", "Ctrl+Shift+O", "Search"));
        app.addShortcut(new Shortcut("Select All Occurrences", "Ctrl+Shift+L", "Editing"));
        app.addShortcut(new Shortcut("Add Cursor Above", "Ctrl+Alt+↑", "Editing"));
        app.addShortcut(new Shortcut("Add Cursor Below", "Ctrl+Alt+↓", "Editing"));
        app.addShortcut(new Shortcut("Move Line Down", "Alt+↓", "Editing"));
        app.addShortcut(new Shortcut("Move Line Up", "Alt+↑", "Editing"));
        app.addShortcut(new Shortcut("Copy Line Down", "Shift+Alt+↓", "Editing"));
        app.addShortcut(new Shortcut("Delete Line", "Ctrl+Shift+K", "Editing"));
        app.addShortcut(new Shortcut("Comment Line", "Ctrl+/", "Editing"));
        app.addShortcut(new Shortcut("Format Document", "Shift+Alt+F", "Editing"));
        app.addShortcut(new Shortcut("Run Debug", "F5", "Debug"));
        app.addShortcut(new Shortcut("Stop Debug", "Shift+F5", "Debug"));
        app.addShortcut(new Shortcut("Step Over", "F10", "Debug"));
        app.addShortcut(new Shortcut("Step Into", "F11", "Debug"));
        app.addShortcut(new Shortcut("Toggle Breakpoint", "F9", "Debug"));
        app.addShortcut(new Shortcut("Split Editor", "Ctrl+\\", "View"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl+=", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        return app;
    }

    private AppShortcuts buildIntelliJ() {
        AppShortcuts app = new AppShortcuts("IntelliJ IDEA", "🧠");
        app.addShortcut(new Shortcut("Search Everywhere", "Shift+Shift", "Navigation"));
        app.addShortcut(new Shortcut("Go to File", "Ctrl+Shift+N", "Navigation"));
        app.addShortcut(new Shortcut("Go to Class", "Ctrl+N", "Navigation"));
        app.addShortcut(new Shortcut("Go to Symbol", "Ctrl+Alt+Shift+N", "Navigation"));
        app.addShortcut(new Shortcut("Go to Declaration", "Ctrl+B", "Navigation"));
        app.addShortcut(new Shortcut("Go to Implementation", "Ctrl+Alt+B", "Navigation"));
        app.addShortcut(new Shortcut("Recent Files", "Ctrl+E", "Navigation"));
        app.addShortcut(new Shortcut("Code Completion", "Ctrl+Space", "Editing"));
        app.addShortcut(new Shortcut("Smart Completion", "Ctrl+Shift+Space", "Editing"));
        app.addShortcut(new Shortcut("Refactor This", "Ctrl+Alt+Shift+T", "Refactoring"));
        app.addShortcut(new Shortcut("Rename", "Shift+F6", "Refactoring"));
        app.addShortcut(new Shortcut("Extract Method", "Ctrl+Alt+M", "Refactoring"));
        app.addShortcut(new Shortcut("Optimize Imports", "Ctrl+Alt+O", "Editing"));
        app.addShortcut(new Shortcut("Format Code", "Ctrl+Alt+L", "Editing"));
        app.addShortcut(new Shortcut("Run", "Shift+F10", "Run/Debug"));
        app.addShortcut(new Shortcut("Debug", "Shift+F9", "Run/Debug"));
        app.addShortcut(new Shortcut("Toggle Breakpoint", "Ctrl+F8", "Run/Debug"));
        app.addShortcut(new Shortcut("Step Over", "F8", "Run/Debug"));
        app.addShortcut(new Shortcut("Step Into", "F7", "Run/Debug"));
        app.addShortcut(new Shortcut("Find Usages", "Alt+F7", "General"));
        app.addShortcut(new Shortcut("Find in Path", "Ctrl+Shift+F", "General"));
        return app;
    }

    private AppShortcuts buildWebStorm() {
        AppShortcuts app = new AppShortcuts("WebStorm", "🌐");
        app.addShortcut(new Shortcut("Search Everywhere", "Shift+Shift", "Navigation"));
        app.addShortcut(new Shortcut("Go to File", "Ctrl+Shift+N", "Navigation"));
        app.addShortcut(new Shortcut("Go to Declaration", "Ctrl+B", "Navigation"));
        app.addShortcut(new Shortcut("Find in Path", "Ctrl+Shift+F", "Search"));
        app.addShortcut(new Shortcut("Run", "Shift+F10", "Run"));
        app.addShortcut(new Shortcut("Debug", "Shift+F9", "Run"));
        app.addShortcut(new Shortcut("Reformat Code", "Ctrl+Alt+L", "Editing"));
        app.addShortcut(new Shortcut("Rename", "Shift+F6", "Refactoring"));
        app.addShortcut(new Shortcut("Open Terminal", "Alt+F12", "View"));
        return app;
    }

    private AppShortcuts buildPyCharm() {
        AppShortcuts app = new AppShortcuts("PyCharm", "🐍");
        app.addShortcut(new Shortcut("Search Everywhere", "Shift+Shift", "Navigation"));
        app.addShortcut(new Shortcut("Go to File", "Ctrl+Shift+N", "Navigation"));
        app.addShortcut(new Shortcut("Run", "Shift+F10", "Run"));
        app.addShortcut(new Shortcut("Debug", "Shift+F9", "Run"));
        app.addShortcut(new Shortcut("Run Python Console", "Alt+F12", "Run"));
        app.addShortcut(new Shortcut("Reformat Code", "Ctrl+Alt+L", "Editing"));
        app.addShortcut(new Shortcut("Rename", "Shift+F6", "Refactoring"));
        app.addShortcut(new Shortcut("Find in Path", "Ctrl+Shift+F", "Search"));
        return app;
    }

    private AppShortcuts buildZed() {
        AppShortcuts app = new AppShortcuts("Zed", "⚡");
        app.addShortcut(new Shortcut("Command Palette", "Ctrl+Shift+P", "General"));
        app.addShortcut(new Shortcut("Go to File", "Ctrl+P", "Navigation"));
        app.addShortcut(new Shortcut("Go to Symbol", "Ctrl+Shift+O", "Navigation"));
        app.addShortcut(new Shortcut("Find in File", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Find in Project", "Ctrl+Shift+F", "Search"));
        app.addShortcut(new Shortcut("Toggle Terminal", "Ctrl+`", "General"));
        app.addShortcut(new Shortcut("Split Pane", "Ctrl+\\", "View"));
        app.addShortcut(new Shortcut("Toggle Project Panel", "Ctrl+Shift+E", "View"));
        app.addShortcut(new Shortcut("Format Document", "Ctrl+Shift+I", "Editing"));
        app.addShortcut(new Shortcut("Comment Line", "Ctrl+/", "Editing"));
        return app;
    }

    private AppShortcuts buildSublime() {
        AppShortcuts app = new AppShortcuts("Sublime Text", "✏️");
        app.addShortcut(new Shortcut("Command Palette", "Ctrl+Shift+P", "General"));
        app.addShortcut(new Shortcut("Go to Anything", "Ctrl+P", "Navigation"));
        app.addShortcut(new Shortcut("Go to Line", "Ctrl+G", "Navigation"));
        app.addShortcut(new Shortcut("Go to Symbol", "Ctrl+R", "Navigation"));
        app.addShortcut(new Shortcut("Find", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Find & Replace", "Ctrl+H", "Search"));
        app.addShortcut(new Shortcut("Find in Files", "Ctrl+Shift+F", "Search"));
        app.addShortcut(new Shortcut("Multiple Cursors", "Ctrl+D", "Editing"));
        app.addShortcut(new Shortcut("Select Line", "Ctrl+L", "Editing"));
        app.addShortcut(new Shortcut("Delete Line", "Ctrl+Shift+K", "Editing"));
        app.addShortcut(new Shortcut("Comment Line", "Ctrl+/", "Editing"));
        app.addShortcut(new Shortcut("Duplicate Line", "Ctrl+Shift+D", "Editing"));
        app.addShortcut(new Shortcut("Indent", "Ctrl+]", "Editing"));
        app.addShortcut(new Shortcut("Unindent", "Ctrl+[", "Editing"));
        app.addShortcut(new Shortcut("Build", "Ctrl+B", "Run"));
        return app;
    }

    private AppShortcuts buildChrome() {
        AppShortcuts app = new AppShortcuts("Chrome", "🌐");
        app.addShortcut(new Shortcut("New Tab", "Ctrl+T", "Tabs"));
        app.addShortcut(new Shortcut("Close Tab", "Ctrl+W", "Tabs"));
        app.addShortcut(new Shortcut("Reopen Closed Tab", "Ctrl+Shift+T", "Tabs"));
        app.addShortcut(new Shortcut("Next Tab", "Ctrl+Tab", "Tabs"));
        app.addShortcut(new Shortcut("Previous Tab", "Ctrl+Shift+Tab", "Tabs"));
        app.addShortcut(new Shortcut("New Window", "Ctrl+N", "Windows"));
        app.addShortcut(new Shortcut("New Incognito Window", "Ctrl+Shift+N", "Windows"));
        app.addShortcut(new Shortcut("Address Bar", "Ctrl+L", "Navigation"));
        app.addShortcut(new Shortcut("Back", "Alt+←", "Navigation"));
        app.addShortcut(new Shortcut("Forward", "Alt+→", "Navigation"));
        app.addShortcut(new Shortcut("Reload", "Ctrl+R", "Navigation"));
        app.addShortcut(new Shortcut("Hard Reload", "Ctrl+Shift+R", "Navigation"));
        app.addShortcut(new Shortcut("Find in Page", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Developer Tools", "Ctrl+Shift+I", "Dev"));
        app.addShortcut(new Shortcut("View Source", "Ctrl+U", "Dev"));
        app.addShortcut(new Shortcut("Console", "Ctrl+Shift+J", "Dev"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl++", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        app.addShortcut(new Shortcut("Full Screen", "F11", "View"));
        app.addShortcut(new Shortcut("History", "Ctrl+H", "General"));
        app.addShortcut(new Shortcut("Downloads", "Ctrl+J", "General"));
        return app;
    }

    private AppShortcuts buildFirefox() {
        AppShortcuts app = new AppShortcuts("Firefox", "🦊");
        app.addShortcut(new Shortcut("New Tab", "Ctrl+T", "Tabs"));
        app.addShortcut(new Shortcut("Close Tab", "Ctrl+W", "Tabs"));
        app.addShortcut(new Shortcut("Reopen Closed Tab", "Ctrl+Shift+T", "Tabs"));
        app.addShortcut(new Shortcut("New Window", "Ctrl+N", "Windows"));
        app.addShortcut(new Shortcut("Private Window", "Ctrl+Shift+P", "Windows"));
        app.addShortcut(new Shortcut("Address Bar", "Ctrl+L", "Navigation"));
        app.addShortcut(new Shortcut("Back", "Alt+←", "Navigation"));
        app.addShortcut(new Shortcut("Forward", "Alt+→", "Navigation"));
        app.addShortcut(new Shortcut("Reload", "Ctrl+R", "Navigation"));
        app.addShortcut(new Shortcut("Find in Page", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Developer Tools", "Ctrl+Shift+I", "Dev"));
        app.addShortcut(new Shortcut("View Source", "Ctrl+U", "Dev"));
        app.addShortcut(new Shortcut("History", "Ctrl+H", "General"));
        return app;
    }

    private AppShortcuts buildSlack() {
        AppShortcuts app = new AppShortcuts("Slack", "💬");
        app.addShortcut(new Shortcut("Quick Switcher", "Ctrl+K", "Navigation"));
        app.addShortcut(new Shortcut("Jump to Unreads", "Ctrl+Shift+A", "Navigation"));
        app.addShortcut(new Shortcut("Next Unread", "Alt+Shift+↓", "Navigation"));
        app.addShortcut(new Shortcut("Go to DMs", "Ctrl+Shift+K", "Navigation"));
        app.addShortcut(new Shortcut("History Back", "Alt+←", "Navigation"));
        app.addShortcut(new Shortcut("New Message", "Ctrl+N", "Messaging"));
        app.addShortcut(new Shortcut("Send Message", "Enter", "Messaging"));
        app.addShortcut(new Shortcut("New Line", "Shift+Enter", "Messaging"));
        app.addShortcut(new Shortcut("Edit Last Message", "↑", "Messaging"));
        app.addShortcut(new Shortcut("Bold", "Ctrl+B", "Formatting"));
        app.addShortcut(new Shortcut("Italic", "Ctrl+I", "Formatting"));
        app.addShortcut(new Shortcut("Strikethrough", "Ctrl+Shift+X", "Formatting"));
        app.addShortcut(new Shortcut("Code", "Ctrl+Shift+C", "Formatting"));
        app.addShortcut(new Shortcut("Search", "Ctrl+F", "General"));
        app.addShortcut(new Shortcut("Preferences", "Ctrl+,", "General"));
        return app;
    }

    private AppShortcuts buildSpotify() {
        AppShortcuts app = new AppShortcuts("Spotify", "🎵");
        app.addShortcut(new Shortcut("Play/Pause", "Space", "Playback"));
        app.addShortcut(new Shortcut("Next Track", "Ctrl+→", "Playback"));
        app.addShortcut(new Shortcut("Previous Track", "Ctrl+←", "Playback"));
        app.addShortcut(new Shortcut("Volume Up", "Ctrl+↑", "Playback"));
        app.addShortcut(new Shortcut("Volume Down", "Ctrl+↓", "Playback"));
        app.addShortcut(new Shortcut("Shuffle", "Ctrl+S", "Playback"));
        app.addShortcut(new Shortcut("Repeat", "Ctrl+R", "Playback"));
        app.addShortcut(new Shortcut("Search", "Ctrl+L", "Navigation"));
        app.addShortcut(new Shortcut("Like Song", "Alt+Shift+B", "Library"));
        return app;
    }

    private AppShortcuts buildNotion() {
        AppShortcuts app = new AppShortcuts("Notion", "📝");
        app.addShortcut(new Shortcut("New Page", "Ctrl+N", "General"));
        app.addShortcut(new Shortcut("Quick Find", "Ctrl+P", "General"));
        app.addShortcut(new Shortcut("Toggle Sidebar", "Ctrl+\\", "General"));
        app.addShortcut(new Shortcut("Bold", "Ctrl+B", "Text"));
        app.addShortcut(new Shortcut("Italic", "Ctrl+I", "Text"));
        app.addShortcut(new Shortcut("Underline", "Ctrl+U", "Text"));
        app.addShortcut(new Shortcut("Code Inline", "Ctrl+E", "Text"));
        app.addShortcut(new Shortcut("Heading 1", "Ctrl+Alt+1", "Blocks"));
        app.addShortcut(new Shortcut("Heading 2", "Ctrl+Alt+2", "Blocks"));
        app.addShortcut(new Shortcut("Bullet List", "Ctrl+Shift+8", "Blocks"));
        app.addShortcut(new Shortcut("Numbered List", "Ctrl+Shift+7", "Blocks"));
        app.addShortcut(new Shortcut("Quote Block", "Ctrl+Shift+9", "Blocks"));
        app.addShortcut(new Shortcut("Link", "Ctrl+K", "General"));
        return app;
    }

    private AppShortcuts buildFigma() {
        AppShortcuts app = new AppShortcuts("Figma", "🎨");
        app.addShortcut(new Shortcut("Select Tool", "V", "Tools"));
        app.addShortcut(new Shortcut("Frame Tool", "F", "Tools"));
        app.addShortcut(new Shortcut("Rectangle Tool", "R", "Tools"));
        app.addShortcut(new Shortcut("Ellipse Tool", "O", "Tools"));
        app.addShortcut(new Shortcut("Text Tool", "T", "Tools"));
        app.addShortcut(new Shortcut("Pen Tool", "P", "Tools"));
        app.addShortcut(new Shortcut("Group Selection", "Ctrl+G", "Editing"));
        app.addShortcut(new Shortcut("Ungroup", "Ctrl+Shift+G", "Editing"));
        app.addShortcut(new Shortcut("Duplicate", "Ctrl+D", "Editing"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl++", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        app.addShortcut(new Shortcut("Fit to Screen", "Shift+1", "View"));
        app.addShortcut(new Shortcut("Show Grid", "Ctrl+'", "View"));
        app.addShortcut(new Shortcut("Show/Hide UI", "Ctrl+\\", "View"));
        return app;
    }

    private AppShortcuts buildDiscord() {
        AppShortcuts app = new AppShortcuts("Discord", "🎮");
        app.addShortcut(new Shortcut("Quick Switcher", "Ctrl+K", "Navigation"));
        app.addShortcut(new Shortcut("Mark Channel Read", "Escape", "Navigation"));
        app.addShortcut(new Shortcut("Mark Server Read", "Shift+Escape", "Navigation"));
        app.addShortcut(new Shortcut("Toggle Mute", "Ctrl+Shift+M", "Voice"));
        app.addShortcut(new Shortcut("Toggle Deafen", "Ctrl+Shift+D", "Voice"));
        app.addShortcut(new Shortcut("Push to Talk", "Defined in settings", "Voice"));
        app.addShortcut(new Shortcut("Upload File", "Ctrl+Shift+U", "Messaging"));
        app.addShortcut(new Shortcut("Create Reaction", "Ctrl+Shift+\\", "Messaging"));
        app.addShortcut(new Shortcut("Search", "Ctrl+F", "General"));
        app.addShortcut(new Shortcut("Preferences", "Ctrl+,", "General"));
        return app;
    }

    private AppShortcuts buildTerminal() {
        AppShortcuts app = new AppShortcuts("Terminal", "⌨️");
        app.addShortcut(new Shortcut("Auto-Complete", "Tab", "Navigation"));
        app.addShortcut(new Shortcut("Previous Command", "↑", "Navigation"));
        app.addShortcut(new Shortcut("Next Command", "↓", "Navigation"));
        app.addShortcut(new Shortcut("Beginning of Line", "Ctrl+A", "Navigation"));
        app.addShortcut(new Shortcut("End of Line", "Ctrl+E", "Navigation"));
        app.addShortcut(new Shortcut("Word Forward", "Alt+F", "Navigation"));
        app.addShortcut(new Shortcut("Word Backward", "Alt+B", "Navigation"));
        app.addShortcut(new Shortcut("Clear Line", "Ctrl+U", "Editing"));
        app.addShortcut(new Shortcut("Delete Word Back", "Ctrl+W", "Editing"));
        app.addShortcut(new Shortcut("Interrupt Process", "Ctrl+C", "Control"));
        app.addShortcut(new Shortcut("End of File / Exit", "Ctrl+D", "Control"));
        app.addShortcut(new Shortcut("Suspend Process", "Ctrl+Z", "Control"));
        app.addShortcut(new Shortcut("Clear Screen", "Ctrl+L", "General"));
        app.addShortcut(new Shortcut("Search History", "Ctrl+R", "General"));
        return app;
    }

    private AppShortcuts buildPostman() {
        AppShortcuts app = new AppShortcuts("Postman", "📮");
        app.addShortcut(new Shortcut("Send Request", "Ctrl+Enter", "Requests"));
        app.addShortcut(new Shortcut("Save Request", "Ctrl+S", "Requests"));
        app.addShortcut(new Shortcut("New Tab", "Ctrl+T", "General"));
        app.addShortcut(new Shortcut("Close Tab", "Ctrl+W", "General"));
        app.addShortcut(new Shortcut("New Request", "Ctrl+Alt+N", "General"));
        app.addShortcut(new Shortcut("Find & Replace", "Ctrl+H", "General"));
        app.addShortcut(new Shortcut("Command Palette", "Ctrl+Shift+P", "General"));
        app.addShortcut(new Shortcut("Toggle Sidebar", "Ctrl+\\", "View"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl++", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        return app;
    }

    private AppShortcuts buildObsidian() {
        AppShortcuts app = new AppShortcuts("Obsidian", "🔮");
        app.addShortcut(new Shortcut("Quick Switcher", "Ctrl+O", "Navigation"));
        app.addShortcut(new Shortcut("Command Palette", "Ctrl+P", "General"));
        app.addShortcut(new Shortcut("Search", "Ctrl+Shift+F", "Search"));
        app.addShortcut(new Shortcut("New Note", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Toggle Edit/Preview", "Ctrl+E", "View"));
        app.addShortcut(new Shortcut("Bold", "Ctrl+B", "Formatting"));
        app.addShortcut(new Shortcut("Italic", "Ctrl+I", "Formatting"));
        app.addShortcut(new Shortcut("Insert Link", "Ctrl+K", "Formatting"));
        app.addShortcut(new Shortcut("Toggle Sidebar", "Ctrl+\\", "View"));
        app.addShortcut(new Shortcut("Navigate Back", "Alt+←", "Navigation"));
        app.addShortcut(new Shortcut("Navigate Forward", "Alt+→", "Navigation"));
        return app;
    }
    
    private AppShortcuts buildOpera() {
        AppShortcuts app = new AppShortcuts("Opera", "🅾");
        app.addShortcut(new Shortcut("Add to Bookmarks", "Ctrl+D", "Bookmarks"));
        app.addShortcut(new Shortcut("Manage Bookmarks", "Ctrl+Shift+B", "Bookmarks"));
        app.addShortcut(new Shortcut("Back", "Alt+←", "Navigation"));
        app.addShortcut(new Shortcut("Forward", "Alt+→", "Navigation"));
        app.addShortcut(new Shortcut("Reload", "Ctrl+R", "Navigation"));
        app.addShortcut(new Shortcut("Reload Without Cache", "Ctrl+F5", "Navigation"));
        app.addShortcut(new Shortcut("Stop", "Esc", "Navigation"));
        app.addShortcut(new Shortcut("Speed Dial", "Alt+Home", "Navigation"));
        app.addShortcut(new Shortcut("Go to Parent Directory", "Ctrl+Backspace", "Navigation"));
        app.addShortcut(new Shortcut("New Tab", "Ctrl+T", "Tabs"));
        app.addShortcut(new Shortcut("New Tab in Island", "Alt+T", "Tabs"));
        app.addShortcut(new Shortcut("Close Tab", "Ctrl+W", "Tabs"));
        app.addShortcut(new Shortcut("Reopen Last Closed Tab", "Ctrl+Shift+T", "Tabs"));
        app.addShortcut(new Shortcut("Cycle Forward Through Tabs", "Ctrl+Tab", "Tabs"));
        app.addShortcut(new Shortcut("Cycle Backward Through Tabs", "Ctrl+Shift+Tab", "Tabs"));
        app.addShortcut(new Shortcut("Switch Right Through Tabs", "Ctrl+PageDown", "Tabs"));
        app.addShortcut(new Shortcut("Switch Left Through Tabs", "Ctrl+PageUp", "Tabs"));
        app.addShortcut(new Shortcut("Previously Active Tab", "Ctrl+`", "Tabs"));
        app.addShortcut(new Shortcut("Switch to Tab 1-9", "Ctrl+1...9", "Tabs"));
        app.addShortcut(new Shortcut("New Window", "Ctrl+N", "Windows"));
        app.addShortcut(new Shortcut("New Private Window", "Ctrl+Shift+N", "Windows"));
        app.addShortcut(new Shortcut("Close Window", "Ctrl+Shift+W", "Windows"));
        app.addShortcut(new Shortcut("Toggle Full Screen", "F11", "Windows"));
        app.addShortcut(new Shortcut("Exit", "Ctrl+Shift+X", "Windows"));
        app.addShortcut(new Shortcut("Find in Page", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Find Next", "Ctrl+G", "Search"));
        app.addShortcut(new Shortcut("Find Previous", "Ctrl+Shift+G", "Search"));
        app.addShortcut(new Shortcut("Focus Address Bar", "Ctrl+L", "Search"));
        app.addShortcut(new Shortcut("Remove Highlighted Suggestion", "Shift+Del", "Search"));
        app.addShortcut(new Shortcut("Open/Close Search Tabs", "Ctrl+Space", "Search"));
        app.addShortcut(new Shortcut("Print", "Ctrl+P", "Page"));
        app.addShortcut(new Shortcut("Print Without Preview", "Ctrl+Shift+P", "Page"));
        app.addShortcut(new Shortcut("Save Page", "Ctrl+S", "Page"));
        app.addShortcut(new Shortcut("View Page Source", "Ctrl+U", "Page"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl+=", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        app.addShortcut(new Shortcut("Reset Zoom to 100%", "Ctrl+0", "View"));
        app.addShortcut(new Shortcut("Cycle Theme Configurations", "Alt+Shift+T", "View"));
        app.addShortcut(new Shortcut("Focus Main Menu Button", "F10", "View"));
        app.addShortcut(new Shortcut("Focus Next Pane", "F6", "View"));
        app.addShortcut(new Shortcut("Focus Previous Pane", "Shift+F6", "View"));
        app.addShortcut(new Shortcut("Focus Page", "F9", "View"));
        app.addShortcut(new Shortcut("Developer Tools", "Ctrl+Shift+I", "Dev"));
        app.addShortcut(new Shortcut("Developer Tools Console", "Ctrl+Shift+J", "Dev"));
        app.addShortcut(new Shortcut("Inspect Element", "Ctrl+Shift+C", "Dev"));
        app.addShortcut(new Shortcut("Extensions", "Ctrl+Shift+E", "Dev"));
        app.addShortcut(new Shortcut("Show Task Manager", "Shift+Esc", "Dev"));
        app.addShortcut(new Shortcut("Open Settings", "Alt+P", "General"));
        app.addShortcut(new Shortcut("Open Main Menu", "Alt+F", "General"));
        app.addShortcut(new Shortcut("History", "Ctrl+H", "General"));
        app.addShortcut(new Shortcut("Downloads", "Ctrl+J", "General"));
        app.addShortcut(new Shortcut("Help", "F1", "General"));
        app.addShortcut(new Shortcut("Clear Browsing Data", "Ctrl+Shift+Del", "General"));
        app.addShortcut(new Shortcut("Snapshot", "Ctrl+Shift+5", "General"));
        app.addShortcut(new Shortcut("Open AI Chat in Side Panel", "Ctrl+O", "General"));
        app.addShortcut(new Shortcut("Toggle Recently Used Messenger", "Ctrl+Shift+M", "General"));
        return app;
    }

    private AppShortcuts buildSteam() {
        AppShortcuts app = new AppShortcuts("Steam", "🎮");
        app.addShortcut(new Shortcut("Take Screenshot (in game)", "F12", "In-Game"));
        app.addShortcut(new Shortcut("Open Steam Overlay", "Shift+Tab", "In-Game"));
        app.addShortcut(new Shortcut("Open Big Picture Mode", "Alt+Enter", "Client"));
        app.addShortcut(new Shortcut("Library", "Ctrl+2", "Navigation"));
        app.addShortcut(new Shortcut("Store", "Ctrl+1", "Navigation"));
        app.addShortcut(new Shortcut("Community", "Ctrl+3", "Navigation"));
        app.addShortcut(new Shortcut("Friends List", "Ctrl+F", "Navigation"));
        app.addShortcut(new Shortcut("Settings", "Ctrl+,", "Navigation"));
        app.addShortcut(new Shortcut("Search Library", "Ctrl+E", "Library"));
        app.addShortcut(new Shortcut("New Chat", "Ctrl+T", "Chat"));
        return app;
    }

    private AppShortcuts buildOBS() {
        AppShortcuts app = new AppShortcuts("OBS Studio", "🎥");
        app.addShortcut(new Shortcut("Start Recording", "Not bound by default", "Recording"));
        app.addShortcut(new Shortcut("Stop Recording", "Not bound by default", "Recording"));
        app.addShortcut(new Shortcut("Start Streaming", "Not bound by default", "Streaming"));
        app.addShortcut(new Shortcut("Stop Streaming", "Not bound by default", "Streaming"));
        app.addShortcut(new Shortcut("Studio Mode Toggle", "Not bound by default", "Studio Mode"));
        app.addShortcut(new Shortcut("Transition", "Not bound by default", "Studio Mode"));
        app.addShortcut(new Shortcut("New Scene", "Insert", "Scenes"));
        app.addShortcut(new Shortcut("Remove Selected Scene", "Delete", "Scenes"));
        app.addShortcut(new Shortcut("Switch to Scene", "Set per scene", "Scenes"));
        app.addShortcut(new Shortcut("Add Source", "Insert", "Sources"));
        app.addShortcut(new Shortcut("Remove Source", "Delete", "Sources"));
        app.addShortcut(new Shortcut("Mute Audio Source", "Set per source", "Audio"));
        app.addShortcut(new Shortcut("Push to Mute", "Set per source", "Audio"));
        app.addShortcut(new Shortcut("Push to Talk", "Set per source", "Audio"));
        app.addShortcut(new Shortcut("Settings", "Ctrl+,", "General"));
        app.addShortcut(new Shortcut("Exit", "Ctrl+Q", "General"));
        return app;
    }

    private AppShortcuts buildPaintNet() {
        AppShortcuts app = new AppShortcuts("Paint.NET", "🖌️");
        app.addShortcut(new Shortcut("New Image", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save As", "Ctrl+Shift+S", "File"));
        app.addShortcut(new Shortcut("Print", "Ctrl+P", "File"));
        app.addShortcut(new Shortcut("Close", "Ctrl+W", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Paste in New Image", "Ctrl+Shift+V", "Edit"));
        app.addShortcut(new Shortcut("Erase Selection", "Delete", "Edit"));
        app.addShortcut(new Shortcut("Fill Selection", "Backspace", "Edit"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Selection"));
        app.addShortcut(new Shortcut("Deselect", "Ctrl+D", "Selection"));
        app.addShortcut(new Shortcut("Invert Selection", "Ctrl+I", "Selection"));
        app.addShortcut(new Shortcut("Pencil Tool", "P", "Tools"));
        app.addShortcut(new Shortcut("Paintbrush", "B", "Tools"));
        app.addShortcut(new Shortcut("Eraser", "E", "Tools"));
        app.addShortcut(new Shortcut("Color Picker", "K", "Tools"));
        app.addShortcut(new Shortcut("Paint Bucket", "F", "Tools"));
        app.addShortcut(new Shortcut("Text Tool", "T", "Tools"));
        app.addShortcut(new Shortcut("Move Selection", "M", "Tools"));
        app.addShortcut(new Shortcut("Rectangle Select", "S", "Tools"));
        app.addShortcut(new Shortcut("Magic Wand", "W", "Tools"));
        app.addShortcut(new Shortcut("Zoom Tool", "Z", "Tools"));
        app.addShortcut(new Shortcut("New Layer", "Ctrl+Shift+N", "Layers"));
        app.addShortcut(new Shortcut("Duplicate Layer", "Ctrl+Shift+D", "Layers"));
        app.addShortcut(new Shortcut("Merge Layer Down", "Ctrl+M", "Layers"));
        app.addShortcut(new Shortcut("Layer Properties", "F4", "Layers"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl++", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        app.addShortcut(new Shortcut("Fit to Window", "Ctrl+B", "View"));
        app.addShortcut(new Shortcut("Actual Size", "Ctrl+Shift+A", "View"));
        return app;
    }

    private AppShortcuts buildGIMP() {
        AppShortcuts app = new AppShortcuts("GIMP", "🎨");
        app.addShortcut(new Shortcut("New Image", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Export As", "Ctrl+Shift+E", "File"));
        app.addShortcut(new Shortcut("Overwrite", "Ctrl+E", "File"));
        app.addShortcut(new Shortcut("Close", "Ctrl+W", "File"));
        app.addShortcut(new Shortcut("Quit", "Ctrl+Q", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Paste as New Image", "Ctrl+Shift+V", "Edit"));
        app.addShortcut(new Shortcut("Fill with FG Color", "Ctrl+,", "Edit"));
        app.addShortcut(new Shortcut("Fill with BG Color", "Ctrl+.", "Edit"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Selection"));
        app.addShortcut(new Shortcut("Deselect", "Ctrl+Shift+A", "Selection"));
        app.addShortcut(new Shortcut("Invert Selection", "Ctrl+I", "Selection"));
        app.addShortcut(new Shortcut("Float Selection", "Ctrl+Shift+L", "Selection"));
        app.addShortcut(new Shortcut("Move Tool", "M", "Tools"));
        app.addShortcut(new Shortcut("Rectangle Select", "R", "Tools"));
        app.addShortcut(new Shortcut("Ellipse Select", "E", "Tools"));
        app.addShortcut(new Shortcut("Free Select (Lasso)", "F", "Tools"));
        app.addShortcut(new Shortcut("Fuzzy Select (Magic Wand)", "U", "Tools"));
        app.addShortcut(new Shortcut("Crop Tool", "Shift+C", "Tools"));
        app.addShortcut(new Shortcut("Paintbrush", "P", "Tools"));
        app.addShortcut(new Shortcut("Pencil", "N", "Tools"));
        app.addShortcut(new Shortcut("Eraser", "Shift+E", "Tools"));
        app.addShortcut(new Shortcut("Bucket Fill", "Shift+B", "Tools"));
        app.addShortcut(new Shortcut("Color Picker", "O", "Tools"));
        app.addShortcut(new Shortcut("Text Tool", "T", "Tools"));
        app.addShortcut(new Shortcut("Zoom Tool", "Z", "Tools"));
        app.addShortcut(new Shortcut("Healing Tool", "H", "Tools"));
        app.addShortcut(new Shortcut("Clone Tool", "C", "Tools"));
        app.addShortcut(new Shortcut("New Layer", "Ctrl+Shift+N", "Layers"));
        app.addShortcut(new Shortcut("Duplicate Layer", "Ctrl+Shift+D", "Layers"));
        app.addShortcut(new Shortcut("Anchor Layer", "Ctrl+H", "Layers"));
        app.addShortcut(new Shortcut("Merge Visible Layers", "Ctrl+M", "Layers"));
        app.addShortcut(new Shortcut("Toggle Quick Mask", "Shift+Q", "View"));
        app.addShortcut(new Shortcut("Fit Image in Window", "Shift+Ctrl+J", "View"));
        app.addShortcut(new Shortcut("Zoom In", "+", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "-", "View"));
        app.addShortcut(new Shortcut("Toggle Fullscreen", "F11", "View"));
        return app;
    }

    private AppShortcuts buildBlender() {
        AppShortcuts app = new AppShortcuts("Blender", "🟧");
        app.addShortcut(new Shortcut("New File", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save As", "Ctrl+Shift+S", "File"));
        app.addShortcut(new Shortcut("Quit", "Ctrl+Q", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Shift+Z", "Edit"));
        app.addShortcut(new Shortcut("Search Menu", "F3", "Edit"));
        app.addShortcut(new Shortcut("Move Selection", "G", "Transform"));
        app.addShortcut(new Shortcut("Rotate Selection", "R", "Transform"));
        app.addShortcut(new Shortcut("Scale Selection", "S", "Transform"));
        app.addShortcut(new Shortcut("Constrain to X Axis", "X (after G/R/S)", "Transform"));
        app.addShortcut(new Shortcut("Constrain to Y Axis", "Y (after G/R/S)", "Transform"));
        app.addShortcut(new Shortcut("Constrain to Z Axis", "Z (after G/R/S)", "Transform"));
        app.addShortcut(new Shortcut("Apply (Confirm)", "Enter", "Transform"));
        app.addShortcut(new Shortcut("Cancel Transform", "Esc", "Transform"));
        app.addShortcut(new Shortcut("Select All", "A", "Selection"));
        app.addShortcut(new Shortcut("Deselect All", "Alt+A", "Selection"));
        app.addShortcut(new Shortcut("Invert Selection", "Ctrl+I", "Selection"));
        app.addShortcut(new Shortcut("Box Select", "B", "Selection"));
        app.addShortcut(new Shortcut("Circle Select", "C", "Selection"));
        app.addShortcut(new Shortcut("Lasso Select", "Ctrl+RMB Drag", "Selection"));
        app.addShortcut(new Shortcut("Add Object", "Shift+A", "Object"));
        app.addShortcut(new Shortcut("Delete Selection", "X", "Object"));
        app.addShortcut(new Shortcut("Duplicate", "Shift+D", "Object"));
        app.addShortcut(new Shortcut("Linked Duplicate", "Alt+D", "Object"));
        app.addShortcut(new Shortcut("Hide Selected", "H", "Object"));
        app.addShortcut(new Shortcut("Unhide All", "Alt+H", "Object"));
        app.addShortcut(new Shortcut("Toggle Edit Mode", "Tab", "Modes"));
        app.addShortcut(new Shortcut("Vertex Mode", "1 (Edit)", "Modes"));
        app.addShortcut(new Shortcut("Edge Mode", "2 (Edit)", "Modes"));
        app.addShortcut(new Shortcut("Face Mode", "3 (Edit)", "Modes"));
        app.addShortcut(new Shortcut("Extrude", "E (Edit)", "Modeling"));
        app.addShortcut(new Shortcut("Inset", "I (Edit)", "Modeling"));
        app.addShortcut(new Shortcut("Loop Cut", "Ctrl+R (Edit)", "Modeling"));
        app.addShortcut(new Shortcut("Bevel", "Ctrl+B (Edit)", "Modeling"));
        app.addShortcut(new Shortcut("Knife Tool", "K (Edit)", "Modeling"));
        app.addShortcut(new Shortcut("Front View", "Numpad 1", "Viewport"));
        app.addShortcut(new Shortcut("Side View", "Numpad 3", "Viewport"));
        app.addShortcut(new Shortcut("Top View", "Numpad 7", "Viewport"));
        app.addShortcut(new Shortcut("Toggle Perspective/Ortho", "Numpad 5", "Viewport"));
        app.addShortcut(new Shortcut("Frame Selected", "Numpad .", "Viewport"));
        app.addShortcut(new Shortcut("Toggle Quad View", "Ctrl+Alt+Q", "Viewport"));
        app.addShortcut(new Shortcut("Render Image", "F12", "Render"));
        app.addShortcut(new Shortcut("Render Animation", "Ctrl+F12", "Render"));
        app.addShortcut(new Shortcut("Play Animation", "Spacebar", "Animation"));
        app.addShortcut(new Shortcut("Insert Keyframe", "I", "Animation"));
        return app;
    }

    private AppShortcuts buildFLStudio() {
        AppShortcuts app = new AppShortcuts("FL Studio", "🎹");
        app.addShortcut(new Shortcut("Play / Stop", "Spacebar", "Transport"));
        app.addShortcut(new Shortcut("Stop Playback", "Ctrl+Space", "Transport"));
        app.addShortcut(new Shortcut("Toggle Record", "R", "Transport"));
        app.addShortcut(new Shortcut("Switch Pattern/Song Mode", "L", "Transport"));
        app.addShortcut(new Shortcut("Rewind to Start", "Home", "Transport"));
        app.addShortcut(new Shortcut("Toggle Metronome", "Ctrl+M", "Transport"));
        app.addShortcut(new Shortcut("New Project", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save As", "Ctrl+Shift+S", "File"));
        app.addShortcut(new Shortcut("Save New Version", "Ctrl+N (in dialog)", "File"));
        app.addShortcut(new Shortcut("Export MP3", "Ctrl+R", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Toggle Playlist", "F5", "Windows"));
        app.addShortcut(new Shortcut("Toggle Piano Roll", "F7", "Windows"));
        app.addShortcut(new Shortcut("Toggle Step Sequencer", "F6", "Windows"));
        app.addShortcut(new Shortcut("Toggle Mixer", "F9", "Windows"));
        app.addShortcut(new Shortcut("Toggle Browser", "F8", "Windows"));
        app.addShortcut(new Shortcut("Toggle Plugin Picker", "F10", "Windows"));
        app.addShortcut(new Shortcut("Open Project Info", "F11", "Windows"));
        app.addShortcut(new Shortcut("Toggle Fullscreen", "F12", "Windows"));
        app.addShortcut(new Shortcut("Pencil Tool", "P", "Piano Roll"));
        app.addShortcut(new Shortcut("Brush Tool", "B", "Piano Roll"));
        app.addShortcut(new Shortcut("Delete Tool", "D", "Piano Roll"));
        app.addShortcut(new Shortcut("Slice Tool", "S", "Piano Roll"));
        app.addShortcut(new Shortcut("Select Tool", "E", "Piano Roll"));
        app.addShortcut(new Shortcut("Zoom Tool", "Z", "Piano Roll"));
        return app;
    }

    private AppShortcuts buildWhatsApp() {
        AppShortcuts app = new AppShortcuts("WhatsApp", "💚");
        app.addShortcut(new Shortcut("New Chat", "Ctrl+N", "Chat"));
        app.addShortcut(new Shortcut("Search", "Ctrl+F", "Chat"));
        app.addShortcut(new Shortcut("Search Within Chat", "Ctrl+Shift+F", "Chat"));
        app.addShortcut(new Shortcut("Next Chat", "Ctrl+Tab", "Chat"));
        app.addShortcut(new Shortcut("Previous Chat", "Ctrl+Shift+Tab", "Chat"));
        app.addShortcut(new Shortcut("Mark as Read/Unread", "Ctrl+Alt+Shift+U", "Chat"));
        app.addShortcut(new Shortcut("Mute Chat", "Ctrl+Alt+Shift+M", "Chat"));
        app.addShortcut(new Shortcut("Archive Chat", "Ctrl+E", "Chat"));
        app.addShortcut(new Shortcut("Delete Chat", "Ctrl+Shift+Backspace", "Chat"));
        app.addShortcut(new Shortcut("Pin Chat", "Ctrl+Alt+Shift+P", "Chat"));
        app.addShortcut(new Shortcut("New Group", "Ctrl+Shift+N", "Groups"));
        app.addShortcut(new Shortcut("Profile and About", "Ctrl+P", "Settings"));
        app.addShortcut(new Shortcut("Settings", "Ctrl+,", "Settings"));
        app.addShortcut(new Shortcut("Status", "Ctrl+S", "Status"));
        app.addShortcut(new Shortcut("Bold", "Ctrl+B", "Formatting"));
        app.addShortcut(new Shortcut("Italic", "Ctrl+I", "Formatting"));
        app.addShortcut(new Shortcut("Strikethrough", "Ctrl+Shift+X", "Formatting"));
        app.addShortcut(new Shortcut("Monospace", "Ctrl+Shift+M", "Formatting"));
        return app;
    }

    private AppShortcuts buildWord() {
        AppShortcuts app = new AppShortcuts("Microsoft Word", "📄");
        app.addShortcut(new Shortcut("New Document", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save As", "F12", "File"));
        app.addShortcut(new Shortcut("Print", "Ctrl+P", "File"));
        app.addShortcut(new Shortcut("Close Document", "Ctrl+W", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Paste Special", "Ctrl+Alt+V", "Edit"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Edit"));
        app.addShortcut(new Shortcut("Find", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Find and Replace", "Ctrl+H", "Search"));
        app.addShortcut(new Shortcut("Go To", "Ctrl+G", "Search"));
        app.addShortcut(new Shortcut("Bold", "Ctrl+B", "Formatting"));
        app.addShortcut(new Shortcut("Italic", "Ctrl+I", "Formatting"));
        app.addShortcut(new Shortcut("Underline", "Ctrl+U", "Formatting"));
        app.addShortcut(new Shortcut("Strikethrough", "Alt+H, 4", "Formatting"));
        app.addShortcut(new Shortcut("Subscript", "Ctrl+=", "Formatting"));
        app.addShortcut(new Shortcut("Superscript", "Ctrl+Shift+=", "Formatting"));
        app.addShortcut(new Shortcut("Increase Font Size", "Ctrl+Shift+>", "Formatting"));
        app.addShortcut(new Shortcut("Decrease Font Size", "Ctrl+Shift+<", "Formatting"));
        app.addShortcut(new Shortcut("Align Left", "Ctrl+L", "Paragraph"));
        app.addShortcut(new Shortcut("Align Center", "Ctrl+E", "Paragraph"));
        app.addShortcut(new Shortcut("Align Right", "Ctrl+R", "Paragraph"));
        app.addShortcut(new Shortcut("Justify", "Ctrl+J", "Paragraph"));
        app.addShortcut(new Shortcut("Single Line Spacing", "Ctrl+1", "Paragraph"));
        app.addShortcut(new Shortcut("Double Line Spacing", "Ctrl+2", "Paragraph"));
        app.addShortcut(new Shortcut("1.5 Line Spacing", "Ctrl+5", "Paragraph"));
        app.addShortcut(new Shortcut("Insert Hyperlink", "Ctrl+K", "Insert"));
        app.addShortcut(new Shortcut("Insert Footnote", "Ctrl+Alt+F", "Insert"));
        app.addShortcut(new Shortcut("Insert Page Break", "Ctrl+Enter", "Insert"));
        app.addShortcut(new Shortcut("Word Count", "Ctrl+Shift+G", "Review"));
        app.addShortcut(new Shortcut("Spell Check", "F7", "Review"));
        app.addShortcut(new Shortcut("Thesaurus", "Shift+F7", "Review"));
        return app;
    }

    private AppShortcuts buildCursor() {
        AppShortcuts app = new AppShortcuts("Cursor", "🖱️");
        app.addShortcut(new Shortcut("AI Chat", "Ctrl+L", "AI"));
        app.addShortcut(new Shortcut("AI Inline Edit", "Ctrl+K", "AI"));
        app.addShortcut(new Shortcut("AI Composer (Agent)", "Ctrl+I", "AI"));
        app.addShortcut(new Shortcut("Toggle AI Chat", "Ctrl+Shift+L", "AI"));
        app.addShortcut(new Shortcut("Open AI Settings", "Ctrl+Shift+J", "AI"));
        app.addShortcut(new Shortcut("Add File to Chat Context", "@", "AI"));
        app.addShortcut(new Shortcut("Accept AI Suggestion", "Tab", "AI"));
        app.addShortcut(new Shortcut("Reject AI Suggestion", "Esc", "AI"));
        app.addShortcut(new Shortcut("Open Command Palette", "Ctrl+Shift+P", "General"));
        app.addShortcut(new Shortcut("Quick Open File", "Ctrl+P", "General"));
        app.addShortcut(new Shortcut("Toggle Terminal", "Ctrl+`", "General"));
        app.addShortcut(new Shortcut("Toggle Sidebar", "Ctrl+B", "General"));
        app.addShortcut(new Shortcut("Save File", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Find in File", "Ctrl+F", "Search"));
        app.addShortcut(new Shortcut("Find in All Files", "Ctrl+Shift+F", "Search"));
        app.addShortcut(new Shortcut("Comment Line", "Ctrl+/", "Editing"));
        app.addShortcut(new Shortcut("Format Document", "Shift+Alt+F", "Editing"));
        return app;
    }

    private AppShortcuts buildAnyDesk() {
        AppShortcuts app = new AppShortcuts("AnyDesk", "🖥️");
        app.addShortcut(new Shortcut("Send Ctrl+Alt+Del to Remote", "Ctrl+Alt+Del (in session)", "Session"));
        app.addShortcut(new Shortcut("Show/Hide Toolbar", "Ctrl+Alt+Shift+1", "Session"));
        app.addShortcut(new Shortcut("Toggle Fullscreen", "Ctrl+Alt+Shift+F", "Session"));
        app.addShortcut(new Shortcut("Take Screenshot", "Ctrl+Alt+Shift+S", "Session"));
        app.addShortcut(new Shortcut("Start Recording", "Ctrl+Alt+Shift+R", "Session"));
        app.addShortcut(new Shortcut("Switch Monitor", "Ctrl+Alt+Shift+M", "Session"));
        app.addShortcut(new Shortcut("Block User Input", "Ctrl+Alt+Shift+B", "Session"));
        app.addShortcut(new Shortcut("Open Chat", "Ctrl+Alt+Shift+C", "Session"));
        app.addShortcut(new Shortcut("Refresh Screen", "Ctrl+Alt+Shift+E", "Session"));
        app.addShortcut(new Shortcut("Show Settings", "Ctrl+Alt+Shift+P", "General"));
        app.addShortcut(new Shortcut("Disconnect Session", "Ctrl+Alt+Shift+D", "Session"));
        return app;
    }

    private AppShortcuts buildCapCut() {
        AppShortcuts app = new AppShortcuts("CapCut", "🎬");
        app.addShortcut(new Shortcut("Play / Pause", "Spacebar", "Playback"));
        app.addShortcut(new Shortcut("Previous Frame", "←", "Playback"));
        app.addShortcut(new Shortcut("Next Frame", "→", "Playback"));
        app.addShortcut(new Shortcut("Jump to Start", "Home", "Playback"));
        app.addShortcut(new Shortcut("Jump to End", "End", "Playback"));
        app.addShortcut(new Shortcut("New Project", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open Project", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save Project", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Import Media", "Ctrl+I", "File"));
        app.addShortcut(new Shortcut("Export", "Ctrl+E", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Delete", "Delete", "Edit"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Edit"));
        app.addShortcut(new Shortcut("Split Clip", "Ctrl+B", "Timeline"));
        app.addShortcut(new Shortcut("Group Clips", "Ctrl+G", "Timeline"));
        app.addShortcut(new Shortcut("Ungroup", "Ctrl+Shift+G", "Timeline"));
        app.addShortcut(new Shortcut("Mute Selected", "Ctrl+Shift+M", "Timeline"));
        app.addShortcut(new Shortcut("Zoom In Timeline", "Ctrl+=", "Timeline"));
        app.addShortcut(new Shortcut("Zoom Out Timeline", "Ctrl+-", "Timeline"));
        app.addShortcut(new Shortcut("Fit Timeline", "Shift+Z", "Timeline"));
        app.addShortcut(new Shortcut("Add Marker", "M", "Timeline"));
        return app;
    }

    private AppShortcuts buildPhotoshop() {
        AppShortcuts app = new AppShortcuts("Photoshop", "🅰");
        app.addShortcut(new Shortcut("New Document", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save As", "Ctrl+Shift+S", "File"));
        app.addShortcut(new Shortcut("Save for Web", "Ctrl+Alt+Shift+S", "File"));
        app.addShortcut(new Shortcut("Close", "Ctrl+W", "File"));
        app.addShortcut(new Shortcut("Quit", "Ctrl+Q", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Step Backward", "Ctrl+Alt+Z", "Edit"));
        app.addShortcut(new Shortcut("Step Forward", "Ctrl+Shift+Z", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Copy Merged", "Ctrl+Shift+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Free Transform", "Ctrl+T", "Edit"));
        app.addShortcut(new Shortcut("Fill", "Shift+F5", "Edit"));
        app.addShortcut(new Shortcut("Move Tool", "V", "Tools"));
        app.addShortcut(new Shortcut("Rectangular Marquee", "M", "Tools"));
        app.addShortcut(new Shortcut("Lasso Tool", "L", "Tools"));
        app.addShortcut(new Shortcut("Magic Wand", "W", "Tools"));
        app.addShortcut(new Shortcut("Crop Tool", "C", "Tools"));
        app.addShortcut(new Shortcut("Eyedropper", "I", "Tools"));
        app.addShortcut(new Shortcut("Spot Healing Brush", "J", "Tools"));
        app.addShortcut(new Shortcut("Brush Tool", "B", "Tools"));
        app.addShortcut(new Shortcut("Clone Stamp", "S", "Tools"));
        app.addShortcut(new Shortcut("History Brush", "Y", "Tools"));
        app.addShortcut(new Shortcut("Eraser Tool", "E", "Tools"));
        app.addShortcut(new Shortcut("Gradient Tool", "G", "Tools"));
        app.addShortcut(new Shortcut("Dodge Tool", "O", "Tools"));
        app.addShortcut(new Shortcut("Pen Tool", "P", "Tools"));
        app.addShortcut(new Shortcut("Type Tool", "T", "Tools"));
        app.addShortcut(new Shortcut("Path Selection Tool", "A", "Tools"));
        app.addShortcut(new Shortcut("Shape Tool", "U", "Tools"));
        app.addShortcut(new Shortcut("Hand Tool", "H", "Tools"));
        app.addShortcut(new Shortcut("Zoom Tool", "Z", "Tools"));
        app.addShortcut(new Shortcut("Default Colors", "D", "Tools"));
        app.addShortcut(new Shortcut("Swap FG/BG Colors", "X", "Tools"));
        app.addShortcut(new Shortcut("New Layer", "Ctrl+Shift+N", "Layers"));
        app.addShortcut(new Shortcut("Duplicate Layer", "Ctrl+J", "Layers"));
        app.addShortcut(new Shortcut("Merge Down", "Ctrl+E", "Layers"));
        app.addShortcut(new Shortcut("Merge Visible", "Ctrl+Shift+E", "Layers"));
        app.addShortcut(new Shortcut("Group Layers", "Ctrl+G", "Layers"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Selection"));
        app.addShortcut(new Shortcut("Deselect", "Ctrl+D", "Selection"));
        app.addShortcut(new Shortcut("Reselect", "Ctrl+Shift+D", "Selection"));
        app.addShortcut(new Shortcut("Inverse Selection", "Ctrl+Shift+I", "Selection"));
        app.addShortcut(new Shortcut("Levels", "Ctrl+L", "Adjustments"));
        app.addShortcut(new Shortcut("Curves", "Ctrl+M", "Adjustments"));
        app.addShortcut(new Shortcut("Hue/Saturation", "Ctrl+U", "Adjustments"));
        app.addShortcut(new Shortcut("Color Balance", "Ctrl+B", "Adjustments"));
        app.addShortcut(new Shortcut("Desaturate", "Ctrl+Shift+U", "Adjustments"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl++", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+-", "View"));
        app.addShortcut(new Shortcut("Fit on Screen", "Ctrl+0", "View"));
        app.addShortcut(new Shortcut("Actual Pixels (100%)", "Ctrl+1", "View"));
        app.addShortcut(new Shortcut("Toggle Rulers", "Ctrl+R", "View"));
        app.addShortcut(new Shortcut("Toggle Guides", "Ctrl+;", "View"));
        return app;
    }

    private AppShortcuts buildDarktable() {
        AppShortcuts app = new AppShortcuts("darktable", "📷");
        app.addShortcut(new Shortcut("Lighttable View", "L", "Views"));
        app.addShortcut(new Shortcut("Darkroom View", "D", "Views"));
        app.addShortcut(new Shortcut("Tethering View", "T", "Views"));
        app.addShortcut(new Shortcut("Map View", "M", "Views"));
        app.addShortcut(new Shortcut("Slideshow View", "S", "Views"));
        app.addShortcut(new Shortcut("Print View", "P", "Views"));
        app.addShortcut(new Shortcut("Preferences", "Ctrl+,", "General"));
        app.addShortcut(new Shortcut("Quit", "Ctrl+Q", "General"));
        app.addShortcut(new Shortcut("Import", "Ctrl+I", "File"));
        app.addShortcut(new Shortcut("Export", "Ctrl+E", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Edit"));
        app.addShortcut(new Shortcut("Select None", "Ctrl+Shift+A", "Edit"));
        app.addShortcut(new Shortcut("Invert Selection", "Ctrl+I", "Edit"));
        app.addShortcut(new Shortcut("Star Rating 1-5", "1, 2, 3, 4, 5", "Rating"));
        app.addShortcut(new Shortcut("Reject Image", "R", "Rating"));
        app.addShortcut(new Shortcut("Color Label Red", "F1", "Rating"));
        app.addShortcut(new Shortcut("Color Label Yellow", "F2", "Rating"));
        app.addShortcut(new Shortcut("Color Label Green", "F3", "Rating"));
        app.addShortcut(new Shortcut("Zoom 100%", "1", "Darkroom"));
        app.addShortcut(new Shortcut("Zoom Fit", "2", "Darkroom"));
        app.addShortcut(new Shortcut("Zoom Fill", "3", "Darkroom"));
        app.addShortcut(new Shortcut("Toggle Mask", "F", "Darkroom"));
        app.addShortcut(new Shortcut("Crop Tool", "Ctrl+Shift+C", "Darkroom"));
        app.addShortcut(new Shortcut("Toggle Histogram", "H", "Darkroom"));
        app.addShortcut(new Shortcut("Toggle Filmstrip", "Ctrl+F", "View"));
        app.addShortcut(new Shortcut("Hide Side Panels", "Tab", "View"));
        app.addShortcut(new Shortcut("Toggle Fullscreen", "F11", "View"));
        return app;
    }

    private AppShortcuts buildZoom() {
        AppShortcuts app = new AppShortcuts("Zoom", "📹");
        app.addShortcut(new Shortcut("Mute / Unmute Audio", "Alt+A", "Audio"));
        app.addShortcut(new Shortcut("Push to Talk (when muted)", "Hold Space", "Audio"));
        app.addShortcut(new Shortcut("Mute Audio for Everyone (Host)", "Alt+M", "Audio"));
        app.addShortcut(new Shortcut("Start / Stop Video", "Alt+V", "Video"));
        app.addShortcut(new Shortcut("Switch Camera", "Alt+N", "Video"));
        app.addShortcut(new Shortcut("Start / Stop Screen Share", "Alt+S", "Sharing"));
        app.addShortcut(new Shortcut("Pause / Resume Screen Share", "Alt+T", "Sharing"));
        app.addShortcut(new Shortcut("Start / Stop Local Recording", "Alt+R", "Recording"));
        app.addShortcut(new Shortcut("Start / Stop Cloud Recording", "Alt+C", "Recording"));
        app.addShortcut(new Shortcut("Pause / Resume Recording", "Alt+P", "Recording"));
        app.addShortcut(new Shortcut("Switch to Active Speaker View", "Alt+F1", "View"));
        app.addShortcut(new Shortcut("Switch to Gallery View", "Alt+F2", "View"));
        app.addShortcut(new Shortcut("Toggle Fullscreen", "Alt+F", "View"));
        app.addShortcut(new Shortcut("Show Floating Meeting Controls", "Ctrl+Alt+Shift+H", "View"));
        app.addShortcut(new Shortcut("Show Participants Panel", "Alt+U", "Panels"));
        app.addShortcut(new Shortcut("Show In-Meeting Chat", "Alt+H", "Panels"));
        app.addShortcut(new Shortcut("Read Active Speaker Name", "Ctrl+2", "Accessibility"));
        app.addShortcut(new Shortcut("Invite People", "Alt+I", "Meeting"));
        app.addShortcut(new Shortcut("Raise / Lower Hand", "Alt+Y", "Meeting"));
        app.addShortcut(new Shortcut("End Meeting", "Alt+Q", "Meeting"));
        app.addShortcut(new Shortcut("Take Screenshot", "Alt+Shift+T", "Meeting"));
        return app;
    }

    private AppShortcuts buildAudacity() {
        AppShortcuts app = new AppShortcuts("Audacity", "🎚️");
        app.addShortcut(new Shortcut("Play / Stop", "Spacebar", "Transport"));
        app.addShortcut(new Shortcut("Play / Stop and Set Cursor", "X", "Transport"));
        app.addShortcut(new Shortcut("Pause", "P", "Transport"));
        app.addShortcut(new Shortcut("Record", "R", "Transport"));
        app.addShortcut(new Shortcut("Append Record", "Shift+R", "Transport"));
        app.addShortcut(new Shortcut("Loop Play", "Shift+Spacebar", "Transport"));
        app.addShortcut(new Shortcut("Skip to Start", "Home", "Transport"));
        app.addShortcut(new Shortcut("Skip to End", "End", "Transport"));
        app.addShortcut(new Shortcut("New Project", "Ctrl+N", "File"));
        app.addShortcut(new Shortcut("Open", "Ctrl+O", "File"));
        app.addShortcut(new Shortcut("Save Project", "Ctrl+S", "File"));
        app.addShortcut(new Shortcut("Save Project As", "Ctrl+Shift+S", "File"));
        app.addShortcut(new Shortcut("Export", "Ctrl+Shift+E", "File"));
        app.addShortcut(new Shortcut("Import Audio", "Ctrl+Shift+I", "File"));
        app.addShortcut(new Shortcut("Undo", "Ctrl+Z", "Edit"));
        app.addShortcut(new Shortcut("Redo", "Ctrl+Y", "Edit"));
        app.addShortcut(new Shortcut("Cut", "Ctrl+X", "Edit"));
        app.addShortcut(new Shortcut("Copy", "Ctrl+C", "Edit"));
        app.addShortcut(new Shortcut("Paste", "Ctrl+V", "Edit"));
        app.addShortcut(new Shortcut("Delete", "Ctrl+K", "Edit"));
        app.addShortcut(new Shortcut("Trim", "Ctrl+T", "Edit"));
        app.addShortcut(new Shortcut("Silence Audio", "Ctrl+L", "Edit"));
        app.addShortcut(new Shortcut("Split", "Ctrl+I", "Edit"));
        app.addShortcut(new Shortcut("Split New", "Ctrl+Alt+I", "Edit"));
        app.addShortcut(new Shortcut("Duplicate", "Ctrl+D", "Edit"));
        app.addShortcut(new Shortcut("Select All", "Ctrl+A", "Selection"));
        app.addShortcut(new Shortcut("Select None", "Ctrl+Shift+A", "Selection"));
        app.addShortcut(new Shortcut("Selection Tool", "F1", "Tools"));
        app.addShortcut(new Shortcut("Envelope Tool", "F2", "Tools"));
        app.addShortcut(new Shortcut("Draw Tool", "F3", "Tools"));
        app.addShortcut(new Shortcut("Multi-Tool", "F6", "Tools"));
        app.addShortcut(new Shortcut("Zoom In", "Ctrl+1", "View"));
        app.addShortcut(new Shortcut("Zoom Out", "Ctrl+3", "View"));
        app.addShortcut(new Shortcut("Zoom Normal", "Ctrl+2", "View"));
        app.addShortcut(new Shortcut("Fit Selection in Window", "Ctrl+E", "View"));
        app.addShortcut(new Shortcut("Fit Project in Window", "Ctrl+F", "View"));
        return app;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void saveCustomShortcuts() {
        Map<String, List<Shortcut>> customMap = new LinkedHashMap<>();
        for (Map.Entry<String, AppShortcuts> entry : appsMap.entrySet()) {
            List<Shortcut> modified = entry.getValue().getShortcuts();
            if (!modified.isEmpty()) customMap.put(entry.getKey(), modified);
        }
        try (Writer writer = new FileWriter(CUSTOM_FILE)) {
            GSON.toJson(customMap, writer);
        } catch (Exception e) {
            System.err.println("[ShortcutDataStore] Failed to save: " + e.getMessage());
        }
    }

    public void saveSettings() {
        try (Writer writer = new FileWriter(SETTINGS_FILE)) {
            GSON.toJson(settings, writer);
        } catch (Exception e) {
            System.err.println("[ShortcutDataStore] Failed to save settings: " + e.getMessage());
        }
    }

    /**
     * Add or update an app in the store dynamically (used when a new detected
     * app is not in the built-in list).
     */
    public void ensureAppExists(String appName, String icon) {
        appsMap.computeIfAbsent(appName, k -> new AppShortcuts(appName, icon));
    }

    public List<AppShortcuts> getAllApps() { return new ArrayList<>(appsMap.values()); }
    public AppShortcuts getApp(String name) { return appsMap.get(name); }
    public AppSettings getSettings() { return settings; }
    public void setSettings(AppSettings settings) { this.settings = settings; }
    public boolean hasApp(String name) { return appsMap.containsKey(name); }
}
