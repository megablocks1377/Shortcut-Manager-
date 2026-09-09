package shortcutmanager.model;

public class AppSettings {
    private String theme;
    private double fontSize;
    private boolean showCategories;
    private boolean showCustomOnly;
    private String accentColor;
    private boolean compactMode;

    public AppSettings() {
        this.theme = "Dark";
        this.fontSize = 14.0;
        this.showCategories = true;
        this.showCustomOnly = false;
        this.accentColor = "#6C63FF";
        this.compactMode = false;
    }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }

    public boolean isShowCategories() { return showCategories; }
    public void setShowCategories(boolean showCategories) { this.showCategories = showCategories; }

    public boolean isShowCustomOnly() { return showCustomOnly; }
    public void setShowCustomOnly(boolean showCustomOnly) { this.showCustomOnly = showCustomOnly; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public boolean isCompactMode() { return compactMode; }
    public void setCompactMode(boolean compactMode) { this.compactMode = compactMode; }
}
