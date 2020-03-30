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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.Managed;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A partial {@link Provider} implementation. Subclasses need to implement {@link ProviderInternal#getType()} and {@link AbstractMinimalProvider#calculateOwnValue()}.
 */
public abstract class AbstractMinimalProvider<T> implements ProviderInternal<T>, Managed {
    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this provider");

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
        return new TransformBackedProvider<>(transformer, this);
    }

    @Override
    public <S> Provider<S> flatMap(final Transformer<? extends Provider<? extends S>, ? super T> transformer) {
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

    protected abstract ValueSupplier.Value<? extends T> calculateOwnValue();

    @Override
    public boolean isPresent() {
        return !calculateOwnValue().isMissing();
    }

    @Override
    public T get() {
        Value<? extends T> value = calculateOwnValue();
        if (value.isMissing()) {
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
            throw new MissingValueException(formatter.toString());
        }
        return value.get();
    }

    @Override
    public T getOrNull() {
        return calculateOwnValue().orNull();
    }

    @Override
    public T getOrElse(T defaultValue) {
        return calculateOwnValue().orElse(defaultValue);
    }

    @Override
    public Value<? extends T> calculateValue() {
        return calculateOwnValue().pushWhenMissing(getDeclaredDisplayName());
    }

    @Override
    public Provider<T> orElse(T value) {
        return new OrElseFixedValueProvider<>(this, value);
    }

    @Override
    public Provider<T> orElse(Provider<? extends T> provider) {
        return new OrElseProvider<>(this, Providers.internal(provider));
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // When used as an input, add the producing tasks if known
        maybeVisitBuildDependencies(context);
    }

    @Override
    public boolean isValueProducedByTask() {
        return false;
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
    }

    protected List<Task> getProducerTasks() {
        List<Task> producers = new ArrayList<>();
        visitProducerTasks(producers::add);
        return producers;
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        return false;
    }

    @Override
    public ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
        if (getType() != null && !targetType.isAssignableFrom(getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the value of %s of type %s using a provider of type %s.", owner.getDisplayName(), targetType.getName(), getType().getName()));
        } else if (getType() == null) {
            return new TypeSanitizingProvider<>(owner, sanitizer, targetType, this);
        } else {
            return this;
        }
    }

    @Override
    public ProviderInternal<T> withFinalValue() {
        return Providers.nullableValue(calculateValue());
    }

    @Override
    public String toString() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        return String.format("provider(%s)", GUtil.elvis(getType(), "?"));
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

}
