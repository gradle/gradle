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

package org.gradle.api.plugins.jvm.internal.testing.engines;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngine;
import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngineConfigurationParameters;
import org.gradle.api.plugins.jvm.testing.engines.JUnitPlatformTestEngineParameters;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.service.ServiceLookup;

import java.util.Collections;

import static org.gradle.internal.Cast.uncheckedCast;

public class JUnitPlatformTestEngineFactory {
    private final InstantiatorFactory instantiatorFactory;
    private final ObjectFactory objectFactory;
    private final ServiceLookup parentServices;

    public JUnitPlatformTestEngineFactory(InstantiatorFactory instantiatorFactory, ObjectFactory objectFactory, ServiceLookup parentServices) {
        this.instantiatorFactory = instantiatorFactory;
        this.objectFactory = objectFactory;
        this.parentServices = parentServices;
    }

    public <T extends JUnitPlatformTestEngineParameters<?>> JUnitPlatformTestEngine<T> create(Class<? extends JUnitPlatformTestEngine<T>> engineClass, Action<T> paramsAction) {
        JUnitPlatformTestEngine<T> engine = create(engineClass);
        paramsAction.execute(engine.getParameters());
        return engine;
    }

    public <T extends JUnitPlatformTestEngineParameters<?>> JUnitPlatformTestEngine<T> create(Class<? extends JUnitPlatformTestEngine<T>> engineClass) {
        IsolationScheme<JUnitPlatformTestEngine<?>, JUnitPlatformTestEngineParameters<?>> isolationScheme = new IsolationScheme<>(uncheckedCast(JUnitPlatformTestEngine.class), uncheckedCast(JUnitPlatformTestEngineParameters.class), JUnitPlatformTestEngineParameters.None.class);
        Class<T> parametersType = isolationScheme.parameterTypeFor(engineClass);
        T parameters = parametersType == null ? null : createParameters(parametersType);
        ServiceLookup lookup = isolationScheme.servicesForImplementation(parameters, parentServices, Collections.emptyList(), p -> true);
        return instantiatorFactory.decorate(lookup).newInstance(engineClass);
    }

    private <T extends JUnitPlatformTestEngineParameters<?>> T createParameters(Class<T> type) {
        IsolationScheme<JUnitPlatformTestEngineParameters<?>, JUnitPlatformTestEngineConfigurationParameters> isolationScheme = new IsolationScheme<>(uncheckedCast(JUnitPlatformTestEngineParameters.class), JUnitPlatformTestEngineConfigurationParameters.class, JUnitPlatformTestEngineConfigurationParameters.None.class);
        Class<? extends JUnitPlatformTestEngineConfigurationParameters> configurationParametersType = isolationScheme.parameterTypeFor(type);
        JUnitPlatformTestEngineConfigurationParameters configurationParameters = configurationParametersType == null ? null : objectFactory.newInstance(configurationParametersType);
        ServiceLookup lookup = isolationScheme.servicesForImplementation(configurationParameters, parentServices, Collections.emptyList(), p -> true);
        return instantiatorFactory.decorate(lookup).newInstance(type);
    }
}
