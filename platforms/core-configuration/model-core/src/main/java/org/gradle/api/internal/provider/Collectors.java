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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

public class Collectors {
    public interface ProvidedCollector<T> extends Collector<T> {
        boolean isProvidedBy(Provider<?> provider);
    }

    public static class SingleElement<T> implements Collector<T> {
        private final T element;

        public SingleElement(T element) {
            this.element = element;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            collector.add(element, collection);
            return Value.present();
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(ImmutableList.of(element));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SingleElement<?> that = (SingleElement<?>) o;
            return Objects.equal(element, that.element);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(element);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public String toString() {
            return String.format("[%s]", element);
        }
    }

    public static class ElementFromProvider<T> implements ProvidedCollector<T> {
        private final ProviderInternal<? extends T> provider;

        public ElementFromProvider(ProviderInternal<? extends T> provider) {
            this.provider = provider;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return provider.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            Value<? extends T> value = provider.calculateValue(consumer);
            if (value.isMissing()) {
                return value.asType();
            }

            collector.add(value.getWithoutSideEffect(), collection);
            return Value.present().withSideEffect(SideEffect.fixedFrom(value));
        }

        @Override
        public boolean isProvidedBy(Provider<?> provider) {
            return Objects.equal(provider, this.provider);
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            ExecutionTimeValue<? extends T> value = provider.calculateExecutionTimeValue();
            return visitValue(value);
        }

        @Override
        public ValueProducer getProducer() {
            return provider.getProducer();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ElementFromProvider<?> that = (ElementFromProvider<?>) o;
            return Objects.equal(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(provider);
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public String toString() {
            return String.format("item(%s)", provider);
        }
    }

    private static <T> ValueSupplier.ExecutionTimeValue<? extends Iterable<? extends T>> visitValue(ValueSupplier.ExecutionTimeValue<? extends T> value) {
        if (value.isMissing()) {
            return ValueSupplier.ExecutionTimeValue.missing();
        } else if (value.hasFixedValue()) {
            // transform preserving side effects
            return ValueSupplier.ExecutionTimeValue.value(value.toValue().transform(ImmutableList::of));
        } else {
            return ValueSupplier.ExecutionTimeValue.changingValue(value.getChangingValue().map(transformer(ImmutableList::of)));
        }
    }

    public static class ElementsFromCollection<T> implements Collector<T> {
        private final Iterable<? extends T> value;

        public ElementsFromCollection(Iterable<? extends T> value) {
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            collector.addAll(value, collection);
            return Value.present();
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(value);
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ElementsFromCollection<?> that = (ElementsFromCollection<?>) o;

            // We're fine with having weak contract of Iterable/Collection.equals.
            @SuppressWarnings("UndefinedEquals")
            boolean result = Objects.equal(value, that.value);
            return result;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public int size() {
            return Iterables.size(value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    public static class ElementsFromCollectionProvider<T> implements ProvidedCollector<T> {
        private final ProviderInternal<? extends Iterable<? extends T>> provider;

        public ElementsFromCollectionProvider(ProviderInternal<? extends Iterable<? extends T>> provider) {
            this.provider = provider;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return provider.calculatePresence(consumer);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> collection) {
            Value<? extends Iterable<? extends T>> value = provider.calculateValue(consumer);
            return collectEntriesFromValue(collector, collection, value);
        }

        private ValueSupplier.Value<Void> collectEntriesFromValue(ValueCollector<T> collector, ImmutableCollection.Builder<T> collection, ValueSupplier.Value<? extends Iterable<? extends T>> value) {
            if (value.isMissing()) {
                return value.asType();
            }

            collector.addAll(value.getWithoutSideEffect(), collection);
            return ValueSupplier.Value.present().withSideEffect(ValueSupplier.SideEffect.fixedFrom(value));
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            return provider.calculateExecutionTimeValue();
        }

        @Override
        public ValueProducer getProducer() {
            return provider.getProducer();
        }

        @Override
        public boolean isProvidedBy(Provider<?> provider) {
            return Objects.equal(this.provider, provider);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ElementsFromCollectionProvider<?> that = (ElementsFromCollectionProvider<?>) o;
            return Objects.equal(provider, that.provider);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(provider);
        }

        @Override
        public int size() {
            if (provider instanceof CollectionProviderInternal) {
                return ((CollectionProviderInternal) provider).size();
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public String toString() {
            return String.valueOf(provider);
        }
    }

    public static class ElementsFromArray<T> implements Collector<T> {
        private final T[] value;

        ElementsFromArray(T[] value) {
            this.value = value;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return true;
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            for (T t : value) {
                collector.add(t, dest);
            }
            return Value.present();
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(ImmutableList.copyOf(value));
        }

        @Override
        public ValueProducer getProducer() {
            return ValueProducer.unknown();
        }

        @Override
        public int size() {
            return value.length;
        }

        @Override
        public String toString() {
            return Arrays.toString(value);
        }
    }

    public static class TypedCollector<T> implements ProvidedCollector<T> {
        private final Class<? extends T> type;
        protected final Collector<T> delegate;
        private final ValueCollector<T> valueCollector;

        public TypedCollector(@Nullable Class<? extends T> type, Collector<T> delegate) {
            this.type = type;
            this.delegate = delegate;
            this.valueCollector = ValueSanitizers.collectorFor(type);
        }

        @Nullable
        public Class<? extends T> getType() {
            return type;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return delegate.calculatePresence(consumer);
        }

        public void collectInto(ImmutableCollection.Builder<T> builder) {
            collectEntries(ValueConsumer.IgnoreUnsafeRead, valueCollector, builder);
        }

        @Override
        public Value<Void> collectEntries(ValueConsumer consumer, ValueCollector<T> collector, ImmutableCollection.Builder<T> dest) {
            return delegate.collectEntries(consumer, collector, dest);
        }

        @Override
        public boolean isProvidedBy(Provider<?> provider) {
            return delegate instanceof ProvidedCollector && ((ProvidedCollector<T>) delegate).isProvidedBy(provider);
        }

        @Override
        public ExecutionTimeValue<? extends Iterable<? extends T>> calculateExecutionTimeValue() {
            return delegate.calculateExecutionTimeValue();
        }

        @Override
        public ValueProducer getProducer() {
            return delegate.getProducer();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypedCollector<?> that = (TypedCollector<?>) o;
            return Objects.equal(type, that.type) &&
                Objects.equal(delegate, that.delegate);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(type, delegate);
        }

        @Override
        public String toString() {
            return String.format("(%s as %s)", delegate, type);
        }
    }
}
