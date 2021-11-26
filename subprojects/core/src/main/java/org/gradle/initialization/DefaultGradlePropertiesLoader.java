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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.Project.GRADLE_PROPERTIES;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultGradlePropertiesLoader implements IGradlePropertiesLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultGradlePropertiesLoader.class);

    private final StartParameterInternal startParameter;
    private final Environment environment;

    public DefaultGradlePropertiesLoader(StartParameterInternal startParameter, Environment environment) {
        this.startParameter = startParameter;
        this.environment = environment;
    }

    @Override
    public GradleProperties loadGradleProperties(File rootDir) {
        return loadProperties(rootDir, uncheckedNonnullCast(System.getProperties()), System.getenv());
    }

    GradleProperties loadProperties(File rootDir, Map<String, String> systemProperties, Map<String, String> envProperties) {
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

    private void addGradleProperties(Map<String, String> target, File propertyFile) {
        Map<String, String> propertiesFile = environment.propertiesFile(propertyFile);
        if (propertiesFile != null) {
            target.putAll(propertiesFile);
        }
    }

    private Map<String, String> getSystemProjectProperties(Map<String, String> systemProperties) {
        // TODO:configuration-cache collect these system properties as inputs
        Map<String, String> systemProjectProperties = selectByPrefix(systemProperties, SYSTEM_PROJECT_PROPERTIES_PREFIX);
        LOGGER.debug("Found system project properties: {}", systemProjectProperties.keySet());
        return systemProjectProperties;
    }

    private Map<String, String> getEnvProjectProperties(Map<String, String> envProperties) {
        // TODO:configuration-cache collect these environment variables as inputs
        Map<String, String> envProjectProperties = selectByPrefix(envProperties, ENV_PROJECT_PROPERTIES_PREFIX);
        LOGGER.debug("Found env project properties: {}", envProjectProperties.keySet());
        return envProjectProperties;
    }

    private Map<String, String> selectByPrefix(Map<String, String> properties, String prefix) {
        int prefixLength = prefix.length();
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (key.length() > prefixLength && key.startsWith(prefix)) {
                result.put(key.substring(prefixLength), entry.getValue());
            }
        }
        return result;
    }

    private void addSystemPropertiesFromGradleProperties(Map<String, String> properties) {
        if (properties.isEmpty()) {
            return;
        }
        String prefix = Project.SYSTEM_PROP_PREFIX + '.';
        for (String key : properties.keySet()) {
            if (key.startsWith(prefix)) {
                System.setProperty(key.substring(prefix.length()), properties.get(key));
            }
        }
    }
}
