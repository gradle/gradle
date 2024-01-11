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
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.Collectors.ElementFromProvider;
import org.gradle.api.internal.provider.Collectors.ElementsFromArray;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollection;
import org.gradle.api.internal.provider.Collectors.ElementsFromCollectionProvider;
import org.gradle.api.internal.provider.Collectors.SingleElement;
import org.gradle.api.provider.CollectionPropertyConfigurer;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.Cast;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public abstract class AbstractCollectionProperty<T, C extends Collection<T>>
    extends AbstractProperty<C, AbstractCollectionProperty.CollectionSupplierGuard<T, C>>
    implements CollectionPropertyInternal<T, C> {

    private static final CollectionSupplier<Object, Collection<Object>> NO_VALUE = new NoValueSupplier<>(Value.missing());
    private final Class<? extends Collection> collectionType;
    private final Class<T> elementType;
    private final Supplier<ImmutableCollection.Builder<T>> collectionFactory;
    private final ValueCollector<T> valueCollector;
    private CollectionSupplierGuard<T, C> defaultValue = emptySupplier();

    AbstractCollectionProperty(PropertyHost host, Class<? extends Collection> collectionType, Class<T> elementType, Supplier<ImmutableCollection.Builder<T>> collectionFactory) {
        super(host);
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.collectionFactory = collectionFactory;
        valueCollector = new ValidatingValueCollector<>(collectionType, elementType, ValueSanitizers.forType(elementType));
        init(defaultValue, noValueSupplier());
    }

    @Override
    protected CollectionSupplierGuard<T, C> getDefaultConvention() {
        return noValueSupplier();
    }

    private CollectionSupplierGuard<T, C> emptySupplier() {
        return guard(new EmptySupplier());
    }

    private CollectionSupplierGuard<T, C> noValueSupplier() {
        return guard(Cast.uncheckedCast(NO_VALUE));
    }

    private void setSupplier(CollectionSupplier<T, C> unguardedSupplier) {
        setSupplier(guard(unguardedSupplier));
    }

    private void setConvention(CollectionSupplier<T, C> unguardedConvention) {
        setConvention(guard(unguardedConvention));
    }

    /**
     * Creates an empty immutable collection.
     */
    protected abstract C emptyCollection();

    public CollectionPropertyConfigurer<T> getExplicitValue() {
        return new ExplicitValueConfigurer();
    }

    @Override
    public HasMultipleValues<T> withActualValue(Action<CollectionPropertyConfigurer<T>> action) {
        setToConventionIfUnset();
        action.execute(getExplicitValue());
        return this;
    }

    @Override
    public HasMultipleValues<T> withActualValue(@DelegatesTo(CollectionPropertyConfigurer.class) Closure<Void> action) {
        setToConventionIfUnset();
        ConfigureUtil.configure(action, getExplicitValue());
        return this;
    }

    @Override
    protected boolean isDefaultConvention() {
        return isNoValueSupplier(getConventionSupplier());
    }

    private boolean isNoValueSupplier(CollectionSupplierGuard<T, C> valueSupplier) {
        return valueSupplier.supplier instanceof NoValueSupplier;
    }

    @Override
    public void add(final T element) {
        getExplicitValue().add(element);
    }

    @Override
    public void add(final Provider<? extends T> providerOfElement) {
        getExplicitValue().add(providerOfElement);
    }

    @Override
    @SafeVarargs
    @SuppressWarnings("varargs")
    public final void addAll(T... elements) {
        getExplicitValue().addAll(elements);
    }

    @Override
    public void addAll(Iterable<? extends T> elements) {
        getExplicitValue().addAll(elements);
    }

    @Override
    public void addAll(Provider<? extends Iterable<? extends T>> provider) {
        getExplicitValue().addAll(provider);
    }

    @Override
    public int size() {
        return calculateOwnPresentValue().getWithoutSideEffect().size();
    }

    private void addExplicitCollector(Collector<T> collector) {
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
            unset();
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
    public SupportsConvention unset() {
        discardValue();
        defaultValue = noValueSupplier();
        return this;
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
    protected Value<? extends C> calculateValueFrom(CollectionSupplierGuard<T, C> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected CollectionSupplierGuard<T, C> finalValue(CollectionSupplierGuard<T, C> value, ValueConsumer consumer) {
        Value<? extends C> result = value.calculateValue(consumer);
        if (!result.isMissing()) {
            return guard(new FixedSupplier<>(result.getWithoutSideEffect(), Cast.uncheckedCast(result.getSideEffect())));
        } else if (result.getPathToOrigin().isEmpty()) {
            return noValueSupplier();
        } else {
            return guard(new NoValueSupplier<>(result));
        }
    }

    @Override
    protected ExecutionTimeValue<? extends C> calculateOwnExecutionTimeValue(CollectionSupplierGuard<T, C> value) {
        return value.calculateExecutionTimeValue();
    }

    @Override
    public HasMultipleValues<T> convention(@Nullable Iterable<? extends T> elements) {
        if (elements == null) {
            unsetConvention();
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
            return Value.of(Cast.<C>uncheckedNonnullCast(builder.build())).withSideEffect(SideEffect.fixedFrom(result));
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
            Value<Void> leftValue = left.collectEntries(consumer, collector, dest);
            if (leftValue.isMissing()) {
                return leftValue;
            }
            Value<Void> rightValue = right.collectEntries(consumer, collector, dest);
            if (rightValue.isMissing()) {
                return rightValue;
            }

            return Value.present()
                .withSideEffect(SideEffect.fixedFrom(leftValue))
                .withSideEffect(SideEffect.fixedFrom(rightValue));
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

    public void update(Transformer<? extends @org.jetbrains.annotations.Nullable Provider<? extends Iterable<? extends T>>, ? super Provider<C>> transform) {
        Provider<? extends Iterable<? extends T>> newValue = transform.transform(shallowCopy());
        if (newValue != null) {
            set(newValue);
        } else {
            set((Iterable<? extends T>) null);
        }
    }

    protected CollectionSupplierGuard<T, C> guard(CollectionSupplier<T, C> supplier) {
        return new CollectionSupplierGuard<>(this, supplier);
    }

    protected static final class CollectionSupplierGuard<T, C extends Collection<T>> implements CollectionSupplier<T, C>, GuardedData<CollectionSupplier<T, C>>, GuardedValueSupplier<CollectionSupplierGuard<T, C>> {
        private final EvaluationContext.EvaluationOwner owner;
        private final CollectionSupplier<T, C> supplier;

        public CollectionSupplierGuard(EvaluationContext.EvaluationOwner owner, CollectionSupplier<T, C> supplier) {
            this.owner = owner;
            this.supplier = supplier;
        }

        @Override
        public CollectionSupplierGuard<T, C> withOwner(EvaluationContext.EvaluationOwner newOwner) {
            return new CollectionSupplierGuard<T, C>(newOwner, supplier);
        }

        @Override
        public EvaluationContext.EvaluationOwner getOwner() {
            return owner;
        }

        @Override
        public CollectionSupplier<T, C> unsafeGet() {
            return supplier;
        }

        @Override
        public Value<? extends C> calculateValue(ValueConsumer consumer) {
            try (EvaluationContext.ScopeContext ignore = EvaluationContext.current().open(owner)) {
                return supplier.calculateValue(consumer);
            }
        }

        @Override
        public CollectionSupplierGuard<T, C> plus(Collector<T> collector) {
            return new CollectionSupplierGuard<>(owner, supplier.plus(collector));
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            try (EvaluationContext.ScopeContext ignore = EvaluationContext.current().open(owner)) {
                return supplier.calculateExecutionTimeValue();
            }
        }

        @Override
        public ValueProducer getProducer() {
            try (EvaluationContext.ScopeContext ignore = EvaluationContext.current().open(owner)) {
                return supplier.getProducer();
            }
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            try (EvaluationContext.ScopeContext ignore = EvaluationContext.current().open(owner)) {
                return supplier.calculatePresence(consumer);
            }
        }
    }

    private abstract class Configurer implements CollectionPropertyConfigurer<T> {
        protected abstract void addCollector(Collector<T> collector);

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
    }

    private class ExplicitValueConfigurer extends Configurer {
        @Override
        protected void addCollector(Collector<T> collector) {
            addExplicitCollector(collector);
        }
    }
}
