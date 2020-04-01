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
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.Try;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstanceGenerator;
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
    private final GradleProperties gradleProperties;
    private final AnonymousListenerBroadcast<Listener> broadcaster;
    private final IsolationScheme<ValueSource, ValueSourceParameters> isolationScheme = new IsolationScheme<>(ValueSource.class, ValueSourceParameters.class, ValueSourceParameters.None.class);
    private final InstanceGenerator paramsInstantiator;
    private final InstanceGenerator specInstantiator;

    public DefaultValueSourceProviderFactory(
        ListenerManager listenerManager,
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        GradleProperties gradleProperties,
        ServiceLookup services
    ) {
        this.broadcaster = listenerManager.createAnonymousBroadcaster(ValueSourceProviderFactory.Listener.class);
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
        this.gradleProperties = gradleProperties;
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

        return instantiateValueSourceProvider(valueSourceType, parametersType, parameters);
    }

    @Override
    public void addListener(Listener listener) {
        broadcaster.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        broadcaster.remove(listener);
    }

    @Override
    @NotNull
    public <T, P extends ValueSourceParameters> Provider<T> instantiateValueSourceProvider(Class<? extends ValueSource<T, P>> valueSourceType, Class<P> parametersType, P parameters) {
        return new ValueSourceProvider<>(valueSourceType, parametersType, parameters);
    }

    @NotNull
    public <T, P extends ValueSourceParameters> ValueSource<T, P> instantiateValueSource(
        Class<? extends ValueSource<T, P>> valueSourceType,
        Class<P> parametersType,
        P isolatedParameters
    ) {
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        services.add(parametersType, isolatedParameters);
        services.add(GradleProperties.class, gradleProperties);
        return instantiatorFactory
            .injectScheme()
            .withServices(services)
            .instantiator()
            .newInstance(valueSourceType);
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

    public class ValueSourceProvider<T, P extends ValueSourceParameters> extends AbstractMinimalProvider<T> {

        private final Class<? extends ValueSource<T, P>> valueSourceType;
        private final Class<P> parametersType;
        private final P parameters;

        @Nullable
        private Try<T> value = null;

        public ValueSourceProvider(
            Class<? extends ValueSource<T, P>> valueSourceType,
            Class<P> parametersType,
            P parameters
        ) {
            this.valueSourceType = valueSourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
        }

        public Class<? extends ValueSource<T, P>> getValueSourceType() {
            return valueSourceType;
        }

        public Class<P> getParametersType() {
            return parametersType;
        }

        public P getParameters() {
            return parameters;
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            // For now, assume value is never calculated from a task output
            return true;
        }

        @Override
        public boolean isValueProducedByTask() {
            return value == null;
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Nullable
        public Try<T> getObtainedValueOrNull() {
            return value;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        @Override
        protected Value<? extends T> calculateOwnValue() {
            synchronized (this) {
                if (value == null) {

                    // TODO - add more information to exception
                    value = Try.ofFailable(() -> obtainValueFromSource());

                    onValueObtained();
                }
                return Value.ofNullable(value.get());
            }
        }

        @Override
        public ExecutionTimeValue<T> calculateExecutionTimeValue() {
            if (value != null) {
                return ExecutionTimeValue.fixedValue(value.get());
            } else {
                return ExecutionTimeValue.changingValue(this);
            }
        }

        @Nullable
        private T obtainValueFromSource() {
            return instantiateValueSource(
                valueSourceType,
                parametersType,
                isolateParameters()
            ).obtain();
        }

        private P isolateParameters() {
            // TODO - consider if should hold the project lock to do the isolation
            return (P) isolatableFactory.isolate(parameters).isolate();
        }

        private void onValueObtained() {
            broadcaster.getSource().valueObtained(
                new DefaultObtainedValue(
                    value,
                    valueSourceType,
                    parametersType,
                    parameters
                )
            );
        }
    }

    private static class DefaultObtainedValue<T, P extends ValueSourceParameters> implements Listener.ObtainedValue<T, P> {

        private final Try<T> value;
        private final Class<? extends ValueSource<T, P>> valueSourceType;
        private final Class<P> parametersType;
        private final P parameters;

        public DefaultObtainedValue(
            Try<T> value,
            Class<? extends ValueSource<T, P>> valueSourceType,
            Class<P> parametersType,
            P parameters
        ) {
            this.value = value;
            this.valueSourceType = valueSourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
        }

        @Override
        public Try<T> getValue() {
            return value;
        }

        @Override
        public Class<? extends ValueSource<T, P>> getValueSourceType() {
            return valueSourceType;
        }

        @Override
        public Class<P> getValueSourceParametersType() {
            return parametersType;
        }

        @Override
        public P getValueSourceParameters() {
            return parameters;
        }
    }
}
