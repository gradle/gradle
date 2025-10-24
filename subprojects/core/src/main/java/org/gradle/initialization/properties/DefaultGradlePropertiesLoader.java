/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.initialization.properties;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.Project.GRADLE_PROPERTIES;

public class DefaultGradlePropertiesLoader implements GradlePropertiesLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGradlePropertiesLoader.class);

    private final StartParameterInternal startParameter;
    private final Environment environment;

    public DefaultGradlePropertiesLoader(StartParameterInternal startParameter, Environment environment) {
        this.startParameter = startParameter;
        this.environment = environment;
    }

    @Override
    public Map<String, String> loadFromGradleHome() {
        return loadFrom(startParameter.getGradleHomeDir());
    }

    @Override
    public Map<String, String> loadFromGradleUserHome() {
        return loadFrom(startParameter.getGradleUserHomeDir());
    }

    @Override
    public Map<String, String> loadFrom(File dir) {
        Map<String, String> loadedProperties = environment.propertiesFile(new File(dir, GRADLE_PROPERTIES));
        return loadedProperties == null ? Collections.emptyMap() : loadedProperties;
    }

    @Override
    public Map<String, String> loadFromSystemProperties() {
        Map<String, String> systemProjectProperties = byPrefix(SYSTEM_PROJECT_PROPERTIES_PREFIX, environment.getSystemProperties());
        LOGGER.debug("Found system project properties: {}", systemProjectProperties.keySet());
        return systemProjectProperties;
    }

    @Override
    public Map<String, String> loadFromEnvironmentVariables() {
        Map<String, String> envProjectProperties = byPrefix(ENV_PROJECT_PROPERTIES_PREFIX, environment.getVariables());
        LOGGER.debug("Found env project properties: {}", envProjectProperties.keySet());
        return envProjectProperties;
    }

    @Override
    public Map<String, String> loadFromStartParameterProjectProperties() {
        return startParameter.getProjectPropertiesUntracked();
    }

    private static Map<String, String> byPrefix(String prefix, Environment.Properties properties) {
        return mapKeysRemovingPrefix(prefix, properties.byNamePrefix(prefix));
    }

    private static Map<String, String> mapKeysRemovingPrefix(String prefix, Map<String, String> mapWithPrefix) {
        Map<String, String> mapWithoutPrefix = new HashMap<>(mapWithPrefix.size());
        for (Map.Entry<String, String> entry : mapWithPrefix.entrySet()) {
            mapWithoutPrefix.put(
                entry.getKey().substring(prefix.length()),
                entry.getValue()
            );
        }
        return mapWithoutPrefix;
    }
}
