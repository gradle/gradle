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
import org.gradle.api.GradleException;
import org.gradle.api.NonExtensible;
import org.gradle.api.internal.properties.GradleProperties;
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
import org.gradle.internal.logging.text.TreeFormatter;
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

        try {
            Class<P> parametersType = extractParametersTypeOf(valueSourceType);
            P parameters = parametersType != null
                ? paramsInstantiator.newInstance(parametersType)
                : null;

            // TODO - consider deferring configuration
            configureParameters(parameters, configureAction);

            return instantiateValueSourceProvider(valueSourceType, parametersType, parameters);
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException(couldNotCreateProviderOf(valueSourceType), e);
        }
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
        @Nullable P isolatedParameters
    ) {
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        services.add(GradleProperties.class, gradleProperties);
        if (isolatedParameters != null) {
            services.add(parametersType, isolatedParameters);
        }
        return instantiatorFactory
            .injectScheme()
            .withServices(services)
            .instantiator()
            .newInstance(valueSourceType);
    }

    @Nullable
    private <T, P extends ValueSourceParameters> Class<P> extractParametersTypeOf(Class<? extends ValueSource<T, P>> valueSourceType) {
        return isolationScheme.parameterTypeFor(valueSourceType, 1);
    }

    private <P extends ValueSourceParameters> void configureParameters(@Nullable P parameters, Action<? super ValueSourceSpec<P>> configureAction) {
        DefaultValueSourceSpec<P> valueSourceSpec = specInstantiator.newInstance(
            DefaultValueSourceSpec.class,
            parameters
        );
        configureAction.execute(valueSourceSpec);
    }

    private String couldNotCreateProviderOf(Class<?> valueSourceType) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Could not create provider for value source ");
        formatter.appendType(valueSourceType);
        formatter.append(".");
        return formatter.toString();
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
        public ValueProducer getProducer() {
            // For now, assume value is never calculated from a task output
            if (value != null) {
                return ValueProducer.unknown();
            } else {
                return ValueProducer.externalValue();
            }
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
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
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
                return ExecutionTimeValue.ofNullable(value.get());
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
        @Nullable
        private final P parameters;

        public DefaultObtainedValue(
            Try<T> value,
            Class<? extends ValueSource<T, P>> valueSourceType,
            Class<P> parametersType,
            @Nullable P parameters
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
