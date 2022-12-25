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
import org.gradle.process.ExecOperations;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DefaultValueSourceProviderFactory implements ValueSourceProviderFactory {

    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;
    private final GradleProperties gradleProperties;
    private final ExecOperations execOperations;
    private final AnonymousListenerBroadcast<ValueListener> valueBroadcaster;
    private final AnonymousListenerBroadcast<ComputationListener> computationBroadcaster;
    private final IsolationScheme<ValueSource, ValueSourceParameters> isolationScheme = new IsolationScheme<>(ValueSource.class, ValueSourceParameters.class, ValueSourceParameters.None.class);
    private final InstanceGenerator paramsInstantiator;
    private final InstanceGenerator specInstantiator;

    public DefaultValueSourceProviderFactory(
        ListenerManager listenerManager,
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        GradleProperties gradleProperties,
        ExecOperations execOperations,
        ServiceLookup services
    ) {
        this.valueBroadcaster = listenerManager.createAnonymousBroadcaster(ValueListener.class);
        this.computationBroadcaster = listenerManager.createAnonymousBroadcaster(ComputationListener.class);
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
        this.gradleProperties = gradleProperties;
        this.execOperations = execOperations;
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
    public void addValueListener(ValueListener listener) {
        valueBroadcaster.add(listener);
    }

    @Override
    public void removeValueListener(ValueListener listener) {
        valueBroadcaster.remove(listener);
    }

    @Override
    public void addComputationListener(ComputationListener listener) {
        computationBroadcaster.add(listener);
    }

    @Override
    public void removeComputationListener(ComputationListener listener) {
        computationBroadcaster.remove(listener);
    }

    @Override
    @Nonnull
    public <T, P extends ValueSourceParameters> Provider<T> instantiateValueSourceProvider(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P parameters
    ) {
        return new ValueSourceProvider<>(
            new LazilyObtainedValue<>(valueSourceType, parametersType, parameters)
        );
    }

    @Nonnull
    public <T, P extends ValueSourceParameters> ValueSource<T, P> instantiateValueSource(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P isolatedParameters
    ) {
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        services.add(GradleProperties.class, gradleProperties);
        services.add(ExecOperations.class, execOperations);
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
    private <P extends ValueSourceParameters> P isolateParameters(@Nullable P parameters) {
        // TODO - consider if should hold the project lock to do the isolation
        return isolatableFactory.isolate(parameters).isolate();
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

    public static class ValueSourceProvider<T, P extends ValueSourceParameters> extends AbstractMinimalProvider<T> {

        protected final LazilyObtainedValue<T, P> value;

        public ValueSourceProvider(LazilyObtainedValue<T, P> value) {
            this.value = value;
        }

        public Class<? extends ValueSource<T, P>> getValueSourceType() {
            return value.sourceType;
        }

        @Override
        public String toString() {
            return String.format("valueof(%s)", getValueSourceType().getSimpleName());
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
            return Value.ofNullable(value.obtain().get());
        }
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
            ValueSource<T, P> source;
            // Return value from local to avoid nullability warnings when returning value from the field directly.
            Try<T> obtained;
            synchronized (this) {
                Try<T> cached = value;
                if (cached != null) {
                    return cached;
                }
                computationBroadcaster.getSource().beforeValueObtained();
                try {
                    // TODO - add more information to exceptions
                    // Fail fast when source can't be instantiated.
                    source = source();
                    value = obtained = Try.ofFailable(source::obtain);
                } finally {
                    computationBroadcaster.getSource().afterValueObtained();
                }
            }
            // Value obtained for the 1st time, notify listeners.
            valueBroadcaster.getSource().valueObtained(obtainedValue(obtained), source);
            return obtained;
        }

        @Nonnull
        private ValueSource<T, P> source() {
            return instantiateValueSource(
                sourceType,
                parametersType,
                isolateParameters(parameters)
            );
        }

        @Nonnull
        private DefaultObtainedValue<T, P> obtainedValue(Try<T> obtained) {
            return new DefaultObtainedValue<>(
                obtained,
                sourceType,
                parametersType,
                parameters
            );
        }
    }

    private static class DefaultObtainedValue<T, P extends ValueSourceParameters> implements ValueListener.ObtainedValue<T, P> {

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
