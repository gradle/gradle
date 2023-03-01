/*
 * Copyright 2009 the original author or authors.
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

import javax.annotation.Nullable;
import java.io.File;

public class SettingsLocation {
    private final File settingsDir;

    @Nullable
    private final File settingsFile;

    public SettingsLocation(File settingsDir, @Nullable File settingsFile) {
        this.settingsDir = settingsDir;
        this.settingsFile = settingsFile;
    }

    /**
     * Returns the settings directory. Never null.
     */
    public File getSettingsDir() {
        return settingsDir;
    }

    /**
     * Returns the settings file. May be null, which mean "no settings file" rather than "use default settings file location".
     */
    @Nullable
    public File getSettingsFile() {
        return settingsFile;
    }
}

