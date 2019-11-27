/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.Try;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceLookup;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class DefaultValueSourceProviderFactory implements ValueSourceProviderFactory {

    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;
    private final IsolationScheme<ValueSource, ValueSourceParameters> isolationScheme = new IsolationScheme<>(ValueSource.class, ValueSourceParameters.class, ValueSourceParameters.None.class);
    private final InstanceGenerator paramsInstantiator;
    private final InstanceGenerator specInstantiator;

    public DefaultValueSourceProviderFactory(InstantiatorFactory instantiatorFactory, IsolatableFactory isolatableFactory, ServiceLookup services) {
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
        // TODO - dedupe logic copied from DefaultBuildServicesRegistry
        this.paramsInstantiator = instantiatorFactory.decorateScheme().withServices(services).instantiator();
        this.specInstantiator = instantiatorFactory.decorateLenientScheme().withServices(services).instantiator();
    }

    @Override
    public <T, P extends ValueSourceParameters> Provider<T> createProviderOf(Class<? extends ValueSource<T, P>> valueSourceType, Action<? super ValueSourceSpec<P>> configureAction) {

        Class<P> parametersType = extractParametersTypeOf(valueSourceType);
        P parameters = paramsInstantiator.newInstance(parametersType);

        // TODO - consider deferring configuration
        configureParameters(parameters, configureAction);

        return new DefaultValueSourceProvider(
            valueSourceType,
            parametersType,
            parameters,
            instantiatorFactory.injectScheme()
        );
    }

    @NotNull
    private <T, P extends ValueSourceParameters> Class<P> extractParametersTypeOf(Class<? extends ValueSource<T, P>> valueSourceType) {
        Class<P> parametersType = isolationScheme.parameterTypeFor(valueSourceType, 1);
        if (parametersType == null) {
            throw new IllegalArgumentException();
        }
        return parametersType;
    }

    private <P extends ValueSourceParameters> void configureParameters(P parameters, Action<? super ValueSourceSpec<P>> configureAction) {
        DefaultValueSourceSpec<P> valueSourceSpec = specInstantiator.newInstance(
            DefaultValueSourceSpec.class,
            parameters
        );
        configureAction.execute(valueSourceSpec);
    }

    @NonExtensible
    public abstract static class DefaultValueSourceSpec<P extends ValueSourceParameters> implements ValueSourceSpec<P> {
        private final P parameters;

        public DefaultValueSourceSpec(P parameters) {
            this.parameters = parameters;
        }

        @Override
        public P getParameters() {
            return parameters;
        }

        @Override
        public void parameters(Action<? super P> configureAction) {
            configureAction.execute(parameters);
        }
    }

    public class DefaultValueSourceProvider<T, P extends ValueSourceParameters> extends AbstractReadOnlyProvider<T> {

        private final Class<? extends ValueSource<T, P>> valueSourceType;
        private final Class<P> parametersType;
        private final ValueSourceParameters parameters;
        private final InstantiationScheme instantiationScheme;

        @Nullable
        private Try<T> value = null;

        public DefaultValueSourceProvider(
            Class<? extends ValueSource<T, P>> valueSourceType,
            Class parametersType,
            ValueSourceParameters parameters,
            InstantiationScheme instantiationScheme
        ) {
            this.valueSourceType = valueSourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
            this.instantiationScheme = instantiationScheme;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        @Nullable
        @Override
        public T getOrNull() {
            synchronized (this) {
                if (value == null) {
                    // TODO - dedupe logic copied from DefaultBuildServicesRegistry
                    DefaultServiceRegistry services = new DefaultServiceRegistry();
                    // TODO - consider if should hold the project lock to do the isolation
                    P isolatedParameters = (P) isolatableFactory.isolate(parameters).isolate();
                    services.add(parametersType, isolatedParameters);
                    // TODO - add more information to exception
                    value = Try.ofFailable(() -> realizeValueSource(services).obtain());
                }
                return value.get();
            }
        }

        @NotNull
        private ValueSource<T, P> realizeValueSource(DefaultServiceRegistry services) {
            return instantiationScheme.withServices(services).instantiator().newInstance(valueSourceType);
        }
    }
}
