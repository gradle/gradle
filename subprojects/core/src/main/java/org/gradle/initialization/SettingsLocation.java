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

import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.resource.BasicTextResourceLoader;
import org.gradle.internal.resource.TextResource;

import java.io.File;

public class SettingsLocation {
    private File settingsDir;
    private ScriptSource settingsScriptSource;

    public SettingsLocation(File settingsDir, File settingsFile) {
        this.settingsDir = settingsDir;
        TextResource settingsResource = new BasicTextResourceLoader().loadFile("settings file", settingsFile);
        this.settingsScriptSource = new TextResourceScriptSource(settingsResource);
    }

    /**
     * Returns the settings directory. Never null.
     */
    public File getSettingsDir() {
        return settingsDir;
    }

    /**
     * Returns the settings script. Never null.
     */
    public ScriptSource getSettingsScriptSource() {
        return settingsScriptSource;
    }
}

