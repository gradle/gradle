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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonExtensible;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.InputSource;
import org.gradle.api.provider.InputSourceParameters;
import org.gradle.api.provider.InputSourceSpec;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.Try;
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DefaultInputSourceProviderFactory implements InputSourceProviderFactory {

    private final InstantiatorFactory instantiatorFactory;
    private final IsolatableFactory isolatableFactory;
    private final GradleProperties gradleProperties;
    private final CalculatedValueFactory calculatedValueFactory;
    private final ExecOperations execOperations;

    private final IsolationScheme<InputSource, InputSourceParameters> isolationScheme = new IsolationScheme<>(InputSource.class, InputSourceParameters.class, InputSourceParameters.None.class);
    private final InstanceGenerator paramsInstantiator;
    private final InstanceGenerator specInstantiator;

    public DefaultInputSourceProviderFactory(
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        GradleProperties gradleProperties,
        CalculatedValueFactory calculatedValueFactory,
        ExecOperations execOperations,
        ServiceLookup services
    ) {
        this.instantiatorFactory = instantiatorFactory;
        this.isolatableFactory = isolatableFactory;
        this.gradleProperties = gradleProperties;
        this.calculatedValueFactory = calculatedValueFactory;
        this.execOperations = execOperations;
        // TODO - dedupe logic copied from DefaultBuildServicesRegistry
        this.paramsInstantiator = instantiatorFactory.decorateScheme().withServices(services).instantiator();
        this.specInstantiator = instantiatorFactory.decorateLenientScheme().withServices(services).instantiator();
    }

    @Override
    public <T, P extends InputSourceParameters> Provider<T> createProviderOf(
        Class<? extends InputSource<T, P>> inputSourceType,
        Action<? super InputSourceSpec<P>> configureAction
    ) {
        try {
            Class<P> parametersType = extractParametersTypeOf(inputSourceType);
            P parameters = parametersType != null
                ? paramsInstantiator.newInstance(parametersType)
                : null;

            // TODO - consider deferring configuration
            configureParameters(parameters, configureAction);

            return instantiateInputSourceProvider(inputSourceType, parametersType, parameters);
        } catch (GradleException e) {
            throw e;
        } catch (Exception e) {
            throw new GradleException(couldNotCreateProviderOf(inputSourceType), e);
        }
    }

    @Nonnull
    public <T, P extends InputSourceParameters> Provider<T> instantiateInputSourceProvider(
        Class<? extends InputSource<T, P>> inputSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P parameters
    ) {
        return new InputSourceProvider<>(
            new LazilyObtainedValue<>(inputSourceType, parametersType, parameters)
        );
    }

    @Nonnull
    public <T, P extends InputSourceParameters> InputSource<T, P> instantiateInputSource(
        Class<? extends InputSource<T, P>> valueSourceType,
        @Nullable Class<P> parametersType,
        @Nullable P isolatedParameters
    ) {
        ServiceRegistry services = ServiceRegistryBuilder.builder()
            .displayName("value source services")
            .provider(registration -> {
                registration.add(GradleProperties.class, gradleProperties);
                registration.add(ExecOperations.class, execOperations);
                if (parametersType != null && isolatedParameters != null) {
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
    private <T, P extends InputSourceParameters> Class<P> extractParametersTypeOf(Class<? extends InputSource<T, P>> valueSourceType) {
        return isolationScheme.parameterTypeFor(valueSourceType, 1);
    }

    private <P extends InputSourceParameters> void configureParameters(@Nullable P parameters, Action<? super InputSourceSpec<P>> configureAction) {
        DefaultInputSourceSpec<P> valueSourceSpec = Cast.uncheckedNonnullCast(specInstantiator.newInstance(
            DefaultInputSourceSpec.class,
            parameters
        ));
        configureAction.execute(valueSourceSpec);
    }

    @Nullable
    private <P extends InputSourceParameters> P isolateParameters(@Nullable P parameters) {
        // TODO - consider if should hold the project lock to do the isolation
        return isolatableFactory.isolate(parameters).isolate();
    }

    private static String couldNotCreateProviderOf(Class<?> valueSourceType) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Could not create provider for value source ");
        formatter.appendType(valueSourceType);
        formatter.append(".");
        return formatter.toString();
    }

    @NonExtensible
    public abstract static class DefaultInputSourceSpec<P extends InputSourceParameters>
        implements InputSourceSpec<P> {

        private final P parameters;

        public DefaultInputSourceSpec(P parameters) {
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

    public static class InputSourceProvider<T, P extends InputSourceParameters> extends AbstractMinimalProvider<T> {
        private final LazilyObtainedValue<T, P> value;

        private InputSourceProvider(LazilyObtainedValue<T, P> value) {
            this.value = value;
        }

        public Class<? extends InputSource<T, P>> getInputSourceType() {
            return value.sourceType;
        }

        @Override
        protected String toStringNoReentrance() {
            return String.format("valueof(%s)", getInputSourceType().getSimpleName());
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
            // TODO: Visit the dependencies
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Override
        public boolean isInputSource() {
            return true;
        }

        @Nullable
        @Override
        public Object getInputSource() {
            return getParameters();
        }

        @Nullable
        public P getInputValue() {
            return getParameters();
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

    private class LazilyObtainedValue<T, P extends InputSourceParameters> {

        public final Class<? extends InputSource<T, P>> sourceType;

        @Nullable
        public final Class<P> parametersType;

        @Nullable
        public final P parameters;

        private final CalculatedValue<@org.jetbrains.annotations.Nullable T> value;

        private LazilyObtainedValue(
            Class<? extends InputSource<T, P>> sourceType,
            @Nullable Class<P> parametersType,
            @Nullable P parameters
        ) {
            this.sourceType = sourceType;
            this.parametersType = parametersType;
            this.parameters = parameters;
            this.value = calculatedValueFactory.create(Describables.of("InputSource of type", sourceType), () -> {
                InputSource<T, P> source = source();
                return source.obtain();
            });
        }

        public boolean hasBeenObtained() {
            return value.isFinalized();
        }

        public Try<@org.jetbrains.annotations.Nullable T> obtain() {
            value.finalizeIfNotAlready();
            return value.getValue();
        }

        @Nonnull
        private InputSource<T, P> source() {
            return instantiateInputSource(
                sourceType,
                parametersType,
                isolateParameters(parameters)
            );
        }
    }
}
