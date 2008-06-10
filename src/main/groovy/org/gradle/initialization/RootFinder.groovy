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

/**
 * @author Hans Dockter
 */
class RootFinder {
    File rootDir
    File settingsFile = null
    String settingsText
    Map gradleProperties = [:]

    void find(StartParameter startParameter) {
        File searchDir = startParameter.currentDir

        // Damn, there is no do while in Groovy yet. We need an ugly work around.
        while (searchDir && !settingsFile) {
            searchDir.eachFile {
                if (it.name.equals(SettingsProcessor.DEFAULT_SETUP_FILE)) {
                    settingsFile = it
                }
            }
            searchDir = startParameter.searchUpwards ? searchDir.parentFile : null
        }
        if (!settingsFile) {
            settingsText = ''
            rootDir = startParameter.currentDir
        } else {
            settingsText = settingsFile.text
            rootDir = settingsFile.parentFile
        }
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