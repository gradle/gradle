package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;

public class EmbeddedScriptSettingsFinder implements ISettingsFinder {
    private ScriptSource settingsScript;
    private File settingsDir;

    public void find(StartParameter startParameter) {
        settingsScript = startParameter.getSettingsScriptSource();
        settingsDir = startParameter.getCurrentDir();
    }

    public File getSettingsDir() {
        return settingsDir;
    }

    public ScriptSource getSettingsScript() {
        return settingsScript;
    }
}
