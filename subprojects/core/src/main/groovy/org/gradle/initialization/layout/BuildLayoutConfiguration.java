/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;

/**
 * Configuration which affects the (static) layout of a build.
 */
public class BuildLayoutConfiguration {
    private File currentDir;
    private boolean searchUpwards;
    private final ScriptSource settingsScriptSource;

    public BuildLayoutConfiguration(StartParameter startParameter) {
        currentDir = startParameter.getCurrentDir();
        searchUpwards = startParameter.isSearchUpwards();
        settingsScriptSource = startParameter.getSettingsScriptSource();
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public boolean isSearchUpwards() {
        return searchUpwards;
    }

    /**
     * Returns the settings script to use, or null to use the default settings script.
     */
    public ScriptSource getSettingsScript() {
        return settingsScriptSource;
    }
}
