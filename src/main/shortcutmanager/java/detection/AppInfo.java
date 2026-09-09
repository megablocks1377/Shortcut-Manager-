package shortcutmanager.detection;

/**
 * Represents a detected running application.
 */
public class AppInfo {
    private final String appName;
    private final String processName;
    private final long pid;
    private final boolean isFocused;

    public AppInfo(String appName, String processName, long pid, boolean isFocused) {
        this.appName = appName;
        this.processName = processName;
        this.pid = pid;
        this.isFocused = isFocused;
    }

    public String getAppName() { return appName; }
    public String getProcessName() { return processName; }
    public long getPid() { return pid; }
    public boolean isFocused() { return isFocused; }

    @Override
    public String toString() {
        return appName + " [" + processName + ", pid=" + pid + (isFocused ? ", FOCUSED" : "") + "]";
    }
}
