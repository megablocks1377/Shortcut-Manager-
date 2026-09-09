package shortcutmanager.detection;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

/**
 * Detects which known applications are currently running, and which one
 * has focus (is the active foreground window).
 *
 * Detection strategy:
 *   1. Use Java's ProcessHandle.allProcesses() to list every running process.
 *   2. Match process executable names against known patterns per app.
 *   3. For the focused/active window, call an OS-specific command:
 *        Linux  → xdotool getactivewindow getwindowname
 *        macOS  → osascript (AppleScript)
 *        Windows→ powershell Get-Process with MainWindowTitle
 *
 * Results are returned as a list of AppInfo objects. Callers should poll
 * this periodically (e.g. every 3 seconds) on a background thread.
 */
public class AppDetector {

    /** Maps app display name → list of substrings to match against the process executable path */
    private static final Map<String, List<String>> PROCESS_PATTERNS = new LinkedHashMap<>();

    static {
        // Order matters: more specific patterns first
        PROCESS_PATTERNS.put("VS Code",        Arrays.asList("code", "code-oss", "codium"));
        PROCESS_PATTERNS.put("IntelliJ IDEA",  Arrays.asList("idea", "idea64", "jetbrains-idea"));
        PROCESS_PATTERNS.put("WebStorm",       Arrays.asList("webstorm", "jetbrains-webstorm"));
        PROCESS_PATTERNS.put("PyCharm",        Arrays.asList("pycharm", "jetbrains-pycharm"));
        PROCESS_PATTERNS.put("CLion",          Arrays.asList("clion", "jetbrains-clion"));
        PROCESS_PATTERNS.put("GoLand",         Arrays.asList("goland", "jetbrains-goland"));
        PROCESS_PATTERNS.put("Zed",            Arrays.asList("zed", "zed-editor"));
        PROCESS_PATTERNS.put("Sublime Text",   Arrays.asList("subl", "sublime_text"));
        PROCESS_PATTERNS.put("Chrome",         Arrays.asList("google-chrome", "chrome", "chromium"));
        PROCESS_PATTERNS.put("Firefox",        Arrays.asList("firefox"));
        PROCESS_PATTERNS.put("Slack",          Arrays.asList("slack"));
        PROCESS_PATTERNS.put("Spotify",        Arrays.asList("spotify"));
        PROCESS_PATTERNS.put("Notion",         Arrays.asList("notion"));
        PROCESS_PATTERNS.put("Figma",          Arrays.asList("figma"));
        PROCESS_PATTERNS.put("Discord",        Arrays.asList("discord"));
        PROCESS_PATTERNS.put("Terminal",       Arrays.asList("gnome-terminal", "xterm", "konsole",
                                                             "alacritty", "kitty", "wezterm",
                                                             "iterm2", "terminal"));
        PROCESS_PATTERNS.put("Postman",        Arrays.asList("postman"));
        PROCESS_PATTERNS.put("DBeaver",        Arrays.asList("dbeaver"));
        PROCESS_PATTERNS.put("Docker Desktop", Arrays.asList("docker desktop", "dockerdesktop"));
        PROCESS_PATTERNS.put("Obsidian",       Arrays.asList("obsidian"));
        PROCESS_PATTERNS.put("Opera",          Arrays.asList("opera"));
        PROCESS_PATTERNS.put("Steam",          Arrays.asList("steam.exe", "steam.sh", "steamwebhelper", "steam"));
        PROCESS_PATTERNS.put("OBS Studio",     Arrays.asList("obs", "obs64", "obs-studio"));
        PROCESS_PATTERNS.put("Paint.NET",      Arrays.asList("paintdotnet", "paint.net"));
        PROCESS_PATTERNS.put("GIMP",           Arrays.asList("gimp", "gimp-2"));
        PROCESS_PATTERNS.put("Blender",        Arrays.asList("blender"));
        PROCESS_PATTERNS.put("FL Studio",      Arrays.asList("fl64", "fl.exe", "flstudio", "fl studio"));
        PROCESS_PATTERNS.put("WhatsApp",       Arrays.asList("whatsapp"));
        PROCESS_PATTERNS.put("Microsoft Word", Arrays.asList("winword", "msword"));
        PROCESS_PATTERNS.put("Cursor",         Arrays.asList("cursor"));
        PROCESS_PATTERNS.put("AnyDesk",        Arrays.asList("anydesk"));
        PROCESS_PATTERNS.put("CapCut",         Arrays.asList("capcut"));
        PROCESS_PATTERNS.put("Photoshop",      Arrays.asList("photoshop", "ps.exe"));
        PROCESS_PATTERNS.put("darktable",      Arrays.asList("darktable"));
        PROCESS_PATTERNS.put("Zoom",           Arrays.asList("zoom.exe", "zoom-client", "zoomus", "zoom"));
        PROCESS_PATTERNS.put("Audacity",       Arrays.asList("audacity"));
    }

