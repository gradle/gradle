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

import org.apache.commons.lang.StringUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.apache.maven.settings.io.DefaultSettingsReader;
import org.apache.maven.settings.io.SettingsReader;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class DefaultMavenSettingsProvider implements MavenSettingsProvider {

    private final MavenFileLocations mavenFileLocations;

    public DefaultMavenSettingsProvider(MavenFileLocations mavenFileLocations) {
        this.mavenFileLocations = mavenFileLocations;
    }

    /**
     * Builds a complete `Settings` instance for this machine.
     *
     * Note that this can be an expensive operation, spawning an external process
     * and doing a bunch of additional processing.
     */
    @Override
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

    /**
     * Read the local repository location from local Maven settings files.
     *
     * @return The path to the local repository, or <code>null</code> if not specified in Maven settings.
     */
    @Override
    public String getLocalRepository() {
        String localRepo = readLocalRepository(mavenFileLocations.getUserSettingsFile());
        if (localRepo == null) {
            localRepo = readLocalRepository(mavenFileLocations.getGlobalSettingsFile());
        }
        return localRepo;
    }

    private String readLocalRepository(File settingsFile) {
        if (settingsFile == null || !settingsFile.exists()) {
            return null;
        }
        Map<String, ?> options = Collections.singletonMap(SettingsReader.IS_STRICT, Boolean.FALSE);
        SettingsReader settingsReader = new DefaultSettingsReader();
        try {
            String localRepository = settingsReader.read(settingsFile, options).getLocalRepository();
            return StringUtils.isEmpty(localRepository) ? null : localRepository;
        } catch (Exception parseException) {
            throw new CannotLocateLocalMavenRepositoryException("Unable to parse local Maven settings: " + settingsFile.getAbsolutePath(), parseException);
        }
    }
}
