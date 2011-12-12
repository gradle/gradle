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

import org.gradle.StartParameter;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutFactory;

/**
 * @author Hans Dockter
 */
public class DefaultSettingsFinder implements ISettingsFinder {
    private final BuildLayoutFactory layoutFactory;

    public DefaultSettingsFinder(BuildLayoutFactory layoutFactory) {
        this.layoutFactory = layoutFactory;
    }

    public SettingsLocation find(StartParameter startParameter) {
        BuildLayout layout = layoutFactory.getLayoutFor(startParameter.getCurrentDir(), startParameter.isSearchUpwards());
        if (layout.getSettingsFile() == null) {
            return new SettingsLocation(layout.getSettingsDir(),
                    new StringScriptSource("empty settings file", ""));
        } else {
            return new SettingsLocation(layout.getSettingsDir(),
                    new UriScriptSource("settings file", layout.getSettingsFile()));

        }
    }
}
