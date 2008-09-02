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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.groovy.scripts.FileScriptSource
import org.gradle.groovy.scripts.ScriptSource

/**
 * @author Hans Dockter
 */
class ParentDirSettingsFinder implements ISettingsFinder {
    File rootDir
    File settingsFile
    ScriptSource settingsScript
    Map<String, String> gradleProperties = [:]

    void find(StartParameter startParameter) {
        File searchDir = startParameter.currentDir
        String settingsFileName = startParameter.settingsFileName

        // Damn, there is no do while in Groovy yet. We need an ugly work around.
        while (searchDir && !settingsFile) {
            searchDir.eachFile {
                if (it.name.equals(settingsFileName)) {
                    settingsFile = it
                }
            }
            searchDir = startParameter.searchUpwards ? searchDir.parentFile : null
        }
        if (!settingsFile) {
            rootDir = startParameter.currentDir
            settingsFile = new File(rootDir, settingsFileName)
        } else {
            rootDir = settingsFile.parentFile
        }
        settingsScript = new FileScriptSource("settings file", settingsFile)
        addGradleProperties(rootDir, startParameter)
    }

    private void addGradleProperties(File rootDir, StartParameter startParameter) {
        [new File(rootDir, Project.GRADLE_PROPERTIES),
                new File(startParameter.gradleUserHomeDir, Project.GRADLE_PROPERTIES)].each { File propertyFile ->
            if (propertyFile.isFile()) {
                Properties properties = new Properties()
                properties.load(new FileInputStream(propertyFile))
                gradleProperties.putAll(properties)
            }
        }
    }
}