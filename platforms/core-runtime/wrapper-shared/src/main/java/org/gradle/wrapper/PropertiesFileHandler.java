/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.wrapper;

import org.gradle.util.internal.ArgumentsSplitter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PropertiesFileHandler {
    private static final String SYSTEM_PROP_PREFIX = "systemProp.";
    private static final String JVMARGS_PROP_KEY = "org.gradle.jvmargs";
    private static final String DEBUG_PROP_KEY = "org.gradle.debug";
    private static final String DEBUG_PROP_VALUE = "true";

    public static Map<String, String> getSystemProperties(File propertiesFile) {
        if (!propertiesFile.isFile()) {
            return Collections.emptyMap();
        }
        Properties properties = loadProperties(propertiesFile);
        Map<String, String> systemProperties = new HashMap<String, String>();
        for (Object argument : properties.keySet()) {
            if (argument.toString().startsWith(SYSTEM_PROP_PREFIX)) {
                String key = argument.toString().substring(SYSTEM_PROP_PREFIX.length());
                if (key.length() > 0) {
                    systemProperties.put(key, properties.get(argument).toString());
                }
            }
        }
        return Collections.unmodifiableMap(systemProperties);
    }

    public static List<String> getJvmArgs(File propertiesFile) {
        if (!propertiesFile.isFile()) {
            return Collections.emptyList();
        }
        Properties properties = loadProperties(propertiesFile);
        List<String> jvmArgs = new ArrayList<String>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if (JVMARGS_PROP_KEY.equals(entry.getKey())) {
                Object jvmArgsPropValue = entry.getValue();
                if (jvmArgsPropValue instanceof String) {
                    jvmArgs.addAll(ArgumentsSplitter.split((String) jvmArgsPropValue));
                }
            } else if (DEBUG_PROP_KEY.equals(entry.getKey()) && DEBUG_PROP_VALUE.equals(entry.getValue())) {
                jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
            }
        }
        return Collections.unmodifiableList(jvmArgs);
    }

    private static Properties loadProperties(File propertiesFile) {
        Properties properties = new Properties();
        try {
            FileInputStream inStream = new FileInputStream(propertiesFile);
            try {
                properties.load(inStream);
            } finally {
                inStream.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error when loading properties file=" + propertiesFile, e);
        }
        return properties;
    }
}
