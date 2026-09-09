package shortcutmanager.model;

import java.util.List;
import java.util.ArrayList;

public class AppShortcuts {
    private String appName;
    private String icon;
    private List<Shortcut> shortcuts;

    public AppShortcuts(String appName, String icon) {
        this.appName = appName;
        this.icon = icon;
        this.shortcuts = new ArrayList<>();
    }

    public AppShortcuts(String appName, String icon, List<Shortcut> shortcuts) {
        this.appName = appName;
        this.icon = icon;
        this.shortcuts = shortcuts;
    }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public List<Shortcut> getShortcuts() { return shortcuts; }
    public void setShortcuts(List<Shortcut> shortcuts) { this.shortcuts = shortcuts; }

    public void addShortcut(Shortcut shortcut) { this.shortcuts.add(shortcut); }

    @Override
    public String toString() { return appName; }
}
