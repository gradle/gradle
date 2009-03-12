/*
 * Copyright 2007, 2008 the original author or authors.
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
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.initialization.ProjectDescriptor;

public class ScriptLocatingSettingsProcessor implements SettingsProcessor {
    private final SettingsProcessor processor;

    public ScriptLocatingSettingsProcessor(SettingsProcessor processor) {
        this.processor = processor;
    }

    public SettingsInternal process(ISettingsFinder settingsFinder, StartParameter startParameter,
                                    IGradlePropertiesLoader propertiesLoader) {
        settingsFinder.find(startParameter);

        SettingsInternal settings = processor.process(settingsFinder, startParameter, propertiesLoader);
        if (startParameter.getDefaultProjectSelector().containsProject(settings.getProjectRegistry())) {
            return settings;
        }

        // The settings we found did not include the desired default project. Try again with an empty settings file.

        // If explicit settings file specified, we're done
        if (startParameter.getSettingsScriptSource() != null) {
            return settings;
        }

        StartParameter noSearchParameter = startParameter.newInstance();
        noSearchParameter.setSettingsScriptSource(new StringScriptSource("empty settings file", ""));
        settingsFinder.find(noSearchParameter);

        settings = processor.process(settingsFinder, noSearchParameter, propertiesLoader);

        ProjectDescriptor rootProject = settings.getRootProject();

        if (noSearchParameter.getBuildFile() != null) {
            // Set explicit build file, if required
            assert noSearchParameter.getBuildFile().getParentFile().equals(rootProject.getProjectDir());
            rootProject.setBuildFileName(noSearchParameter.getBuildFile().getName());
        }
        
        return settings;
    }
}
