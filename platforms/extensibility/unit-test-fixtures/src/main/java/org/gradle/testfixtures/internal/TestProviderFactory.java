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

package org.gradle.testfixtures.internal;

import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.ValueSourceProviderFactory;
import org.gradle.api.internal.provider.sources.process.ProcessOutputProviderFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.event.ListenerManager;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class TestProviderFactory extends DefaultProviderFactory {
    private final Map<String, String> testEnvironmentVariables = new HashMap<>();
    private final Map<String, String> testSystemProperties = new HashMap<>();
    private final Map<String, String> testGradleProperties = new HashMap<>();

    public TestProviderFactory(
        @Nullable ValueSourceProviderFactory valueSourceProviderFactory,
        @Nullable ProcessOutputProviderFactory processOutputProviderFactory,
        @Nullable ListenerManager listenerManager
    ) {
        super(valueSourceProviderFactory, processOutputProviderFactory, listenerManager);
    }

    public void setTestVariables(TestVariables testVariables) {
        testEnvironmentVariables.putAll(testVariables.getEnvironmentVariables());
        testSystemProperties.putAll(testVariables.getSystemProperties());
        testGradleProperties.putAll(testVariables.getGradleProperties());
    }

    @Override
    public Provider<String> environmentVariable(Provider<String> variableName) {
        return variableName.map(testEnvironmentVariables::get).orElse(super.environmentVariable(variableName));
    }

    @Override
    public Provider<Map<String, String>> environmentVariablesPrefixedBy(Provider<String> variableNamePrefix) {
        return allVariablesPrefixedBy(variableNamePrefix,
            testEnvironmentVariables,
            super.environmentVariablesPrefixedBy(variableNamePrefix));
    }

    @Override
    public Provider<String> systemProperty(Provider<String> propertyName) {
        return propertyName.map(testSystemProperties::get).orElse(super.systemProperty(propertyName));
    }

    @Override
    public Provider<Map<String, String>> systemPropertiesPrefixedBy(Provider<String> variableNamePrefix) {
        return allVariablesPrefixedBy(variableNamePrefix,
            testSystemProperties,
            super.systemPropertiesPrefixedBy(variableNamePrefix));
    }

    @Override
    public Provider<String> gradleProperty(Provider<String> propertyName) {
        return propertyName.map(testGradleProperties::get).orElse(super.gradleProperty(propertyName));
    }

    @Override
    public Provider<Map<String, String>> gradlePropertiesPrefixedBy(Provider<String> variableNamePrefix) {
        return allVariablesPrefixedBy(variableNamePrefix,
            testGradleProperties,
            super.gradlePropertiesPrefixedBy(variableNamePrefix));
    }

    private static Provider<Map<String, String>> allVariablesPrefixedBy(
        Provider<String> variableNamePrefix,
        Map<String, String> testVars,
        Provider<Map<String, String>> realVars
    ) {
        return variableNamePrefix
            .map(prefix -> testVars.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
            .zip(realVars, (test, real) -> {
                Map<String, String> result = new HashMap<>();
                result.putAll(real);
                result.putAll(test);
                return result;
            });
    }
}
