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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.TestEngineConfigurationParameters;
import org.gradle.api.plugins.jvm.TestEngineConfigurationParameters.None;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultTestEngineParametersContainer implements TestEngineParametersContainerInternal {
    private final DomainObjectSet<TestEngineConfigurationParameters> testEngineConfigurations;
    private final TestEngineContainerInternal testEngines;
    private final ObjectFactory objectFactory;

    @Inject
    public DefaultTestEngineParametersContainer(TestEngineContainerInternal testEngines, ObjectFactory objectFactory) {
        this.testEngines = testEngines;
        this.objectFactory = objectFactory;
        this.testEngineConfigurations = objectFactory.domainObjectSet(TestEngineConfigurationParameters.class);
    }

    @Override
    public <T extends TestEngineConfigurationParameters> void withType(Class<T> type, Action<? super T> action) {
        if (type == None.class) {
            throw new IllegalArgumentException("Cannot configure parameters with type None");
        }
        testEngineConfigurations.withType(type, action);
    }

    @Override
    public Set<CommandLineArgumentProvider> getCommandLineArgumentProviders() {
        testEngines.getTestEngines().forEach(testEngine ->
            testEngineConfigurations.add(objectFactory.newInstance(testEngine.getConfigurationParametersType())));
        return testEngineConfigurations.stream()
            .map(TestEngineConfigurationParameters::mapToConfigurationParameters)
            .collect(Collectors.toSet());
    }
}
