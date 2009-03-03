package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;

public class EmbeddedScriptSettingsFinder implements ISettingsFinder {
    private final ISettingsFinder finder;
    private ScriptSource settingsScript;
    private File settingsDir;

    public EmbeddedScriptSettingsFinder(ISettingsFinder finder) {
        this.finder = finder;
    }

    public void find(StartParameter startParameter) {
        if (startParameter.getSettingsScriptSource() != null) {
            settingsScript = startParameter.getSettingsScriptSource();
            settingsDir = startParameter.getCurrentDir();
        } else {
            finder.find(startParameter);
        }
    }

    public File getSettingsDir() {
        if (settingsScript != null) {
            return settingsDir;
        } else {
            return finder.getSettingsDir();
        }
    }

    public ScriptSource getSettingsScriptSource() {
        if (settingsScript != null) {
            return settingsScript;
        } else {
            return finder.getSettingsScriptSource();
        }
    }
}
