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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.internal.provider.CollectionPropertyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.RegistersTestEngines;
import org.gradle.api.plugins.jvm.TestEngine;
import org.gradle.api.plugins.jvm.TestEngineRegistration;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceLookupException;
import org.gradle.internal.service.ServiceRegistry;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

abstract public class DefaultTestEngineContainer implements TestEngineContainerInternal {
    private final RegistersTestEnginesImpl registry;
    private final RegistersTestEnginesImpl conventions;
    //TODO rather than backing this with a domain object set, maybe we can just use a CollectionEventRegister
    // and handle the configuration rules directly
    private final DomainObjectSet<TestEngine> engines;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry parentServices;
    private final ObjectFactory objectFactory;

    @Inject
    public DefaultTestEngineContainer(ObjectFactory objectFactory, ServiceRegistry parentServices, InstantiatorFactory instantiatorFactory) {
        this.registry = new RegistersTestEnginesImpl(objectFactory);
        this.conventions = new RegistersTestEnginesImpl(objectFactory);
        this.engines = objectFactory.domainObjectSet(TestEngine.class);
        this.instantiatorFactory = instantiatorFactory;
        this.parentServices = parentServices;
        this.objectFactory = objectFactory;
        registry.implementations.convention(conventions.implementations);
    }

    @Override
    public void convention(Action<RegistersTestEngines> action) {
        conventions.clear();
        action.execute(conventions);
    }

    @Override
    public <T extends TestEngineRegistration> RegistersTestEngines register(Class<? extends TestEngine<T>> engineClass) {
        registry.register(engineClass);
        return this;
    }

    @Override
    public <T extends TestEngineRegistration> RegistersTestEngines register(Class<? extends TestEngine<T>> engineClass, Action<T> testEngineRegistrationConfiguration) {
        registry.register(engineClass, testEngineRegistrationConfiguration);
        return this;
    }

    @Override
    public <T extends TestEngineRegistration> void withType(Class<? extends TestEngine<T>> engineClass, Action<T> testEngineRegistrationConfiguration) {
        engines.withType(engineClass).configureEach(engine -> testEngineRegistrationConfiguration.execute(engine.getRegistration()));
    }

    @Override
    public Set<? extends TestEngine<?>> getTestEngines() {
        Set<Class<? extends TestEngine<?>>> registeredTypes = Sets.newHashSet();
        Optional.ofNullable(registry.implementations.getOrNull()).orElse(Collections.emptySet())
            .forEach(testEngine -> {
                if (registeredTypes.add(testEngine.engineClass)) {
                    engines.add(newEngineInstance(testEngine.engineClass));
                }
                engines.withType(testEngine.engineClass).configureEach(engine -> testEngine.registrationConfiguration.execute(Cast.uncheckedCast(engine.getRegistration())));
            });
        return immutable(engines);
    }

    @Override
    public boolean isEmpty() {
        return internal(registry.implementations).size() == 0;
    }

    private static CollectionPropertyInternal<TestEngineTypeRegistration<?>, Set<TestEngineTypeRegistration<?>>> internal(SetProperty<TestEngineTypeRegistration<?>> registrations) {
        return Cast.uncheckedCast(registrations);
    }

    private <T extends TestEngineRegistration> TestEngine<T> newEngineInstance(Class<? extends TestEngine<T>> engineClass) {
        IsolationScheme<TestEngine<?>, TestEngineRegistration> isolationScheme = new IsolationScheme<>(Cast.uncheckedCast(TestEngine.class), TestEngineRegistration.class, TestEngineRegistration.None.class);
        Class<T> parametersType = isolationScheme.parameterTypeFor(engineClass);
        if (parametersType == null) {
            throw new ServiceLookupException(String.format("Cannot infer test engine parameters for %s", engineClass.getName()));
        }
        T registration = objectFactory.newInstance(parametersType);
        ServiceLookup lookup = isolationScheme.servicesForImplementation(registration, parentServices, Collections.emptyList(), p -> true);
        return instantiatorFactory.decorate(lookup).newInstance(engineClass);
    }

    private static Set<? extends TestEngine<?>> immutable(DomainObjectSet<TestEngine> engines) {
        ImmutableSet.Builder<TestEngine<?>> builder = ImmutableSet.builder();
        engines.forEach(builder::add);
        return builder.build();
    }

    private static class TestEngineTypeRegistration<T extends TestEngineRegistration> {
        final Class<? extends TestEngine<T>> engineClass;

        final Action<T> registrationConfiguration;

        public TestEngineTypeRegistration(Class<? extends TestEngine<T>> engineClass, Action<T> registrationConfiguration) {
            this.engineClass = engineClass;
            this.registrationConfiguration = registrationConfiguration;
        }
    }

    private static class RegistersTestEnginesImpl implements RegistersTestEngines {
        private final SetProperty<TestEngineTypeRegistration<?>> implementations;

        public RegistersTestEnginesImpl(ObjectFactory objectFactory) {
            this.implementations = Cast.uncheckedCast(objectFactory.setProperty(TestEngineTypeRegistration.class));
        }

        @Override
        public <T extends TestEngineRegistration> RegistersTestEngines register(Class<? extends TestEngine<T>> engineClass) {
            implementations.add(new TestEngineTypeRegistration<>(engineClass, Actions.doNothing()));
            return this;
        }

        @Override
        public <T extends TestEngineRegistration> RegistersTestEngines register(Class<? extends TestEngine<T>> engineClass, Action<T> testEngineRegistrationConfiguration) {
            implementations.add(new TestEngineTypeRegistration<>(engineClass, testEngineRegistrationConfiguration));
            return this;
        }

        void clear() {
            implementations.empty();
        }
    }
}
