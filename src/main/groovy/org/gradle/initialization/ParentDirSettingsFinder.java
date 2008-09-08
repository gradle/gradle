/*
 * Copyright 2007 the original author or authors.
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
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ParentDirSettingsFinder implements ISettingsFinder {
    private File settingsDir;
    private File settingsFile;
    private ScriptSource settingsScriptSource;

    public void find(StartParameter startParameter) {
        File searchDir = startParameter.getCurrentDir();
        String settingsFileName = startParameter.getSettingsFileName();

        // Damn, there is no do while in Groovy yet. We need an ugly work around.
        while (searchDir != null && settingsFile == null) {
            for (File file : searchDir.listFiles()) {
                if (file.getName().equals(settingsFileName)) {
                    settingsFile = file;
                }
            }
            searchDir = startParameter.isSearchUpwards() ? searchDir.getParentFile() : null;
        }
        if (settingsFile == null) {
            settingsDir = startParameter.getCurrentDir();
            settingsFile = new File(settingsDir, settingsFileName);
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

    public File getSettingsFile() {
        return settingsFile;
    }

    public void setSettingsFile(File settingsFile) {
        this.settingsFile = settingsFile;
    }

    public ScriptSource getSettingsScriptSource() {
        return settingsScriptSource;
    }

    public void setSettingsScriptSource(ScriptSource settingsScriptSource) {
        this.settingsScriptSource = settingsScriptSource;
    }
}