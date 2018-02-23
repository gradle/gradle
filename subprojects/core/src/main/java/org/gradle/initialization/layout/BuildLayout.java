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

import org.gradle.initialization.SettingsLocation;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.io.File;

@UsedByScanPlugin
public class BuildLayout extends SettingsLocation {
    private final File rootDirectory;

    // Note: `null` for `settingsFile` means explicitly no settings
    //       A non null value can be a non existent file, which is semantically equivalent to an empty file
    public BuildLayout(File rootDirectory, File settingsDir, File settingsFile) {
        super(settingsDir, settingsFile);
        this.rootDirectory = rootDirectory;
    }

    /**
     * Returns the root directory of the build, is never null.
     */
    public File getRootDirectory() {
        return rootDirectory;
    }
}
