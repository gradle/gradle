/*
 * Copyright 2021 the original author or authors.
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

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class SystemPropertySettingGradlePropertiesLoader implements IGradlePropertiesLoader {
    private final IGradlePropertiesLoader delegate;
    private final StartParameterInternal startParameter;

    public SystemPropertySettingGradlePropertiesLoader(IGradlePropertiesLoader delegate, StartParameterInternal startParameter) {
        this.delegate = delegate;
        this.startParameter = startParameter;
    }

    @Override
    public GradleProperties loadGradleProperties(File rootDir) {
        GradleProperties gradleProperties = delegate.loadGradleProperties(rootDir);
        Map<String, String> properties = gradleProperties.mergeProperties(Collections.emptyMap());
        addSystemPropertiesFromGradleProperties(properties);
        System.getProperties().putAll(startParameter.getSystemPropertiesArgs());
        return gradleProperties;
    }

    private void addSystemPropertiesFromGradleProperties(Map<String, String> properties) {
        for (String key : properties.keySet()) {
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.setProperty(key.substring((Project.SYSTEM_PROP_PREFIX + '.').length()), properties.get(key));
            }
        }
    }
}
