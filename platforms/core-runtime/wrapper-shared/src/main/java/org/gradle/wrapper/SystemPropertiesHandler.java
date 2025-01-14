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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SystemPropertiesHandler {
    static final String SYSTEM_PROP_PREFIX = "systemProp.";

    public static Map<String, String> getSystemProperties(File propertiesFile) {
        Map<String, String> propertyMap = new HashMap<String, String>();
        if (!propertiesFile.isFile()) {
            return propertyMap;
        }
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

        for (Object argument : properties.keySet()) {
            if (argument.toString().startsWith(SYSTEM_PROP_PREFIX)) {
                String key = argument.toString().substring(SYSTEM_PROP_PREFIX.length());
                if (key.length() > 0) {
                    propertyMap.put(key, properties.get(argument).toString());
                }
            }
        }
        return propertyMap;
    }

    // TODO (donat) class name is misleading; this method is not about system properties
    public static List<String> getJvmArgs(File propertiesFile) {
        List<String> jvmArgList = new ArrayList<String>();
        if (!propertiesFile.isFile()) {
            return jvmArgList;
        }
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

        // TODO (donat) naive implementation; there's probably a snippet dealing with parsing the list of jvmargs in a more dependable way
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            if ("org.gradle.jvmargs".equals(entry.getKey())) {
                String jvmargs = entry.getValue().toString(); // TODO (donat) null?
                for (String s : jvmargs.split(" ")) {
                    if (s != null && !s.isEmpty()) {
                        jvmArgList.add(s);
                    }
                }
            }
        }
        return jvmArgList;
    }
}
