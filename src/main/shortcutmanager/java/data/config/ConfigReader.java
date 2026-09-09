package shortcutmanager.data.config;

import shortcutmanager.model.Shortcut;

import java.util.List;

/**
 * Interface for reading shortcuts from an application's own config files.
 * Implementations return the shortcuts found in the config, which are
 * then merged on top of the database defaults in ShortcutDataStore.
 */
public interface ConfigReader {

    /**
     * The internal name identifying this reader (e.g. "vscode", "intellij").
     */
    String getReaderId();

    /**
     * Returns true if this app's config files are found on the current machine.
     * Used to know whether config-reading is possible at all.
     */
    boolean isInstalled();

    /**
     * Read shortcuts from the app's config. Returns an empty list if the
     * config file doesn't exist or can't be parsed — never throws.
     */
    List<Shortcut> readShortcuts();

    /**
     * Write a modified shortcut back to the app's config file.
     * Returns true if the write succeeded.
     */
    boolean writeShortcut(Shortcut shortcut);
}
