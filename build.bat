@echo off
setlocal

set JAVAFX_LIB=.javafx/lib
set GSON=lib/gson-2.10.1.jar
set OUT=out
set CP=%OUT%;%GSON%
set MP=%JAVAFX_LIB%;%GSON%

echo Compiling...
javac --module-path "%MP%" --add-modules javafx.controls,javafx.fxml,com.google.gson -cp "%CP%" -d %OUT% ^
    src/main/shortcutmanager/java/module-info.java ^
    src/main/shortcutmanager/java/Main.java ^
    src/main/shortcutmanager/java/ui/ToggleSwitch.java ^
    src/main/shortcutmanager/java/ui/ShortcutKeyCell.java ^
    src/main/shortcutmanager/java/ui/SettingsController.java ^
    src/main/shortcutmanager/java/ui/MainController.java ^
    src/main/shortcutmanager/java/model/Shortcut.java ^
    src/main/shortcutmanager/java/model/AppShortcuts.java ^
    src/main/shortcutmanager/java/model/AppSettings.java ^
    src/main/shortcutmanager/java/data/ShortcutDataStore.java ^
    src/main/shortcutmanager/java/data/config/ConfigReader.java ^
    src/main/shortcutmanager/java/data/config/VSCodeConfigReader.java ^
    src/main/shortcutmanager/java/data/config/IntelliJConfigReader.java ^
    src/main/shortcutmanager/java/data/config/ZedConfigReader.java ^
    src/main/shortcutmanager/java/data/config/SublimeConfigReader.java ^
    src/main/shortcutmanager/java/detection/AppDetector.java ^
    src/main/shortcutmanager/java/detection/AppInfo.java

if errorlevel 1 (
    echo Compilation failed!
    exit /b 1
)

echo Copying resources...
if not exist "%OUT%\com\shortcutmanager" mkdir "%OUT%\com\shortcutmanager"
copy /Y src\main\shortcutmanager\resources\main.fxml "%OUT%\com\shortcutmanager\" >nul
copy /Y src\main\shortcutmanager\resources\dark.css "%OUT%\com\shortcutmanager\" >nul
copy /Y src\main\shortcutmanager\resources\light.css "%OUT%\com\shortcutmanager\" >nul

echo Running...
java --module-path "%MP%" --add-modules javafx.controls,javafx.fxml,com.google.gson -cp "%CP%" shortcutmanager.Main