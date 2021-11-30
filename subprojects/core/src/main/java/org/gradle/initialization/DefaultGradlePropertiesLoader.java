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

    GradleProperties loadProperties(File rootDir, Map<String, String> systemProperties, Map<String, String> environmentVariables) {
        Map<String, Object> defaultProperties = new HashMap<>();
        Map<String, Object> overrideProperties = new HashMap<>();

        addGradlePropertiesFrom(startParameter.getGradleHomeDir(), defaultProperties);
        addGradlePropertiesFrom(rootDir, defaultProperties);
        addGradlePropertiesFrom(startParameter.getGradleUserHomeDir(), overrideProperties);

        // TODO:configuration-cache What happens when a system property is set from a Gradle property and
        //    that same system property is then used to set a Gradle property from an included build?
        //    e.g., included-build/gradle.properties << systemProp.org.gradle.project.fromSystemProp=42
        setSystemPropertiesFromGradleProperties(defaultProperties);
        setSystemPropertiesFromGradleProperties(overrideProperties);
        System.getProperties().putAll(startParameter.getSystemPropertiesArgs());

        overrideProperties.putAll(projectPropertiesFromEnvironmentVariables(environmentVariables));
        overrideProperties.putAll(projectPropertiesFromSystemProperties(systemProperties));
        overrideProperties.putAll(startParameter.getProjectProperties());

        return new DefaultGradleProperties(defaultProperties, overrideProperties);
    }

    private void addGradlePropertiesFrom(File dir, Map<String, Object> target) {
        Map<String, String> propertiesFile = environment.propertiesFile(new File(dir, GRADLE_PROPERTIES));
        if (propertiesFile != null) {
            target.putAll(propertiesFile);
        }
    }

    private Map<String, String> projectPropertiesFromSystemProperties(Map<String, String> systemProperties) {
        // TODO:configuration-cache collect these system properties as inputs
        Map<String, String> systemProjectProperties = selectByPrefix(systemProperties, SYSTEM_PROJECT_PROPERTIES_PREFIX);
        LOGGER.debug("Found system project properties: {}", systemProjectProperties.keySet());
        return systemProjectProperties;
    }

    private Map<String, String> projectPropertiesFromEnvironmentVariables(Map<String, String> environmentVariables) {
        // TODO:configuration-cache collect these environment variables as inputs
        Map<String, String> envProjectProperties = selectByPrefix(environmentVariables, ENV_PROJECT_PROPERTIES_PREFIX);
        LOGGER.debug("Found env project properties: {}", envProjectProperties.keySet());
        return envProjectProperties;
    }

    private Map<String, String> selectByPrefix(Map<String, String> properties, String prefix) {
        Map<String, String> result = new HashMap<>();
        int prefixLength = prefix.length();
        for (String key : properties.keySet()) {
            if (key.length() > prefixLength && key.startsWith(prefix)) {
                result.put(key.substring(prefixLength), properties.get(key));
            }
        }
        return result;
    }

    private void setSystemPropertiesFromGradleProperties(Map<String, Object> properties) {
        if (properties.isEmpty()) {
            return;
        }
        String prefix = Project.SYSTEM_PROP_PREFIX + '.';
        int prefixLength = prefix.length();
        for (String key : properties.keySet()) {
            if (key.length() > prefixLength && key.startsWith(prefix)) {
                System.setProperty(key.substring(prefixLength), (String)properties.get(key));
            }
        }
    }
}
