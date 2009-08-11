package org.gradle.initialization;

import org.gradle.StartParameter;

public class EmbeddedScriptSettingsFinder implements ISettingsFinder {
    private final ISettingsFinder finder;

    public EmbeddedScriptSettingsFinder(ISettingsFinder finder) {
        this.finder = finder;
    }

    public SettingsLocation find(StartParameter startParameter) {
        if (startParameter.getSettingsScriptSource() != null) {
            return new SettingsLocation(startParameter.getCurrentDir(),
                                        startParameter.getSettingsScriptSource());
        } else {
            return finder.find(startParameter);
        }
    }
}
