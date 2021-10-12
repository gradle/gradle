/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.internal.Cast;
import org.gradle.util.internal.GUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.gradle.api.Project.GRADLE_PROPERTIES;

public class DefaultGradlePropertiesLoader implements IGradlePropertiesLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGradlePropertiesLoader.class);

    private final StartParameterInternal startParameter;

    public DefaultGradlePropertiesLoader(StartParameterInternal startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public GradleProperties loadGradleProperties(File rootDir) {
        return loadProperties(rootDir, startParameter, getAllSystemProperties(), getAllEnvProperties());
    }

    GradleProperties loadProperties(File rootDir, StartParameterInternal startParameter, Map<String, String> systemProperties, Map<String, String> envProperties) {
        Map<String, String> defaultProperties = new HashMap<>();
        Map<String, String> overrideProperties = new HashMap<>();

        addGradleProperties(defaultProperties, new File(startParameter.getGradleHomeDir(), GRADLE_PROPERTIES));
        addGradleProperties(defaultProperties, new File(rootDir, GRADLE_PROPERTIES));
        addGradleProperties(overrideProperties, new File(startParameter.getGradleUserHomeDir(), GRADLE_PROPERTIES));

        addSystemPropertiesFromGradleProperties(defaultProperties);
        addSystemPropertiesFromGradleProperties(overrideProperties);
        System.getProperties().putAll(startParameter.getSystemPropertiesArgs());

        overrideProperties.putAll(getEnvProjectProperties(envProperties));
        overrideProperties.putAll(getSystemProjectProperties(systemProperties));
        overrideProperties.putAll(startParameter.getProjectProperties());

        return new DefaultGradleProperties(defaultProperties, overrideProperties);
    }

    Map<String, String> getAllSystemProperties() {
        return Cast.uncheckedNonnullCast(System.getProperties());
    }

    Map<String, String> getAllEnvProperties() {
        return System.getenv();
    }

    private void addGradleProperties(Map<String, String> target, File... files) {
        for (File propertyFile : files) {
            if (propertyFile != null && propertyFile.isFile()) {
                Properties properties = GUtil.loadProperties(propertyFile);
                target.putAll(Cast.uncheckedNonnullCast(properties));
            }
        }
    }

    private Map<String, String> getSystemProjectProperties(Map<String, String> systemProperties) {
        Map<String, String> systemProjectProperties = new HashMap<>();
        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            if (entry.getKey().startsWith(SYSTEM_PROJECT_PROPERTIES_PREFIX) && entry.getKey().length() > SYSTEM_PROJECT_PROPERTIES_PREFIX.length()) {
                systemProjectProperties.put(entry.getKey().substring(SYSTEM_PROJECT_PROPERTIES_PREFIX.length()), entry.getValue());
            }
        }
        LOGGER.debug("Found system project properties: {}", systemProjectProperties.keySet());
        return systemProjectProperties;
    }

    private Map<String, String> getEnvProjectProperties(Map<String, String> envProperties) {
        Map<String, String> envProjectProperties = new HashMap<>();
        for (Map.Entry<String, String> entry : envProperties.entrySet()) {
            if (entry.getKey().startsWith(ENV_PROJECT_PROPERTIES_PREFIX) && entry.getKey().length() > ENV_PROJECT_PROPERTIES_PREFIX.length()) {
                envProjectProperties.put(entry.getKey().substring(ENV_PROJECT_PROPERTIES_PREFIX.length()), entry.getValue());
            }
        }
        LOGGER.debug("Found env project properties: {}", envProjectProperties.keySet());
        return envProjectProperties;
    }

    private void addSystemPropertiesFromGradleProperties(Map<String, String> properties) {
        for (String key : properties.keySet()) {
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.setProperty(key.substring((Project.SYSTEM_PROP_PREFIX + '.').length()), properties.get(key));
            }
        }
    }
}
