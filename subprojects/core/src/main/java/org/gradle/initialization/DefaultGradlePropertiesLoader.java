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

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.properties.MutableGradleProperties;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.Project.GRADLE_PROPERTIES;

public class DefaultGradlePropertiesLoader implements IGradlePropertiesLoader {

    private final StartParameterInternal startParameter;

    private final Environment environment;

    public DefaultGradlePropertiesLoader(StartParameterInternal startParameter, Environment environment) {
        this.startParameter = startParameter;
        this.environment = environment;
    }

    @Override
    public MutableGradleProperties loadGradleProperties(File rootDir) {
        return loadProperties(rootDir);
    }

    private MutableGradleProperties loadProperties(File rootDir) {
        Map<String, Object> defaultProperties = new HashMap<>();
        Map<String, Object> overrideProperties = new HashMap<>();

        addGradlePropertiesFrom(startParameter.getGradleHomeDir(), defaultProperties);
        addGradlePropertiesFrom(rootDir, defaultProperties);
        addGradlePropertiesFrom(startParameter.getGradleUserHomeDir(), overrideProperties);

        return new DefaultGradleProperties(defaultProperties, overrideProperties);
    }

    private void addGradlePropertiesFrom(File dir, Map<String, Object> target) {
        Map<String, String> propertiesFile = environment.propertiesFile(new File(dir, GRADLE_PROPERTIES));
        if (propertiesFile != null) {
            target.putAll(propertiesFile);
        }
    }
}
