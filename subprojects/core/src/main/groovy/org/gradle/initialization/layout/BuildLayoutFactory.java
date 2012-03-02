/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization.layout;

import java.io.File;

public class BuildLayoutFactory {
    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(File currentDir, boolean searchUpwards) {
        return getLayoutFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(BuildLayoutConfiguration configuration) {
        if (configuration.isUseEmptySettings()) {
            return new BuildLayout(configuration.getCurrentDir(), configuration.getCurrentDir(), null);
        }
        if (configuration.getSettingsFile() != null) {
            return new BuildLayout(configuration.getCurrentDir(), configuration.getCurrentDir(), configuration.getSettingsFile());
        }

        File currentDir = configuration.getCurrentDir();
        boolean searchUpwards = configuration.isSearchUpwards();
        return getLayoutFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    BuildLayout getLayoutFor(File currentDir, File stopAt) {
        File settingsFile = new File(currentDir, "settings.gradle");
        if (settingsFile.isFile()) {
            return layout(currentDir, currentDir, settingsFile);
        }
        for (File candidate = currentDir.getParentFile(); candidate != null && !candidate.equals(stopAt); candidate = candidate.getParentFile()) {
            settingsFile = new File(candidate, "settings.gradle");
            if (settingsFile.isFile()) {
                return layout(candidate, candidate, settingsFile);
            }
            settingsFile = new File(candidate, "master/settings.gradle");
            if (settingsFile.isFile()) {
                return layout(candidate, settingsFile.getParentFile(), settingsFile);
            }
        }
        return layout(currentDir, currentDir);
    }

    private BuildLayout layout(File rootDir, File settingsDir) {
        return new BuildLayout(rootDir, settingsDir, null);
    }

    private BuildLayout layout(File rootDir, File settingsDir, File settingsFile) {
        return new BuildLayout(rootDir, settingsDir, settingsFile);
    }
}
