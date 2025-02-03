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
import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.Collectors.ElementFromProvider;
import org.gradle.api.internal.provider.Collectors.ElementsFromArray;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollection;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollectionProvider;
import org.gradle.api.internal.provider.Collectors.SingleElement;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static org.gradle.api.internal.provider.AppendOnceList.toAppendOnceList;

/**
 * The base class for collection properties.
 * <p>
 * Value suppliers for collection properties are implementations of {@link CollectionSupplier}.
 * </p>
 * <p>
 * Elements stored in collection property values are implemented via various implementations of {@link Collector}.
 * </p>
 * <h2>Collection suppliers</h2>
 * The value of a collection property is represented at any time as an instance of an implementation of {@link CollectionSupplier}, namely:
 * <ul>
 *     <li>{@link EmptySupplier}, the initial value of a collection (or after {@link #empty()} is invoked)</li>
 *     <li>{@link NoValueSupplier}, when the collection value is unset (via {@link #set(Iterable)} or {@link #unset()}.</li>
 *     <li>{@link FixedSupplier}, when the collection is finalized - in that case, the fixed supplier will wrap the realized
 *     of the Java collection this collection property corresponds to</li>
 *     <li>{@link CollectingSupplier}, when the collection is still being added to - in that case,
 *     the collecting supplier will wrap a {@link Collector} that lazily represents the yet-to-be realized contents of the collection - see below for details</li>
 * </ul>
 *
 * <h2>Collectors</h2>
 * <p>
 *     While a collection property's contents are being built up, its value is represented by a {@link CollectingSupplier}.
 *     The collecting supplier will wrap a {@link Collector} instance that represents the various forms that elements can be added to a collection property (before the collection is finalized), namely:
 * </p>
 *     <ul>
 *         <li>{@link SingleElement} to represent a single element addition
 *         <li>{@link ElementFromProvider} to represent a single element added as a provider
 *         <li>{@link ElementsFromArray} to represent a single element added as an array</li>
 *         <li>{@link ElementsFromCollection} to represent a batch of elements added (or set wholesale) as an <code>Iterable</code>
 *         <li>{@link ElementsFromCollectionProvider} to represent a batch of elements added (or set wholesale) as a provider of <code>Iterable</code>
 *     </ul>
 * <p>Also, if a collection is built up via multiple additions, which is quite common, after each addition operation, its value will be represented via a new {@link CollectionSupplier} instance.
 * Each addition operation adds an individual collector to the shared underlying append-only list of collectors.
 * </p>
 *
 * @param <T> the type of element this collection property can hold
 * @param <C> the type of {@link Collection} (as returned by {@link ProviderInternal#getType()}) that corresponds to this collection property's realized value, for instance, when {@link Provider#get()} is invoked.
 */
