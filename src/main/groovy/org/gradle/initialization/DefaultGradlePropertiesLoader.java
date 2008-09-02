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
import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultGradlePropertiesLoader implements IGradlePropertiesLoader {
    private Map<String, String> gradleProperties = new HashMap<String, String>();

    public void loadGradleProperties(File rootDir, StartParameter startParameter) {
        addGradleProperties(
                new File(rootDir, Project.GRADLE_PROPERTIES),
                new File(startParameter.getGradleUserHomeDir(), Project.GRADLE_PROPERTIES));
    }

    private void addGradleProperties(File... files) {
        for (File propertyFile : files) {
            if (propertyFile.isFile()) {
                Properties properties = new Properties();
                try {
                    properties.load(new FileInputStream(propertyFile));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
}
