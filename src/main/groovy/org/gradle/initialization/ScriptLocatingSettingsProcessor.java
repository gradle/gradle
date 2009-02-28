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
import org.gradle.api.internal.SettingsInternal;

public class ScriptLocatingSettingsProcessor implements SettingsProcessor {
    private final SettingsProcessor processor;

    public ScriptLocatingSettingsProcessor(SettingsProcessor processor) {
        this.processor = processor;
    }

    public SettingsInternal process(ISettingsFinder settingsFinder, StartParameter startParameter,
                                    IGradlePropertiesLoader propertiesLoader) {
        settingsFinder.find(startParameter);
        propertiesLoader.loadProperties(settingsFinder.getSettingsDir(), startParameter);

        SettingsInternal settings = processor.process(settingsFinder, startParameter, propertiesLoader);
        if (!settings.getProjectRegistry().findAll(startParameter.getDefaultProjectSelector()).isEmpty()) {
            return settings;
        }

        // The settings we found did not include the default project. Try again with no search upwards.

        StartParameter noSearchParameter = startParameter.newInstance();
        noSearchParameter.setSearchUpwards(false);
        settingsFinder.find(noSearchParameter);
        propertiesLoader.loadProperties(settingsFinder.getSettingsDir(), noSearchParameter);

        return processor.process(settingsFinder, noSearchParameter, propertiesLoader);
    }
}
