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
import org.gradle.api.internal.provider.ValueSupplier.ExecutionTimeValue;
import org.gradle.api.internal.provider.ValueSupplier.Value;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueFactory;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class DefaultValueSourceProviderFactory implements ValueSourceProviderFactory {

    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;
    private final GradleProperties gradleProperties;
    private final CalculatedValueFactory calculatedValueFactory;
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
        CalculatedValueFactory calculatedValueFactory,
        ExecOperations execOperations,
        ServiceLookup services
    ) {
        this.valueBroadcaster = listenerManager.createAnonymousBroadcaster(ValueListener.class);
        this.computationBroadcaster = listenerManager.createAnonymousBroadcaster(ComputationListener.class);
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
        this.gradleProperties = gradleProperties;
        this.calculatedValueFactory = calculatedValueFactory;
        this.execOperations = execOperations;
        // TODO - dedupe logic copied from DefaultBuildServicesRegistry
        // TODO: Is it intentional we use a service registry that allows all services, even internal ones, to be injected?
        //       All other usages of `IsolationScheme` use a specially crafted service registry only allowing certain services to be injected.
        this.paramsInstantiator = instantiatorFactory.decorateScheme().withServices(services).instantiator();
        this.specInstantiator = instantiatorFactory.decorateLenient(services);
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
    @NonNull
    public <T, P extends ValueSourceParameters> Provider<T> instantiateValueSourceProvider(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P parameters
    ) {
        return new ValueSourceProvider<>(
            new LazilyObtainedValue<>(valueSourceType, parametersType, parameters)
        );
    }

    @NonNull
    public <T, P extends ValueSourceParameters> ValueSource<T, P> instantiateValueSource(
        Class<? extends ValueSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P isolatedParameters
    ) {
        ServiceRegistry services = ServiceRegistryBuilder.builder()
            .displayName("value source services")
            .provider(registration -> {
                registration.add(GradleProperties.class, gradleProperties);
                registration.add(ExecOperations.class, execOperations);
                if (isolatedParameters != null) {
                    registration.add(parametersType, isolatedParameters);
                }
            })
            .build();

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
        private final LazilyObtainedValue<T, P> value;

        private ValueSourceProvider(LazilyObtainedValue<T, P> value) {
            this.value = value;
        }

        public Class<? extends ValueSource<T, P>> getValueSourceType() {
            return value.sourceType;
        }

        @Override
        protected String toStringNoReentrance() {
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

        public boolean hasBeenObtained() {
            return value.hasBeenObtained();
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        @Override
        public ExecutionTimeValue<T> calculateExecutionTimeValue() {
            if (value.hasBeenObtained()) {
                return value.obtain().asExecutionTimeValue();
            } else {
                return ExecutionTimeValue.changingValue(this);
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return value.obtain().asNullableValue();
        }
    }

    private class LazilyObtainedValue<T, P extends ValueSourceParameters> {

        public final Class<? extends ValueSource<T, P>> sourceType;

        @Nullable
        public final Class<P> parametersType;

        @Nullable
        public final P parameters;

        private final CalculatedValue<@Nullable T> value;
        // A temporary holder for the source used to obtain the value.
        // This is sent to observers alongside the actual value by a single thread.
        // The thread then clears the reference to save memory.
        private final AtomicReference<ValueSource<T, P>> sourceRef = new AtomicReference<>();

        private LazilyObtainedValue(
            Class<? extends ValueSource<T, P>> sourceType,
            @Nullable Class<P> parametersType,
            @Nullable P parameters
        ) {
            this.sourceType = sourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
            this.value = calculatedValueFactory.create(Describables.of("ValueSource of type", sourceType), () -> {
                    computationBroadcaster.getSource().beforeValueObtained();
                    try {
                        ValueSource<T, P> source = source();
                        sourceRef.set(source);
                        return source.obtain();
                    } finally {
                        computationBroadcaster.getSource().afterValueObtained();
                    }
                }
            );
        }

        public boolean hasBeenObtained() {
            return value.isFinalized();
        }

        public ObtainedValueHolder<T> obtain() {
            final @Nullable ValueSource<T, P> obtainedFrom;
            try {
                value.finalizeIfNotAlready();
            } finally {
                // Don't leak the source implementation even if obtaining its value throws.
                // This is mostly a theoretical possibility, but the call above is blocking, so it can be interrupted.
                obtainedFrom = sourceRef.getAndSet(null);
            }
            Try<@Nullable T> obtained = value.getValue();
            if (obtainedFrom != null) {
                // We are the first thread to see the obtained value. Let's tell the interested parties about it.
                valueBroadcaster.getSource().valueObtained(obtainedValue(obtained), obtainedFrom);
                DisplayName displayName = null;
                if (obtainedFrom instanceof Describable) {
                    displayName = Describables.of(((Describable) obtainedFrom).getDisplayName());
                }
                return new ObtainedValueHolder<>(obtained, displayName);
            }
            return new ObtainedValueHolder<>(obtained);
        }

        @NonNull
        private ValueSource<T, P> source() {
            return instantiateValueSource(
                sourceType,
                parametersType,
                isolateParameters(parameters)
            );
        }

        @NonNull
        private DefaultObtainedValue<T, P> obtainedValue(Try<@Nullable T> obtained) {
            return new DefaultObtainedValue<>(
                obtained,
                sourceType,
                parametersType,
                parameters
            );
        }
    }

    private static class ObtainedValueHolder<T> {
        private final Try<T> obtained;
        @Nullable
        private final DisplayName displayName;

        private ObtainedValueHolder(Try<T> obtained, @Nullable DisplayName displayName) {
            this.obtained = obtained;
            this.displayName = displayName;
        }

        public ObtainedValueHolder(Try<T> obtained) {
            this(obtained, null);
        }

        public Value<T> asNullableValue() {
            return Value.ofNullable(obtained.get()).pushWhenMissing(displayName);
        }

        public ExecutionTimeValue<T> asExecutionTimeValue() {
            return ExecutionTimeValue.ofNullable(obtained.get());
        }
    }

    private static class DefaultObtainedValue<T, P extends ValueSourceParameters> implements ValueListener.ObtainedValue<T, P> {

        private final Try<@Nullable T> value;
        private final Class<? extends ValueSource<T, P>> valueSourceType;
        private final Class<P> parametersType;
        @Nullable
        private final P parameters;

        public DefaultObtainedValue(
            Try<@Nullable T> value,
            Class<? extends ValueSource<T, P>> valueSourceType,
            @Nullable Class<P> parametersType,
            @Nullable P parameters
        ) {
            this.value = value;
            this.valueSourceType = valueSourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
        }

        @Override
        public Try<@Nullable T> getValue() {
            return value;
        }

        @Override
        public Class<? extends ValueSource<T, P>> getValueSourceType() {
            return valueSourceType;
        }

        @Override
        @Nullable
        public Class<P> getValueSourceParametersType() {
            return parametersType;
        }

        @Override
        public P getValueSourceParameters() {
            return parameters;
        }
    }
}