public abstract class AbstractCollectionProperty<T, C extends Collection<T>> extends AbstractProperty<C, CollectionSupplier<T, C>>
    implements CollectionPropertyInternal<T, C> {

    private final Class<? extends Collection> collectionType;
    private final Class<T> elementType;
    private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;
    private final ValueCollector<T> valueCollector;
    private CollectionSupplier<T, C> defaultValue;

    AbstractCollectionProperty(PropertyHost host, Class<? extends Collection> collectionType, Class<T> elementType, Supplier<ImmutableCollection.Builder<T>> collectionFactory) {
        super(host);
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.collectionFactory = collectionFactory;
        valueCollector = new ValidatingValueCollector<>(collectionType, elementType, ValueSanitizers.forType(elementType));
        init();
    }

    private void init() {
        defaultValue = emptySupplier();
        init(defaultValue, noValueSupplier());
    }

    @Override
    protected CollectionSupplier<T, C> getDefaultValue() {
        return defaultValue;
    }

    @Override
    protected CollectionSupplier<T, C> getDefaultConvention() {
        return noValueSupplier();
    }

    private CollectionSupplier<T, C> emptySupplier() {
        return new EmptySupplier();
    }

    private CollectionSupplier<T, C> noValueSupplier() {
        return new NoValueSupplier(Value.missing());
    }

    /**
     * Creates an empty immutable collection.
     */
    protected abstract C emptyCollection();

    private void withActualValue(Runnable action) {
        setToConventionIfUnset();
        action.run();
    }

    @Override
    protected boolean isDefaultConvention() {
        return isNoValueSupplier(getConventionSupplier());
    }

    private boolean isNoValueSupplier(CollectionSupplier<T, C> valueSupplier) {
        // Cannot use plain NoValueSupplier because of Java restrictions:
        // a generic type [AbstractCollectionProperty<T, C>.]NoValueSupplier cannot be used in instanceof.
        return valueSupplier instanceof AbstractCollectionProperty<?, ?>.NoValueSupplier;
    }

    @Override
    public void add(final T element) {
        Preconditions.checkNotNull(element, "Cannot add a null element to a property of type %s.", collectionType.getSimpleName());
        addExplicitCollector(new SingleElement<>(element));
    }

    @Override
    public void add(final Provider<? extends T> providerOfElement) {
        addExplicitCollector(new ElementFromProvider<>(Providers.internal(providerOfElement)));
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final void addAll(T... elements) {
        addExplicitCollector(new ElementsFromArray<>(elements));
    }

    @Override
    public void addAll(Iterable<? extends T> elements) {
        addExplicitCollector(new ElementsFromCollection<>(elements));
    }

    @Override
    public void addAll(Provider<? extends Iterable<? extends T>> provider) {
        addExplicitCollector(new ElementsFromCollectionProvider<>(Providers.internal(provider)));
    }

    @Override
    public void append(T element) {
        withActualValue(() -> add(element));
    }

    @Override
    public void append(Provider<? extends T> provider) {
        withActualValue(() -> add(provider));
    }

    @Override
    @SuppressWarnings("varargs")
    @SafeVarargs
    public final void appendAll(T... elements) {
        withActualValue(() -> addAll(elements));
    }

    @Override
    public void appendAll(Iterable<? extends T> elements) {
        withActualValue(() -> addAll(elements));
    }

    @Override
    public void appendAll(Provider<? extends Iterable<? extends T>> provider) {
        withActualValue(() -> addAll(provider));
    }

    @Override
    public int size() {
        return calculateOwnPresentValue().getWithoutSideEffect().size();
    }

    /**
     * Adds the given supplier as the new root supplier for this collection.
     *
     * @param collector the collector to add
     */
    private void addExplicitCollector(Collector<T> collector) {
        assertCanMutate();
        CollectionSupplier<T, C> explicitValue = getExplicitValue(defaultValue);
        setSupplier(explicitValue.plus(collector));
    }

    @Override
    @Nonnull
    public Class<C> getType() {
        return Cast.uncheckedNonnullCast(collectionType);
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
            setSupplier(new FixedSupplier(value.getFixedValue(), Cast.uncheckedCast(value.getSideEffect())));
        } else {
            CollectingSupplier<T, C> asSupplier = Cast.uncheckedNonnullCast(value.getChangingValue());
            setSupplier(asSupplier);
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
            unsetValueAndDefault();
        } else {
            setSupplier(newSupplierOf(new ElementsFromCollection<>(elements)));
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
        setSupplier(newSupplierOf(new ElementsFromCollectionProvider<>(p)));
    }

    private void unsetValueAndDefault() {
        // assign no-value default before restoring to it
        defaultValue = noValueSupplier();
        unset();
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
    protected Value<? extends C> calculateValueFrom(EvaluationScopeContext context, CollectionSupplier<T, C> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected CollectionSupplier<T, C> finalValue(EvaluationScopeContext context, CollectionSupplier<T, C> value, ValueConsumer consumer) {
        Value<? extends C> result = value.calculateValue(consumer);
        if (!result.isMissing()) {
            return new FixedSupplier(result.getWithoutSideEffect(), Cast.uncheckedCast(result.getSideEffect()));
        } else if (result.getPathToOrigin().isEmpty()) {
            return noValueSupplier();
        } else {
            return new NoValueSupplier(result);
        }
    }

    @Override
    protected ExecutionTimeValue<? extends C> calculateOwnExecutionTimeValue(EvaluationScopeContext context, CollectionSupplier<T, C> value) {
        return value.calculateExecutionTimeValue();
    }

    @Override
    public HasMultipleValues<T> convention(@Nullable Iterable<? extends T> elements) {
        if (elements == null) {
            unsetConvention();
        } else {
            setConvention(newSupplierOf(new ElementsFromCollection<>(elements)));
        }
        return this;
    }

    @Override
    public HasMultipleValues<T> convention(Provider<? extends Iterable<? extends T>> provider) {
        setConvention(newSupplierOf(new ElementsFromCollectionProvider<>(Providers.internal(provider))));
        return this;
    }

    @Override
    protected String describeContents() {
        String typeDisplayName = collectionType.getSimpleName().toLowerCase(Locale.ROOT);
        return String.format("%s(%s, %s)", typeDisplayName, elementType, describeValue());
    }

    class NoValueSupplier implements CollectionSupplier<T, C> {
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
            // No value + something = no value.
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

        @Override
        public String toString() {
            return value.toString();
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
            return newSupplierOf(collector);
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(emptyCollection());
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.noProducer();
        }

        @Override
        public String toString() {
            return "[]";
        }
    }

    private class FixedSupplier implements CollectionSupplier<T, C> {
        private final C value;
        private final SideEffect<? super C> sideEffect;

        public FixedSupplier(C value, @Nullable SideEffect<? super C> sideEffect) {
            this.value = value;
            this.sideEffect = sideEffect;
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
            return newSupplierOf(new FixedValueCollector<>(value, sideEffect)).plus(collector);
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(value).withSideEffect(sideEffect);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    private CollectingSupplier<T, C> newSupplierOf(Collector<T> value) {
        return new CollectingSupplier<>(getType(), collectionFactory, valueCollector, value);
    }

    private static class CollectingSupplier<T, C extends Collection<T>> extends AbstractCollectingSupplier<Collector<T>, C> implements CollectionSupplier<T, C> {
        private final Class<C> type;
        private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;
        private final ValueCollector<T> valueCollector;

        public CollectingSupplier(Class<C> type, Supplier<ImmutableCollection.Builder<T>> collectionFactory, ValueCollector<T> valueCollector, Collector<T> value) {
            this(type, collectionFactory, valueCollector, AppendOnceList.of(value));
        }

        // A constructor for sharing.
        private CollectingSupplier(
            Class<C> type,
            Supplier<ImmutableCollection.Builder<T>> collectionFactory,
            ValueCollector<T> valueCollector,
            AppendOnceList<Collector<T>> collectors
        ) {
            super(collectors);
            this.type = type;
            this.collectionFactory = collectionFactory;
            this.valueCollector = valueCollector;
        }

        @Nullable
        @Override
        public Class<C> getType() {
            return type;
        }

        @Override
        public Value<? extends C> calculateOwnValue(ValueConsumer consumer) {
            // TODO - don't make a copy when the collector already produces an immutable collection
            return calculateValue(
                (builder, collector) -> collector.collectEntries(consumer, valueCollector, builder),
                collectionFactory.get(),
                builder -> Cast.uncheckedNonnullCast(builder.build())
            );
        }

        @Override
        public CollectionSupplier<T, C> plus(Collector<T> addedCollector) {
            return new CollectingSupplier<>(type, collectionFactory, valueCollector, collectors.plus(addedCollector));
        }

        @Override
        @SuppressWarnings("unchecked")
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            return calculateExecutionTimeValue(
                collector -> (ExecutionTimeValue<? extends C>) collector.calculateExecutionTimeValue(),
                this::calculateFixedExecutionTimeValue,
                this::calculateChangingExecutionTimeValue
            );
        }

        private ExecutionTimeValue<? extends C> calculateChangingExecutionTimeValue(
            List<ExecutionTimeValue<? extends C>> values
        ) {
            return ExecutionTimeValue.changingValue(
                new CollectingSupplier<>(
                    type,
                    collectionFactory,
                    valueCollector,
                    values.stream().map(this::toCollector).collect(toAppendOnceList())
                )
            );
        }

        private ExecutionTimeValue<? extends C> calculateFixedExecutionTimeValue(
            List<ExecutionTimeValue<? extends C>> executionTimeValues, SideEffectBuilder<C> sideEffectBuilder
        ) {
            ImmutableCollection.Builder<T> entries = collectionFactory.get();
            for (ExecutionTimeValue<? extends C> value : executionTimeValues) {
                entries.addAll(value.getFixedValue());
                sideEffectBuilder.add(SideEffect.fixedFrom(value));
            }
            return ExecutionTimeValue.fixedValue(Cast.uncheckedNonnullCast(entries.build()));
        }

        private Collector<T> toCollector(ExecutionTimeValue<? extends Iterable<? extends T>> value) {
            Preconditions.checkArgument(!value.isMissing(), "Cannot get a collector for the missing value");
            if (value.isChangingValue() || value.hasChangingContent() || value.getSideEffect() != null) {
                return new ElementsFromCollectionProvider<>(value.toProvider());
            }
            return new ElementsFromCollection<>(value.getFixedValue());
        }
    }

    /**
     * A fixed value collector, similar to {@link ElementsFromCollection} but with a side effect.
     */
    private static class FixedValueCollector<T, C extends Collection<T>> implements Collector<T> {
        @Nullable
        private final SideEffect<? super C> sideEffect;
        private final C collection;

        private FixedValueCollector(C collection, @Nullable SideEffect<? super C> sideEffect) {
            this.collection = collection;
            this.sideEffect = sideEffect;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            collector.addAll(collection, dest);
            return sideEffect != null
                ? Value.present().withSideEffect(SideEffect.fixed(collection, sideEffect))
                : Value.present();
        }

        @Override
        public int size() {
            return collection.size();
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(collection).withSideEffect(sideEffect);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public String toString() {
            return collection.toString();
        }
    }

    public void replace(Transformer<? extends @org.jetbrains.annotations.Nullable Provider<? extends Iterable<? extends T>>, ? super Provider<C>> transformation) {
        Provider<? extends Iterable<? extends T>> newValue = transformation.transform(shallowCopy());
        if (newValue != null) {
            set(newValue);
        } else {
            set((Iterable<? extends T>) null);
        }
    }
}
