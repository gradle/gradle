/*
 * Copyright 2007-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.FileScriptSource;

import java.io.File;
import java.util.List;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsFinder implements ISettingsFinder {
    private File settingsDir;
    private ScriptSource settingsScriptSource;
    private List<ISettingsFileSearchStrategy> settingsFileSearchStrategies;

    public DefaultSettingsFinder(List<ISettingsFileSearchStrategy> settingsFileSearchStrategies) {
        this.settingsFileSearchStrategies = settingsFileSearchStrategies;
    }

    public void find(StartParameter startParameter) {
        File settingsFile = null;
        for (ISettingsFileSearchStrategy settingsFileSearchStrategy : settingsFileSearchStrategies) {
            settingsFile = settingsFileSearchStrategy.find(startParameter);
            if (settingsFile != null) {
                break;
            }
        }
        if (settingsFile == null) {
            settingsDir = startParameter.getCurrentDir();
            settingsFile = new File(startParameter.getCurrentDir(), startParameter.getSettingsFileName());
        } else {
            settingsDir = settingsFile.getParentFile();
        }
        settingsScriptSource = new FileScriptSource("settings file", settingsFile);
    }

    public File getSettingsDir() {
        return settingsDir;
    }

    public void setSettingsDir(File settingsDir) {
        this.settingsDir = settingsDir;
    }

    public ScriptSource getSettingsScriptSource() {
        return settingsScriptSource;
    }

    public void setSettingsScriptSource(ScriptSource settingsScriptSource) {
        this.settingsScriptSource = settingsScriptSource;
    }
}
