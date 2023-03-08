/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.initialization.properties;

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.initialization.EnvironmentChangeTracker;

import java.util.Map;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultSystemPropertiesInstaller implements SystemPropertiesInstaller {

    private final EnvironmentChangeTracker environmentChangeTracker;

    private final StartParameterInternal startParameter;

    private final GradleInternal gradleInternal;

    public DefaultSystemPropertiesInstaller(
        EnvironmentChangeTracker environmentChangeTracker,
        StartParameterInternal startParameter,
        GradleInternal gradleInternal
    ) {
        this.environmentChangeTracker = environmentChangeTracker;
        this.startParameter = startParameter;
        this.gradleInternal = gradleInternal;
    }

    @Override
    public void setSystemPropertiesFrom(GradleProperties gradleProperties) {
        // TODO:configuration-cache What happens when a system property is set from a Gradle property and
        //    that same system property is then used to set a Gradle property from an included build?
        //    e.g., included-build/gradle.properties << systemProp.org.gradle.project.fromSystemProp=42
        setSystemPropertiesFromGradleProperties(gradleProperties.getProperties());
        setSystemPropertiesFromStartParameter();
    }

    private void setSystemPropertiesFromStartParameter() {
        Map<String, String> systemPropertiesArgs = startParameter.getSystemPropertiesArgs();

        for (String key : systemPropertiesArgs.keySet()) {
            environmentChangeTracker.systemPropertyOverridden(key);
        }

        System.getProperties().putAll(systemPropertiesArgs);
    }

    private void setSystemPropertiesFromGradleProperties(Map<String, Object> properties) {
        if (properties.isEmpty()) {
            return;
        }
        String prefix = Project.SYSTEM_PROP_PREFIX + '.';
        int prefixLength = prefix.length();
        for (String key : properties.keySet()) {
            if (key.length() > prefixLength && key.startsWith(prefix)) {
                String systemPropertyKey = key.substring(prefixLength);
                if (!gradleInternal.isRootBuild()) {
                    environmentChangeTracker.systemPropertyLoaded(systemPropertyKey, properties.get(key), System.getProperty(systemPropertyKey));
                }
                System.setProperty(systemPropertyKey, uncheckedNonnullCast(properties.get(key)));
            }
        }
    }
}
