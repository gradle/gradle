/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.Collectors.ElementFromProvider;
import org.gradle.api.internal.provider.Collectors.ElementsFromArray;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollection;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollectionProvider;
import org.gradle.api.internal.provider.Collectors.SingleElement;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class AbstractCollectionProperty<T, C extends Collection<T>> extends AbstractProperty<C, CollectionSupplier<T, C>> implements CollectionPropertyInternal<T, C> {
    private static final CollectionSupplier<Object, Collection<Object>> NO_VALUE = new NoValueSupplier<>(Value.missing());
    private final Class<? extends Collection> collectionType;
    private final Class<T> elementType;
    private final ValueCollector<T> valueCollector;
    private CollectionSupplier<T, C> defaultValue = emptySupplier();

    AbstractCollectionProperty(PropertyHost host, Class<? extends Collection> collectionType, Class<T> elementType) {
        super(host);
        this.collectionType = collectionType;
        this.elementType = elementType;
        valueCollector = new ValidatingValueCollector<>(collectionType, elementType, ValueSanitizers.forType(elementType));
        init(defaultValue, noValueSupplier());
    }

    private CollectionSupplier<T, C> emptySupplier() {
        return new EmptySupplier();
    }

    private CollectionSupplier<T, C> noValueSupplier() {
        return Cast.uncheckedCast(NO_VALUE);
    }

    /**
     * Creates an immutable collection from the given current values of this property.
     */
    protected abstract ImmutableCollection.Builder<T> builder();

    /**
     * Creates an empty immutable collection.
     */
    protected abstract C emptyCollection();

    @Override
    public void add(final T element) {
        Preconditions.checkNotNull(element, String.format("Cannot add a null element to a property of type %s.", collectionType.getSimpleName()));
        addCollector(new SingleElement<>(element));
    }

    @Override
    public void add(final Provider<? extends T> providerOfElement) {
        addCollector(new ElementFromProvider<>(Providers.internal(providerOfElement)));
    }

    @Override
    public void addAll(T... elements) {
        addCollector(new ElementsFromArray<>(elements));
    }

    @Override
    public void addAll(Iterable<? extends T> elements) {
        addCollector(new ElementsFromCollection<>(elements));
    }

    @Override
    public void addAll(Provider<? extends Iterable<? extends T>> provider) {
        addCollector(new ElementsFromCollectionProvider<>(Providers.internal(provider)));
    }

    private void addCollector(Collector<T> collector) {
        assertCanMutate();
        setSupplier(getExplicitValue(defaultValue).plus(collector));
    }

    @Nullable
    @Override
    public Class<C> getType() {
        return (Class<C>) collectionType;
    }

    @Override
    public Class<T> getElementType() {
        return elementType;
    }

    /**
     * Unpacks this property into a list of element providers.
     */
    public List<ProviderInternal<? extends Iterable<? extends T>>> getProviders() {
        List<ProviderInternal<? extends Iterable<? extends T>>> sources = new ArrayList<>();
        getSupplier().visit(sources);
        return sources;
    }

    /**
     * Sets the value of this property the given list of element providers.
     */
    public void providers(List<ProviderInternal<? extends Iterable<? extends T>>> providers) {
        CollectionSupplier<T, C> value = defaultValue;
        for (ProviderInternal<? extends Iterable<? extends T>> provider : providers) {
            value = value.plus(new ElementsFromCollectionProvider<>(provider));
        }
        setSupplier(value);
    }

    @Override
    public void setFromAnyValue(Object object) {
        if (object instanceof Provider) {
            set((Provider<C>) object);
        } else {
            if (object != null && !(object instanceof Iterable)) {
                throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using an instance of type %s.", collectionType.getName(), object.getClass().getName()));
            }
            set((Iterable<? extends T>) object);
        }
    }

    @Override
    public void set(@Nullable final Iterable<? extends T> elements) {
        if (elements == null) {
            discardValue();
            defaultValue = noValueSupplier();
        } else {
            setSupplier(new CollectingSupplier(new ElementsFromCollection<>(elements)));
        }
    }

    @Override
    public void set(final Provider<? extends Iterable<? extends T>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        ProviderInternal<? extends Iterable<? extends T>> p = Providers.internal(provider);
        if (p.getType() != null && !Iterable.class.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using a provider of type %s.", collectionType.getName(), p.getType().getName()));
        }
        if (p instanceof CollectionPropertyInternal) {
            CollectionPropertyInternal<T, C> collectionProp = (CollectionPropertyInternal<T, C>) p;
            if (!elementType.isAssignableFrom(collectionProp.getElementType())) {
                throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s with element type %s using a provider with element type %s.", collectionType.getName(), elementType.getName(), collectionProp.getElementType().getName()));
            }
        }
        setSupplier(new CollectingSupplier(new ElementsFromCollectionProvider<>(p)));
    }

    @Override
    public HasMultipleValues<T> value(@Nullable Iterable<? extends T> elements) {
        set(elements);
        return this;
    }

    @Override
    public HasMultipleValues<T> value(Provider<? extends Iterable<? extends T>> provider) {
        set(provider);
        return this;
    }

    @Override
    public HasMultipleValues<T> empty() {
        setSupplier(emptySupplier());
        return this;
    }

    @Override
    protected Value<? extends C> calculateOwnValue(CollectionSupplier<T, C> value) {
        return value.calculateValue();
    }

    @Override
    protected CollectionSupplier<T, C> finalValue(CollectionSupplier<T, C> value) {
        Value<? extends C> result = calculateOwnValue(value);
        if (!result.isMissing()) {
            return new FixedSupplier<>(result.get());
        } else if (result.getPathToOrigin().isEmpty()) {
            return noValueSupplier();
        } else {
            return new NoValueSupplier<>(result);
        }
    }

    @Override
    public HasMultipleValues<T> convention(@Nullable Iterable<? extends T> elements) {
        if (elements == null) {
            setConvention(noValueSupplier());
        } else {
            setConvention(new CollectingSupplier(new ElementsFromCollection<>(elements)));
        }
        return this;
    }

    @Override
    public HasMultipleValues<T> convention(Provider<? extends Iterable<? extends T>> provider) {
        setConvention(new CollectingSupplier(new ElementsFromCollectionProvider<>(Providers.internal(provider))));
        return this;
    }

    @Override
    protected String describeContents() {
        return String.format("%s(%s, %s)", collectionType.getSimpleName().toLowerCase(), elementType, getSupplier().toString());
    }

    static class NoValueSupplier<T, C extends Collection<? extends T>> implements CollectionSupplier<T, C> {
        private final Value<? extends C> value;

        public NoValueSupplier(Value<? extends C> value) {
            assert value.isMissing();
            this.value = value.asType();
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public Value<? extends C> calculateValue() {
            return value;
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            // No value + something = no value
            return this;
        }

        @Override
        public void visit(List<ProviderInternal<? extends Iterable<? extends T>>> sources) {
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return true;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }

        @Override
        public boolean isValueProducedByTask() {
            return false;
        }
    }

    private class EmptySupplier implements CollectionSupplier<T, C> {
        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public Value<? extends C> calculateValue() {
            return Value.of(emptyCollection());
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            // empty + something = something
            return new CollectingSupplier(collector);
        }

        @Override
        public void visit(List<ProviderInternal<? extends Iterable<? extends T>>> sources) {
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return true;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }

        @Override
        public boolean isValueProducedByTask() {
            return false;
        }
    }

    private static class FixedSupplier<T, C extends Collection<? extends T>> implements CollectionSupplier<T, C> {
        private final C value;

        public FixedSupplier(C value) {
            this.value = value;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public Value<? extends C> calculateValue() {
            return Value.of(value);
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visit(List<ProviderInternal<? extends Iterable<? extends T>>> sources) {
            sources.add(Providers.of(value));
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return true;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
        }

        @Override
        public boolean isValueProducedByTask() {
            return false;
        }
    }

    private class CollectingSupplier implements CollectionSupplier<T, C> {
        private final Collector<T> value;

        public CollectingSupplier(Collector<T> value) {
            this.value = value;
        }

        @Override
        public boolean isPresent() {
            return value.isPresent();
        }

        @Override
        public Value<C> calculateValue() {
            // TODO - don't make a copy when the collector already produces an immutable collection
            ImmutableCollection.Builder<T> builder = builder();
            Value<Void> result = value.collectEntries(valueCollector, builder);
            if (result.isMissing()) {
                return result.asType();
            }
            return Value.of(Cast.uncheckedCast(builder.build()));
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            return new CollectingSupplier(new PlusCollector<>(value, collector));
        }

        @Override
        public void visit(List<ProviderInternal<? extends Iterable<? extends T>>> sources) {
            value.visit(sources);
        }

        @Override
        public boolean isValueProducedByTask() {
            return value.isValueProducedByTask();
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            return value.maybeVisitBuildDependencies(context);
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            value.visitProducerTasks(visitor);
        }
    }

    private static class PlusCollector<T> implements Collector<T> {
        private final Collector<T> left;
        private final Collector<T> right;

        public PlusCollector(Collector<T> left, Collector<T> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public boolean isPresent() {
            return left.isPresent() && right.isPresent();
        }

        @Override
        public int size() {
            return left.size() + right.size();
        }

        @Override
        public Value<Void> collectEntries(ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            Value<Void> value = left.collectEntries(collector, dest);
            if (value.isMissing()) {
                return value;
            }
            return right.collectEntries(collector, dest);
        }

        @Override
        public void visit(List<ProviderInternal<? extends Iterable<? extends T>>> sources) {
            left.visit(sources);
            right.visit(sources);
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            if (left.maybeVisitBuildDependencies(context)) {
                return right.maybeVisitBuildDependencies(context);
            }
            return false;
        }

        @Override
        public void visitProducerTasks(Action<? super Task> visitor) {
            left.visitProducerTasks(visitor);
            right.visitProducerTasks(visitor);
        }

        @Override
        public boolean isValueProducedByTask() {
            return left.isValueProducedByTask() || right.isValueProducedByTask();
        }
    }
}
