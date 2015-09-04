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
package org.gradle.api.internal.artifacts.mvnsettings;

import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.*;

public class DefaultMavenSettingsProvider implements MavenSettingsProvider {

    private final MavenFileLocations mavenFileLocations;

    public DefaultMavenSettingsProvider(MavenFileLocations mavenFileLocations) {
        this.mavenFileLocations = mavenFileLocations;
    }

    public Settings buildSettings() throws SettingsBuildingException {
        DefaultSettingsBuilderFactory factory = new DefaultSettingsBuilderFactory();
        DefaultSettingsBuilder defaultSettingsBuilder = factory.newInstance();
        DefaultSettingsBuildingRequest settingsBuildingRequest = new DefaultSettingsBuildingRequest();
        settingsBuildingRequest.setSystemProperties(System.getProperties());
        settingsBuildingRequest.setUserSettingsFile(mavenFileLocations.getUserSettingsFile());
        settingsBuildingRequest.setGlobalSettingsFile(mavenFileLocations.getGlobalSettingsFile());
        SettingsBuildingResult settingsBuildingResult = defaultSettingsBuilder.build(settingsBuildingRequest);
        return settingsBuildingResult.getEffectiveSettings();
    }
}
