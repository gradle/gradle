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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemPropertiesHandler {

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

        Pattern pattern = Pattern.compile("systemProp\\.(.*)");
        for (Object argument : properties.keySet()) {
            Matcher matcher = pattern.matcher(argument.toString());
            if (matcher.find()) {
                String key = matcher.group(1);
                if (key.length() > 0) {
                    propertyMap.put(key, properties.get(argument).toString());
                }
            }
        }
        return propertyMap;
    }
}
