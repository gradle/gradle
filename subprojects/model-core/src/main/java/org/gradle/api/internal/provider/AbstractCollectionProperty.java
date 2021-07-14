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
import org.gradle.api.internal.provider.Collectors.ElementFromProvider;
import org.gradle.api.internal.provider.Collectors.ElementsFromArray;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollection;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollectionProvider;
import org.gradle.api.internal.provider.Collectors.SingleElement;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractCollectionProperty<T, C extends Collection<T>> extends AbstractProperty<C, CollectionSupplier<T, C>> implements CollectionPropertyInternal<T, C> {
    private static final CollectionSupplier<Object, Collection<Object>> NO_VALUE = new NoValueSupplier<>(Value.missing());
    private final Class<? extends Collection> collectionType;
    private final Class<T> elementType;
    private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;
    private final ValueCollector<T> valueCollector;
    private CollectionSupplier<T, C> defaultValue = emptySupplier();

    AbstractCollectionProperty(PropertyHost host, Class<? extends Collection> collectionType, Class<T> elementType, Supplier<ImmutableCollection.Builder<T>> collectionFactory) {
        super(host);
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.collectionFactory = collectionFactory;
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
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final void addAll(T... elements) {
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
        return Cast.uncheckedCast(collectionType);
    }

    @Override
    public Class<T> getElementType() {
        return elementType;
    }

    /**
     * Sets the value of this property the given value.
     */
    public void fromState(ExecutionTimeValue<? extends C> value) {
        if (value.isMissing()) {
            setSupplier(noValueSupplier());
        } else if (value.isFixedValue()) {
            setSupplier(new FixedSupplier<>(value.getFixedValue()));
        } else {
            setSupplier(new CollectingSupplier(new ElementsFromCollectionProvider<>(value.getChangingValue())));
        }
    }

    @Override
    public void setFromAnyValue(Object object) {
        if (object instanceof Provider) {
            set(Cast.<Provider<C>>uncheckedCast(object));
        } else {
            if (object != null && !(object instanceof Iterable)) {
                throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using an instance of type %s.", collectionType.getName(), object.getClass().getName()));
            }
            set(Cast.<Iterable<? extends T>>uncheckedCast(object));
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
            CollectionPropertyInternal<T, C> collectionProp = Cast.uncheckedCast(p);
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
    protected Value<? extends C> calculateValueFrom(CollectionSupplier<T, C> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected CollectionSupplier<T, C> finalValue(CollectionSupplier<T, C> value, ValueConsumer consumer) {
        Value<? extends C> result = value.calculateValue(consumer);
        if (!result.isMissing()) {
            return new FixedSupplier<>(result.get());
        } else if (result.getPathToOrigin().isEmpty()) {
            return noValueSupplier();
        } else {
            return new NoValueSupplier<>(result);
        }
    }

    @Override
    protected ExecutionTimeValue<? extends C> calculateOwnExecutionTimeValue(CollectionSupplier<T, C> value) {
        return value.calculateExecutionTimeValue();
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
        public boolean calculatePresence(ValueConsumer consumer) {
            return false;
        }

        @Override
        public Value<? extends C> calculateValue(ValueConsumer consumer) {
            return value;
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            // No value + something = no value
            return this;
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.missing();
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }
    }

    private class EmptySupplier implements CollectionSupplier<T, C> {
        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<? extends C> calculateValue(ValueConsumer consumer) {
            return Value.of(emptyCollection());
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            // empty + something = something
            return new CollectingSupplier(collector);
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(emptyCollection());
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.noProducer();
        }
    }

    private static class FixedSupplier<T, C extends Collection<? extends T>> implements CollectionSupplier<T, C> {
        private final C value;

        public FixedSupplier(C value) {
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<? extends C> calculateValue(ValueConsumer consumer) {
            return Value.of(value);
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(value);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }
    }

    private class CollectingSupplier implements CollectionSupplier<T, C> {
        private final Collector<T> value;

        public CollectingSupplier(Collector<T> value) {
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return value.calculatePresence(consumer);
        }

        @Override
        public Value<C> calculateValue(ValueConsumer consumer) {
            // TODO - don't make a copy when the collector already produces an immutable collection
            ImmutableCollection.Builder<T> builder = collectionFactory.get();
            Value<Void> result = value.collectEntries(consumer, valueCollector, builder);
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
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            List<ExecutionTimeValue<? extends Iterable<? extends T>>> values = new ArrayList<>();
            value.calculateExecutionTimeValue(values::add);
            boolean fixed = true;
            boolean changingContent = false;
            for (ExecutionTimeValue<? extends Iterable<? extends T>> value : values) {
                if (value.isMissing()) {
                    return ExecutionTimeValue.missing();
                }
                if (value.isChangingValue()) {
                    fixed = false;
                }
                changingContent |= value.hasChangingContent();
            }

            if (fixed) {
                ImmutableCollection.Builder<T> builder = collectionFactory.get();
                for (ExecutionTimeValue<? extends Iterable<? extends T>> value : values) {
                    builder.addAll(value.getFixedValue());
                }
                ExecutionTimeValue<C> mergedValue = ExecutionTimeValue.fixedValue(Cast.uncheckedNonnullCast(builder.build()));
                if (changingContent) {
                    return mergedValue.withChangingContent();
                } else {
                    return mergedValue;
                }
            }

            // At least one of the values is a changing value
            List<ProviderInternal<? extends Iterable<? extends T>>> providers = new ArrayList<>(values.size());
            for (ExecutionTimeValue<? extends Iterable<? extends T>> value : values) {
                providers.add(value.toProvider());
            }
            // TODO - CollectionSupplier could be replaced with ProviderInternal, so this type and the collection provider can be merged
            return ExecutionTimeValue.changingValue(new CollectingProvider<>(AbstractCollectionProperty.this.getType(), providers, collectionFactory));
        }

        @Override
        public ValueProducer getProducer() {
            return value.getProducer();
        }
    }

    private static class CollectingProvider<T, C extends Collection<? extends T>> extends AbstractMinimalProvider<C> {
        private final Class<C> type;
        private final List<ProviderInternal<? extends Iterable<? extends T>>> providers;
        private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;

        public CollectingProvider(Class<C> type, List<ProviderInternal<? extends Iterable<? extends T>>> providers, Supplier<ImmutableCollection.Builder<T>> collectionFactory) {
            this.type = type;
            this.providers = providers;
            this.collectionFactory = collectionFactory;
        }

        @Nullable
        @Override
        public Class<C> getType() {
            return type;
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.changingValue(this);
        }

        @Override
        protected Value<? extends C> calculateOwnValue(ValueConsumer consumer) {
            ImmutableCollection.Builder<T> builder = collectionFactory.get();
            for (ProviderInternal<? extends Iterable<? extends T>> provider : providers) {
                Value<? extends Iterable<? extends T>> value = provider.calculateValue(consumer);
                if (value.isMissing()) {
                    return Value.missing();
                }
                builder.addAll(value.get());
            }
            return Value.of(Cast.uncheckedNonnullCast(builder.build()));
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
        public boolean calculatePresence(ValueConsumer consumer) {
            return left.calculatePresence(consumer) && right.calculatePresence(consumer);
        }

        @Override
        public int size() {
            return left.size() + right.size();
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            Value<Void> value = left.collectEntries(consumer, collector, dest);
            if (value.isMissing()) {
                return value;
            }
            return right.collectEntries(consumer, collector, dest);
        }

        @Override
        public void calculateExecutionTimeValue(Action<? super ExecutionTimeValue<? extends Iterable<? extends T>>> visitor) {
            left.calculateExecutionTimeValue(visitor);
            right.calculateExecutionTimeValue(visitor);
        }

        @Override
        public ValueProducer getProducer() {
            return left.getProducer().plus(right.getProducer());
        }
    }
}
