package shortcutmanager.model;

public class Shortcut {
    private String action;
    private String keys;
    private String category;
    private boolean custom;

    public Shortcut(String action, String keys, String category) {
        this.action = action;
        this.keys = keys;
        this.category = category;
        this.custom = false;
    }

    public Shortcut(String action, String keys, String category, boolean custom) {
        this.action = action;
        this.keys = keys;
        this.category = category;
        this.custom = custom;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getKeys() { return keys; }
    public void setKeys(String keys) { this.keys = keys; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }

    @Override
    public String toString() {
        return action + " -> " + keys;
    }
}
