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
import org.gradle.api.internal.provider.Collectors.ElementFromProvider;
import org.gradle.api.internal.provider.Collectors.ElementsFromArray;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollection;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollectionProvider;
import org.gradle.api.internal.provider.Collectors.MinusCollector;
import org.gradle.api.internal.provider.Collectors.PlusCollector;
import org.gradle.api.internal.provider.Collectors.SingleElement;
import org.gradle.api.provider.CollectionPropertyConfigurer;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class AbstractCollectionProperty<T, C extends Collection<T>> extends AbstractProperty<C, CollectionSupplier<T, C>> implements CollectionPropertyInternal<T, C> {
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
        return new EmptySupplier(false);
    }

    private CollectionSupplier<T, C> noValueSupplier() {
        return Cast.uncheckedCast(new NoValueSupplier<>(Value.missing()));
    }

    /**
     * Creates an empty immutable collection.
     */
    protected abstract C emptyCollection();

    private CollectionPropertyConfigurer<T> getConventionValue() {
        assertCanMutate();
        return new ConventionConfigurer();
    }

    private CollectionPropertyConfigurer<T> getExplicitValue() {
        assertCanMutate();
        return new ExplicitValueConfigurer();
    }

    @Override
    public CollectionPropertyConfigurer<T> value() {
        if (isExplicit())
            return getExplicitValue();
        return getConventionValue();
    }

    @Override
    public void excludeAll(Predicate<T> filter) {
        setSupplier(getSupplier().keep(filter.negate()));
    }

    @Override
    public void excludeAll(Provider<? extends Iterable<? extends T>> provider) {
        setSupplier(getSupplier().minus(new ElementsFromCollectionProvider<>(Providers.internal(provider))));
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final void excludeAll(T... elements) {
        setSupplier(getSupplier().minus(new ElementsFromArray<>(elements)));
    }

    @Override
    public void excludeAll(Iterable<? extends T> elements) {
        setSupplier(getSupplier().minus(new ElementsFromCollection<>(elements)));
    }

    @Override
    public void exclude(Provider<T> provider) {
        setSupplier(getSupplier().minus(new ElementFromProvider<>(Providers.internal(provider))));
    }

    @Override
    public void exclude(T element) {
        setSupplier(getSupplier().minus(new SingleElement<>(element)));
    }

    @Override
    public void add(final T element) {
        Preconditions.checkNotNull(element, "Cannot add a null element to a property of type %s.", collectionType.getSimpleName());
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

    @Override
    public int size() {
        return calculateOwnPresentValue().getWithoutSideEffect().size();
    }

    private void addCollector(Collector<T> collector) {
        assertCanMutate();
        setSupplier(getExplicitValue(defaultValue).plus(collector));
    }

    private void addConventionCollector(Collector<T> collector) {
        assertCanMutate();
        setConvention(getConventionSupplier().plus(collector));
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
        } else if (value.hasFixedValue()) {
            setSupplier(new FixedSupplier<>(value.getFixedValue(), Cast.uncheckedCast(value.getSideEffect())));
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
            return new FixedSupplier<>(result.getWithoutSideEffect(), Cast.uncheckedCast(result.getSideEffect()));
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

    class NoValueSupplier<T, C extends Collection<? extends T>> implements CollectionSupplier<T, C> {
        private final Value<? extends C> value;

        public NoValueSupplier(Value<? extends C> value) {
            assert value.isMissing();
            this.value = value.asType();
        }

        @Override
        public CollectionSupplier<T, C> pruned() {
            return Cast.uncheckedCast(emptySupplier().pruned());
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
        public CollectionSupplier<T, C> minus(Collector<T> collector) {
            // No value - something = no value
            return this;
        }

        @Override
        public CollectionSupplier<T, C> keep(Predicate<T> filter) {
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
        private final boolean pruning;

        private EmptySupplier(boolean pruning) {
            this.pruning = pruning;
        }

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
            return new CollectingSupplier(collector, pruning);
        }

        @Override
        public CollectionSupplier<T, C> minus(Collector<T> collector) {
            // empty - something = empty
            return this;
        }

        @Override
        public CollectionSupplier<T, C> pruned() {
            return pruning ? this : new EmptySupplier(true);
        }

        @Override
        public CollectionSupplier<T, C> keep(Predicate<T> filter) {
            return this;
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
        private final SideEffect<? super C> sideEffect;

        public FixedSupplier(C value, @Nullable SideEffect<? super C> sideEffect) {
            this.value = value;
            this.sideEffect = sideEffect;
        }

        @Override
        public CollectionSupplier<T, C> pruned() {
            return null;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<? extends C> calculateValue(ValueConsumer consumer) {
            return Value.of(value).withSideEffect(sideEffect);
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CollectionSupplier<T, C> minus(Collector<T> collector) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CollectionSupplier<T, C> keep(Predicate<T> filter) {
            // fixed values do not allow further filtering
            throw new UnsupportedOperationException();
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(value).withSideEffect(sideEffect);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }
    }

    private class CollectingSupplier implements CollectionSupplier<T, C> {
        private final Collector<T> value;
        private final boolean pruning;

        public CollectingSupplier(Collector<T> value, boolean pruning) {
            this.value = value;
            this.pruning = pruning;
        }

        public CollectingSupplier(Collector<T> value) {
            this(value, false);
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
            return Value.of(Cast.<C>uncheckedNonnullCast(builder.build())).withSideEffect(SideEffect.fixedFrom(result));
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> collector) {
            return new CollectingSupplier(new PlusCollector<>(value, collector, pruning), pruning);
        }

        @Override
        public CollectionSupplier<T, C> minus(Collector<T> collector) {
            return new CollectingSupplier(new MinusCollector<>(value, collector, pruning, collectionFactory), pruning);
        }

        @Override
        public CollectionSupplier<T, C> keep(Predicate<T> filter) {
            return minus(new Collectors.FilteringCollector<>(value, filter.negate(), collectionFactory));
        }

        @Override
        public CollectionSupplier<T, C> pruned() {
            return pruning ? this : new CollectingSupplier(value, true);
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
                return getFixedExecutionTimeValue(values, changingContent);
            }

            // At least one of the values is a changing value
            List<ProviderInternal<? extends Iterable<? extends T>>> providers = new ArrayList<>(values.size());
            for (ExecutionTimeValue<? extends Iterable<? extends T>> value : values) {
                providers.add(value.toProvider());
            }
            // TODO - CollectionSupplier could be replaced with ProviderInternal, so this type and the collection provider can be merged
            return ExecutionTimeValue.changingValue(new CollectingProvider<>(AbstractCollectionProperty.this.getType(), providers, collectionFactory));
        }

        private ExecutionTimeValue<C> getFixedExecutionTimeValue(List<ExecutionTimeValue<? extends Iterable<? extends T>>> values, boolean changingContent) {
            ImmutableCollection.Builder<T> builder = collectionFactory.get();
            SideEffectBuilder<C> sideEffectBuilder = SideEffect.builder();
            for (ExecutionTimeValue<? extends Iterable<? extends T>> value : values) {
                builder.addAll(value.getFixedValue());
                sideEffectBuilder.add(SideEffect.fixedFrom(value));
            }

            ExecutionTimeValue<C> mergedValue = ExecutionTimeValue.fixedValue(Cast.uncheckedNonnullCast(builder.build()));
            if (changingContent) {
                mergedValue = mergedValue.withChangingContent();
            }

            return mergedValue.withSideEffect(sideEffectBuilder.build());
        }

        @Override
        public ValueProducer getProducer() {
            return value.getProducer();
        }
    }

    /**
     * A provider for a collection type whose elements are themselves providers.
     */
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
            SideEffectBuilder<? super C> sideEffectBuilder = SideEffect.builder();
            for (ProviderInternal<? extends Iterable<? extends T>> provider : providers) {
                Value<? extends Iterable<? extends T>> value = provider.calculateValue(consumer);
                if (value.isMissing()) {
                    return Value.missing();
                }
                builder.addAll(value.getWithoutSideEffect());
                sideEffectBuilder.add(SideEffect.fixedFrom(value));
            }

            Value<? extends C> resultValue = Value.of(Cast.uncheckedNonnullCast(builder.build()));
            return resultValue.withSideEffect(sideEffectBuilder.build());
        }
    }


    //TODO-RC consider combining these two implementations if their only difference is the target CollectingSupplier
    private class ConventionConfigurer implements CollectionPropertyConfigurer<T> {

        private boolean pruned;

        private void prune() {
            if (!pruned) {
                pruned = true;
                setConvention(getConventionSupplier().pruned());
            }
        }

        @Override
        public void add(T element) {
            prune();
            addConventionCollector(new SingleElement<>(element));
        }

        @Override
        public void add(Provider<? extends T> provider) {
            prune();
            addConventionCollector(new ElementFromProvider<>(Providers.internal(provider)));
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final void addAll(T... elements) {
            prune();
            addConventionCollector(new ElementsFromArray<>(elements));
        }

        @Override
        public void addAll(Iterable<? extends T> elements) {
            prune();
            addConventionCollector(new ElementsFromCollection<>(elements));
        }

        @Override
        public void addAll(Provider<? extends Iterable<? extends T>> provider) {
            prune();
            addConventionCollector(new ElementsFromCollectionProvider<>(Providers.internal(provider)));
        }

        @Override
        public void excludeAll(Predicate<T> filter) {
            prune();
            setConvention(getConventionSupplier().keep(filter.negate()));
        }

        @Override
        public void excludeAll(Provider<? extends Iterable<? extends T>> provider) {
            prune();
            setConvention(getConventionSupplier().minus(new ElementsFromCollectionProvider<>(Providers.internal(provider))));
        }

        @Override
        public void excludeAll(Iterable<? extends T> elements) {
            prune();
            setConvention(getConventionSupplier().minus(new ElementsFromCollection<>(elements)));
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final void excludeAll(T... elements) {
            prune();
            setConvention(getConventionSupplier().minus(new ElementsFromArray<>(elements)));
        }

        @Override
        public void exclude(Provider<T> provider) {
            prune();
            setConvention(getConventionSupplier().minus(new ElementFromProvider<>(Providers.internal(provider))));
        }

        @Override
        public void exclude(T element) {
            prune();
            setConvention(getConventionSupplier().minus(new SingleElement<>(element)));
        }
    }

    private class ExplicitValueConfigurer implements CollectionPropertyConfigurer<T> {

        private boolean pruned = false;

        @Override
        public void add(T element) {
            prune();
            AbstractCollectionProperty.this.add(element);
        }

        private void prune() {
            if (!pruned) {
                pruned = true;
                setSupplier(getSupplier().pruned());
            }
        }

        @Override
        public void add(Provider<? extends T> provider) {
            prune();
            AbstractCollectionProperty.this.add(provider);
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final void addAll(T... elements) {
            prune();
            AbstractCollectionProperty.this.addAll(elements);
        }

        @Override
        public void addAll(Iterable<? extends T> elements) {
            prune();
            AbstractCollectionProperty.this.addAll(elements);
        }

        @Override
        public void addAll(Provider<? extends Iterable<? extends T>> provider) {
            prune();
            AbstractCollectionProperty.this.addAll(provider);
        }

        @Override
        public void excludeAll(Predicate<T> filter) {
            prune();
            AbstractCollectionProperty.this.excludeAll(filter);
        }

        @Override
        public void exclude(Provider<T> provider) {
            prune();
            AbstractCollectionProperty.this.exclude(provider);
        }

        @Override
        public void exclude(T element) {
            prune();
            AbstractCollectionProperty.this.exclude(element);
        }

        @Override
        public void excludeAll(Provider<? extends Iterable<? extends T>> provider) {
            prune();
            AbstractCollectionProperty.this.excludeAll(provider);
        }

        @Override
        public void excludeAll(Iterable<? extends T> elements) {
            prune();
            AbstractCollectionProperty.this.excludeAll(elements);
        }

        @Override
        @SafeVarargs
        @SuppressWarnings("varargs")
        public final void excludeAll(T... elements) {
            prune();
            AbstractCollectionProperty.this.excludeAll(elements);
        }
    }

    /**
     * Returns the frozen view of this Property. Further updates to this Property do not affect the return provider.
     * However, the Provider itself is live - it reflects changes to its dependencies.
     *
     * @return the frozen view of this Property
     */
    protected Provider<C> freeze() {
        return Providers.of(get());
    }
}
