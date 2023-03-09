/*
 * Copyright 2023 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

import static org.gradle.initialization.IGradlePropertiesLoader.ENV_PROJECT_PROPERTIES_PREFIX;
import static org.gradle.initialization.IGradlePropertiesLoader.SYSTEM_PROJECT_PROPERTIES_PREFIX;

public class DefaultProjectPropertiesLoader implements ProjectPropertiesLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultProjectPropertiesLoader.class);

    private final Environment environment;

    private final StartParameterInternal startParameter;

    public DefaultProjectPropertiesLoader(StartParameterInternal startParameter, Environment environment) {
        this.environment = environment;
        this.startParameter = startParameter;
    }

    @Override
    public Map<String, Object> loadProjectProperties() {
        Map<String, Object> properties = new HashMap<>();

        properties.putAll(projectPropertiesFromEnvironmentVariables());
        properties.putAll(projectPropertiesFromSystemProperties());
        properties.putAll(startParameter.getProjectProperties());

        return properties;
    }

    private Map<String, String> projectPropertiesFromSystemProperties() {
        Map<String, String> systemProjectProperties = byPrefix(SYSTEM_PROJECT_PROPERTIES_PREFIX, environment.getSystemProperties());
        LOGGER.debug("Found system project properties: {}", systemProjectProperties.keySet());
        return systemProjectProperties;
    }

    private Map<String, String> projectPropertiesFromEnvironmentVariables() {
        Map<String, String> envProjectProperties = byPrefix(ENV_PROJECT_PROPERTIES_PREFIX, environment.getVariables());
        LOGGER.debug("Found env project properties: {}", envProjectProperties.keySet());
        return envProjectProperties;
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
