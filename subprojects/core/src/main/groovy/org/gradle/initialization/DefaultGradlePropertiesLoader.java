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

import org.gradle.StartParameter;
import org.gradle.util.GUtil;
import org.gradle.api.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * @author Hans Dockter
 */
public class DefaultGradlePropertiesLoader implements IGradlePropertiesLoader {
    private static Logger logger = LoggerFactory.getLogger(DefaultGradlePropertiesLoader.class);

    private Map<String, String> gradleProperties = new HashMap<String, String>();

    public void loadProperties(File settingsDir, StartParameter startParameter) {
        loadProperties(settingsDir, startParameter, getAllSystemProperties(), getAllEnvProperties());
    }

    void loadProperties(File settingsDir, StartParameter startParameter, Map<String, String> systemProperties, Map<String, String> envProperties) {
        gradleProperties.clear();
        addGradleProperties(new File(settingsDir, Project.GRADLE_PROPERTIES), new File(startParameter.getGradleUserHomeDir(), Project.GRADLE_PROPERTIES));
        setSystemProperties(startParameter.getSystemPropertiesArgs());
        gradleProperties.putAll(getEnvProjectProperties(envProperties));
        gradleProperties.putAll(getSystemProjectProperties(systemProperties));
        gradleProperties.putAll(startParameter.getProjectProperties());
    }

    Map getAllSystemProperties() {
        return System.getProperties();
    }

    Map<String, String> getAllEnvProperties() {
        // The reason why we have an try-catch block here is for JDK 1.4 compatibility. We use the retrotranslator to produce
        // a 1.4 compatible version. But the retrotranslator is not capable of translating System.getenv to 1.4.
        // The System.getenv call is only available in 1.5. In fact 1.4 does not offer any API to read
        // environment variables. Therefore this call leads to an exception when used with 1.4. We ignore the exception in this
        // case and simply return an empty hashmap.
        try {
            return System.getenv();
        } catch (Throwable e) {
            logger.debug("The System.getenv() call has lead to an exception. Probably you are running on Java 1.4.", e);
            return Collections.emptyMap();
        }
    }

    private void addGradleProperties(File... files) {
        for (File propertyFile : files) {
            if (propertyFile.isFile()) {
                Properties properties = GUtil.loadProperties(propertyFile);
                gradleProperties.putAll(new HashMap(properties));
            }
        }
    }

    public Map<String, String> getGradleProperties() {
        return gradleProperties;
    }

    public void setGradleProperties(Map<String, String> gradleProperties) {
        this.gradleProperties = gradleProperties;
    }

    private Map<String, String> getSystemProjectProperties(Map<String, String> systemProperties) {
        Map<String, String> systemProjectProperties = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            if (entry.getKey().startsWith(SYSTEM_PROJECT_PROPERTIES_PREFIX) && entry.getKey().length() > SYSTEM_PROJECT_PROPERTIES_PREFIX.length()) {
                systemProjectProperties.put(entry.getKey().substring(SYSTEM_PROJECT_PROPERTIES_PREFIX.length()), entry.getValue());
            }
        }
        logger.debug("Found system project properties: {}", systemProjectProperties.keySet());
        return systemProjectProperties;
    }

    private Map<String, String> getEnvProjectProperties(Map<String, String> envProperties) {
        Map<String, String> envProjectProperties = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : envProperties.entrySet()) {
            if (entry.getKey().startsWith(ENV_PROJECT_PROPERTIES_PREFIX) && entry.getKey().length() > ENV_PROJECT_PROPERTIES_PREFIX.length()) {
                envProjectProperties.put(entry.getKey().substring(ENV_PROJECT_PROPERTIES_PREFIX.length()), entry.getValue());
            }
        }
        logger.debug("Found env project properties: {}", envProjectProperties.keySet());
        return envProjectProperties;
    }

    private void setSystemProperties(Map<String, String> properties) {
        System.getProperties().putAll(properties);
        addSystemPropertiesFromGradleProperties();
    }

    private void addSystemPropertiesFromGradleProperties() {
        for (String key : gradleProperties.keySet()) {
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.setProperty(key.substring((Project.SYSTEM_PROP_PREFIX + '.').length()), gradleProperties.get(key));
            }
        }
    }
}
