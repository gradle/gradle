/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.jvm;

import org.gradle.StartParameter;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link org.gradle.api.provider.ProviderFactory} which only provides environment variables, system properties, and Gradle properties.
 * Fully featured provider factory is not available at this service scope.
 */
class DaemonProviderFactory extends DefaultProviderFactory {

    private final StartParameter parameters;
    private final Map<String, String> propertiesOverridenMap = new HashMap<>();

    public DaemonProviderFactory(StartParameter parameters) {
        this.parameters = parameters;
    }

    @Override
    @Nonnull
    public Provider<String> environmentVariable(@Nonnull String variableName) {
        return environmentVariable(Providers.of(variableName));
    }

    @Override
    @Nonnull
    public Provider<String> environmentVariable(Provider<String> variableName) {
        return variableName.map(System::getenv);
    }

    @Override
    @Nonnull
    public Provider<String> systemProperty(@Nonnull String propertyName) {
        return systemProperty(Providers.of(propertyName));
    }

    @Override
    @Nonnull
    public Provider<String> systemProperty(Provider<String> propertyName) {
        return propertyName.map(this::getProperty);
    }

    @Override
    @Nonnull
    public Provider<String> gradleProperty(@Nonnull String propertyName) {
        return gradleProperty(Providers.of(propertyName));
    }

    @Override
    @Nonnull
    public Provider<String> gradleProperty(Provider<String> propertyName) {
        return propertyName.map(this::getProperty);
    }

    public void overrideProperty(String key, boolean value) {
        propertiesOverridenMap.put(key, String.valueOf(value));
    }

    @Nullable
    private String getProperty(String key) {
        if (propertiesOverridenMap.containsKey(key)) {
            return propertiesOverridenMap.get(key);
        }
        String value = System.getProperty(key);
        if (value != null) {
            return value;
        }
        value = parameters.getProjectProperties().get(key);
        if (value != null) {
            return value;
        }
        return parameters.getSystemPropertiesArgs().get(key);
    }
}
