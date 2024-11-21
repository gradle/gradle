/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;

/**
 * A partial {@link Provider} implementation. Subclasses must implement {@link ProviderInternal#getType()} and {@link AbstractMinimalProvider#calculateOwnValue(ValueConsumer)}.
 */
public abstract class AbstractMinimalProvider<T> implements ProviderInternal<T>, Managed {
    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this provider");

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends @org.jetbrains.annotations.Nullable S, ? super T> transformer) {
        // Could do a better job of inferring the type
        return new TransformBackedProvider<>(null, this, transformer);
    }

    @Override
    public ProviderInternal<T> filter(final Spec<? super T> spec) {
        return new FilteringProvider<>(this, spec);
    }

    @Override
    public <S> Provider<S> flatMap(final Transformer<? extends @org.jetbrains.annotations.Nullable Provider<? extends S>, ? super T> transformer) {
        return new FlatMapProvider<>(this, transformer);
    }

    /**
     * Returns the human consumable display name for this provider, or null if this is not known.
     */
    @Nullable
    protected DisplayName getDeclaredDisplayName() {
        return null;
    }

    /**
     * Returns a display name for this provider, using a default if this is not known.
     */
    protected DisplayName getDisplayName() {
        DisplayName displayName = getDeclaredDisplayName();
        if (displayName == null) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName;
    }

    protected DisplayName getTypedDisplayName() {
        return DEFAULT_DISPLAY_NAME;
    }

    protected abstract ValueSupplier.Value<? extends T> calculateOwnValue(ValueConsumer consumer);

    protected Value<? extends T> calculateOwnPresentValue() {
        Value<? extends T> value = calculateOwnValue(ValueConsumer.IgnoreUnsafeRead);
        if (value.isMissing()) {
            throw new MissingValueException(cannotQueryValueOf(value));
        }

        return value;
    }

    @Override
    public boolean isPresent() {
        return calculatePresence(ValueConsumer.IgnoreUnsafeRead);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return !calculateOwnValue(consumer).isMissing();
    }

    @Override
    public T get() {
        return calculateOwnPresentValue().get();
    }

    @Override
    public T getOrNull() {
        return calculateOwnValue(ValueConsumer.IgnoreUnsafeRead).orNull();
    }

    @Override
    public T getOrElse(T defaultValue) {
        return calculateOwnValue(ValueConsumer.IgnoreUnsafeRead).orElse(Cast.uncheckedNonnullCast(defaultValue));
    }

    @Override
    public Value<? extends T> calculateValue(ValueConsumer consumer) {
        return calculateOwnValue(consumer).pushWhenMissing(getDeclaredDisplayName());
    }

    @Override
    public Provider<T> orElse(T value) {
        return new OrElseFixedValueProvider<>(this, value);
    }

    @Override
    public Provider<T> orElse(Provider<? extends T> provider) {
        return new OrElseProvider<>(this, Providers.internal(provider));
    }

    @Deprecated
    @Override
    public final Provider<T> forUseAtConfigurationTime() {
        DeprecationLogger.deprecateMethod(Provider.class, "forUseAtConfigurationTime")
            .withAdvice("Simply remove the call.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "for_use_at_configuration_time_deprecation")
            .nagUser();
        return this;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // When used as an input, add the producing tasks if known
        getProducer().visitDependencies(context);
    }

    @Override
    public ValueProducer getProducer() {
        return ValueProducer.unknown();
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return ExecutionTimeValue.value(calculateOwnValue(ValueConsumer.IgnoreUnsafeRead));
    }

    @Override
    public ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
        Class<T> selfType = getType();
        if (selfType != null && !targetType.isAssignableFrom(selfType)) {
            throw new IllegalArgumentException(formatInvalidTypeException(owner.getDisplayName(), targetType, selfType));
        } else if (selfType == null) {
            return new MappingProvider<>(Cast.uncheckedCast(targetType), this, new TypeSanitizingTransformer<>(owner, sanitizer, targetType));
        } else {
            return this;
        }
    }

    private static String formatInvalidTypeException(String owner, Class<?> targetType, Class<?> selfType) {
        String targetTypeName = targetType.getName();
        String selfTypeName = selfType.getName();
        if (targetTypeName.equals(selfTypeName)) {
            // This may happen when the same type is loaded by different classloaders.
            targetTypeName = targetTypeName + " loaded with " + targetType.getClassLoader();
            selfTypeName = selfTypeName + " loaded with " + selfType.getClassLoader();
        }
        return String.format("Cannot set the value of %s of type %s using a provider of type %s.", owner, targetTypeName, selfTypeName);
    }

    @Override
    public ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
        return Providers.nullableValue(calculateValue(consumer));
    }

    @Override
    public final String toString() {
        // Override #toStringNoReentrance instead
        return EvaluationContext.current().tryEvaluate(this, "<CIRCULAR REFERENCE>", this::toStringNoReentrance);
    }

    @Override
    public boolean isImmutable() {
        return false;
    }

    @Override
    public Class<?> publicType() {
        return Provider.class;
    }

    @Override
    public Object unpackState() {
        return getOrNull();
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.ProviderManagedFactory.FACTORY_ID;
    }

    private String cannotQueryValueOf(Value<? extends T> value) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Cannot query the value of ").append(getDisplayName().getDisplayName()).append(" because it has no value available.");
        if (!value.getPathToOrigin().isEmpty()) {
            formatter.node("The value of ").append(getTypedDisplayName().getDisplayName()).append(" is derived from");
            formatter.startChildren();
            for (DisplayName displayName : value.getPathToOrigin()) {
                formatter.node(displayName.getDisplayName());
            }
            formatter.endChildren();
        }
        return formatter.toString();
    }

    /**
     * Marks this provider as being evaluated until the returned scope is closed.
     *
     * @return the scope
     */
    protected EvaluationScopeContext openScope() {
        return EvaluationContext.current().open(this);
    }

    /**
     * An implementation for the toString method that is never called if the current provider is being evaluated.
     *
     * @return the string representation of the provider
     */
    protected String toStringNoReentrance() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        Class<?> type = getType();
        return String.format("provider(%s)", type == null ? "?" : type.getName());
    }
}
