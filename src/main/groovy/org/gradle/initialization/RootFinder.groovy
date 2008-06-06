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

/**
 * @author Hans Dockter
 */
class RootFinder {
    File currentDir
    File rootDir
    File settingsFile = null
    String settingsText

    void find(File currentDir, boolean searchUpwards) {
        this.currentDir = currentDir
        File searchDir = currentDir

        // Damn, there is no do while in Groovy yet. We need an ugly work around.
        while (searchDir && !settingsFile) {
            searchDir.eachFile {
                if (it.name.equals(SettingsProcessor.DEFAULT_SETUP_FILE)) {
                    settingsFile = it
                }
            }
            searchDir = searchUpwards ? searchDir.parentFile : null
        }
        if (!settingsFile) {
            settingsText = ''
            rootDir = currentDir
        } else {
            settingsText = settingsFile.text
            rootDir = settingsFile.parentFile
        }
    }
}