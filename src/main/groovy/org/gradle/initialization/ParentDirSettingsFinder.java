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
import org.gradle.api.Project;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class ParentDirSettingsFinder implements ISettingsFinder {
    private File rootDir;
    private File settingsFile;
    private ScriptSource settingsScript;
    private Map<String, String> gradleProperties = new HashMap<String, String>();

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
            rootDir = startParameter.getCurrentDir();
            settingsFile = new File(rootDir, settingsFileName);
        } else {
            rootDir = settingsFile.getParentFile();
        }
        settingsScript = new FileScriptSource("settings file", settingsFile);
        addGradleProperties(rootDir, startParameter);
    }

    private void addGradleProperties(File rootDir, StartParameter startParameter) {
        addGradleProperties(
                new File(rootDir, Project.GRADLE_PROPERTIES),
                new File(startParameter.getGradleUserHomeDir(), Project.GRADLE_PROPERTIES));
    }

    private void addGradleProperties(File... files) {
        for (File propertyFile : files) {
            if (propertyFile.isFile()) {
                Properties properties = new Properties();
                try {
                    properties.load(new FileInputStream(propertyFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                gradleProperties.putAll(new HashMap(properties));
            }
        }
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public File getSettingsFile() {
        return settingsFile;
    }

    public void setSettingsFile(File settingsFile) {
        this.settingsFile = settingsFile;
    }

    public ScriptSource getSettingsScript() {
        return settingsScript;
    }

    public void setSettingsScript(ScriptSource settingsScript) {
        this.settingsScript = settingsScript;
    }

    public Map<String, String> getGradleProperties() {
        return gradleProperties;
    }

    public void setGradleProperties(Map<String, String> gradleProperties) {
        this.gradleProperties = gradleProperties;
    }
}