    private final String os;

    public AppDetector() {
        this.os = System.getProperty("os.name", "").toLowerCase();
    }

    /**
     * Returns a list of known apps that are currently running.
     * The list is ordered: focused app first (if detected), then others.
     */
    public List<AppInfo> detectRunningApps() {
        String focusedWindowName = getFocusedWindowName();
        Map<String, AppInfo> found = new LinkedHashMap<>();

        ProcessHandle.allProcesses().forEach(handle -> {
            Optional<String> commandOpt = handle.info().command();
            if (commandOpt.isEmpty()) return;

            String command = commandOpt.get().toLowerCase();
            String execName = Path.of(command).getFileName().toString().toLowerCase();

            for (Map.Entry<String, List<String>> entry : PROCESS_PATTERNS.entrySet()) {
                String appName = entry.getKey();
                if (found.containsKey(appName)) continue; // already found

                boolean matches = entry.getValue().stream()
                        .anyMatch(pattern -> execName.contains(pattern) || command.contains(pattern));
                if (matches) {
                    boolean isFocused = focusedWindowName != null &&
                            focusedWindowName.toLowerCase().contains(appName.split(" ")[0].toLowerCase());
                    found.put(appName, new AppInfo(appName, execName, handle.pid(), isFocused));
                    break;
                }
            }
        });

        // Sort: focused first, then alphabetical
        return found.values().stream()
                .sorted(Comparator.comparingInt((AppInfo a) -> a.isFocused() ? 0 : 1)
                        .thenComparing(AppInfo::getAppName))
                .collect(Collectors.toList());
    }

    /**
     * Returns the name of the currently focused app, or null if it can't be determined.
     */
    public String detectFocusedApp() {
        String windowName = getFocusedWindowName();
        if (windowName == null) return null;

        String lower = windowName.toLowerCase();
        for (Map.Entry<String, List<String>> entry : PROCESS_PATTERNS.entrySet()) {
            String appName = entry.getKey();
            // Check window title contains app name or a known process pattern
            if (lower.contains(appName.toLowerCase().split(" ")[0])) return appName;
            for (String pattern : entry.getValue()) {
                if (lower.contains(pattern)) return appName;
            }
        }
        return null;
    }

    /**
     * Returns the list of all app names that this detector knows about,
     * regardless of whether they are running.
     */
    public static List<String> getAllKnownAppNames() {
        return new ArrayList<>(PROCESS_PATTERNS.keySet());
    }

    // -------------------------------------------------------------------------
    // OS-specific focused window detection
    // -------------------------------------------------------------------------

    private String getFocusedWindowName() {
        if (os.contains("linux")) return getFocusedWindowLinux();
        if (os.contains("mac") || os.contains("darwin")) return getFocusedWindowMac();
        if (os.contains("win")) return getFocusedWindowWindows();
        return null;
    }

    private String getFocusedWindowLinux() {
        // Try xdotool first (most reliable on X11)
        String result = runCommand("xdotool", "getactivewindow", "getwindowname");
        if (result != null) return result;

        // Fallback: try wmctrl
        result = runCommand("wmctrl", "-l");
        // wmctrl output is not easy to filter for active window without more info, skip for now

        return null;
    }

    private String getFocusedWindowMac() {
        return runCommand("osascript", "-e",
                "tell application \"System Events\" to get name of first process where frontmost is true");
    }

    private String getFocusedWindowWindows() {
        // Use PowerShell to get the foreground window process name
        return runCommand("powershell", "-Command",
                "(Get-Process | Where-Object { $_.MainWindowHandle -ne 0 } | " +
                "Sort-Object CPU -Descending | Select-Object -First 1).Name");
    }

    /** Run a command and return its stdout as a trimmed string, or null on failure */
    private String runCommand(String... args) {
        try {
            Process p = new ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            return output.isEmpty() ? null : output;
        } catch (Exception e) {
            return null; // Tool not installed or failed — fail silently
        }
    }
}
