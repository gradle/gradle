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
import org.gradle.api.Describable;
import org.gradle.api.GradleException;
import org.gradle.api.NonExtensible;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.Cast;
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
    private final ConfigurationTimeBarrier configurationTimeBarrier;
    private final AnonymousListenerBroadcast<Listener> broadcaster;
    private final IsolationScheme<ValueSource, ValueSourceParameters> isolationScheme = new IsolationScheme<>(ValueSource.class, ValueSourceParameters.class, ValueSourceParameters.None.class);
    private final InstanceGenerator paramsInstantiator;
    private final InstanceGenerator specInstantiator;

    public DefaultValueSourceProviderFactory(
        ConfigurationTimeBarrier configurationTimeBarrier,
        ListenerManager listenerManager,
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        GradleProperties gradleProperties,
        ServiceLookup services
    ) {
        this.configurationTimeBarrier = configurationTimeBarrier;
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
    public <T, P extends ValueSourceParameters> Provider<T> instantiateValueSourceProvider(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P parameters
    ) {
        return new NonConfigurationTimeProvider<>(
            configurationTimeBarrier,
            new LazilyObtainedValue<>(valueSourceType, parametersType, parameters)
        );
    }

    @NotNull
    public <T, P extends ValueSourceParameters> ValueSource<T, P> instantiateValueSource(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
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
        DefaultValueSourceSpec<P> valueSourceSpec = Cast.uncheckedNonnullCast(specInstantiator.newInstance(
            DefaultValueSourceSpec.class,
            parameters
        ));
        configureAction.execute(valueSourceSpec);
    }

    @Nullable
    private <P extends ValueSourceParameters> P isolateParameters(P parameters) {
        // TODO - consider if should hold the project lock to do the isolation
        return isolatableFactory.isolate(parameters).isolate();
    }

    private <T, P extends ValueSourceParameters> void valueObtained(DefaultObtainedValue<T, P> obtainedValue) {
        broadcaster.getSource().valueObtained(obtainedValue);
    }

    private String couldNotCreateProviderOf(Class<?> valueSourceType) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Could not create provider for value source ");
        formatter.appendType(valueSourceType);
        formatter.append(".");
        return formatter.toString();
    }

    @NonExtensible
    public abstract static class DefaultValueSourceSpec<P extends ValueSourceParameters>
        implements ValueSourceSpec<P> {

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

    private static class NonConfigurationTimeProvider<T, P extends ValueSourceParameters>
        extends ValueSourceProvider<T, P> {

        private ConfigurationTimeBarrier configurationTimeBarrier;

        public NonConfigurationTimeProvider(ConfigurationTimeBarrier configurationTimeBarrier, LazilyObtainedValue<T, P> value) {
            super(value);
            this.configurationTimeBarrier = configurationTimeBarrier;
        }

        @Override
        protected void vetoAtConfigurationTime() {
            if (configurationTimeBarrier.isAtConfigurationTime()) {
                throw new IllegalStateException(cannotObtainValueAtConfigurationTime());
            }
        }

        @Override
        public Provider<T> forUseAtConfigurationTime() {
            return new ConfigurationTimeProvider<>(value);
        }

        private String cannotObtainValueAtConfigurationTime() {
            TreeFormatter message = new TreeFormatter();
            message.node("Cannot obtain value from provider of ");
            if (Describable.class.isAssignableFrom(value.sourceType)) {
                message.append(((Describable) value.source()).getDisplayName());
            } else {
                message.appendType(value.sourceType);
            }
            return message
                .append(" at configuration time.")
                .node("Use a provider returned by 'forUseAtConfigurationTime()' instead.")
                .toString();
        }
    }

    private static class ConfigurationTimeProvider<T, P extends ValueSourceParameters>
        extends ValueSourceProvider<T, P> {

        public ConfigurationTimeProvider(LazilyObtainedValue<T, P> value) {
            super(value);
        }

        @Override
        protected void vetoAtConfigurationTime() {
        }

        @Override
        public Provider<T> forUseAtConfigurationTime() {
            return this;
        }
    }

    public abstract static class ValueSourceProvider<T, P extends ValueSourceParameters>
        extends AbstractMinimalProvider<T> {

        protected final LazilyObtainedValue<T, P> value;

        public ValueSourceProvider(LazilyObtainedValue<T, P> value) {
            this.value = value;
        }

        public Class<? extends ValueSource<T, P>> getValueSourceType() {
            return value.sourceType;
        }

        @Nullable
        public Class<P> getParametersType() {
            return value.parametersType;
        }

        @Nullable
        public P getParameters() {
            return value.parameters;
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.externalValue();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Nullable
        public Try<T> getObtainedValueOrNull() {
            return value.value;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        @Override
        public ExecutionTimeValue<T> calculateExecutionTimeValue() {
            if (value.hasBeenObtained()) {
                return ExecutionTimeValue.ofNullable(value.obtain().get());
            } else {
                return ExecutionTimeValue.changingValue(this);
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            vetoAtConfigurationTime();
            return Value.ofNullable(value.obtain().get());
        }

        protected abstract void vetoAtConfigurationTime();
    }

    private class LazilyObtainedValue<T, P extends ValueSourceParameters> {

        public final Class<? extends ValueSource<T, P>> sourceType;

        @Nullable
        public final Class<P> parametersType;

        @Nullable
        public final P parameters;

        @Nullable
        private volatile Try<T> value = null;

        private LazilyObtainedValue(
            Class<? extends ValueSource<T, P>> sourceType,
            @Nullable Class<P> parametersType,
            @Nullable P parameters
        ) {
            this.sourceType = sourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
        }

        public boolean hasBeenObtained() {
            return value != null;
        }

        public Try<T> obtain() {
            if (obtainValueForThe1stTime()) {
                valueObtained(obtainedValue());
            }
            return value;
        }

        private boolean obtainValueForThe1stTime() {
            boolean valueWasObtained = false;
            synchronized (this) {
                if (value == null) {
                    // TODO - add more information to exception
                    value = Try.ofFailable(() -> source().obtain());
                    valueWasObtained = true;
                }
            }
            return valueWasObtained;
        }

        @NotNull
        private ValueSource<T, P> source() {
            return instantiateValueSource(
                sourceType,
                parametersType,
                isolateParameters(parameters)
            );
        }

        @NotNull
        private DefaultObtainedValue<T, P> obtainedValue() {
            return new DefaultObtainedValue<>(
                value,
                sourceType,
                parametersType,
                parameters
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
