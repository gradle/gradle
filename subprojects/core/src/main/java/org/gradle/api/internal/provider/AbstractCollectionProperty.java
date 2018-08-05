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
import groovy.lang.GString;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public abstract class AbstractCollectionProperty<T, C extends Collection<T>> extends AbstractProvider<C> implements CollectionPropertyInternal<T, C> {
    private static final EmptyCollection EMPTY_COLLECTION = new EmptyCollection();
    private static final NoValueCollector NO_VALUE_COLLECTOR = new NoValueCollector();
    private static final StringValueCollector STRING_VALUE_COLLECTOR = new StringValueCollector();
    private static final IdentityValueCollector IDENTITY_VALUE_COLLECTOR = new IdentityValueCollector();
    private final Class<? extends Collection> collectionType;
    private final Class elementType;
    private final ValueCollector<T> valueCollector;
    private Collector<T> value = (Collector<T>) EMPTY_COLLECTION;
    private List<Collector<T>> collectors = new LinkedList<Collector<T>>();

    AbstractCollectionProperty(Class<? extends Collection> collectionType, Class<T> elementType) {
        this.collectionType = collectionType;
        this.elementType = elementType;
        valueCollector = (ValueCollector<T>) (elementType == String.class ? STRING_VALUE_COLLECTOR : IDENTITY_VALUE_COLLECTOR);
    }

    /**
     * Creates an immutable collection from the given current values of this property.
     */
    protected abstract C fromValue(Collection<T> values);

    @Override
    public void add(final T element) {
        Preconditions.checkNotNull(element, String.format("Cannot add a null element to a property of type %s.", collectionType.getSimpleName()));
        collectors.add(new SingleElement<T>(element));
    }

    @Override
    public void add(final Provider<? extends T> providerOfElement) {
        collectors.add(new ElementFromProvider<T>(providerOfElement));
    }

    @Override
    public void addAll(T... elements) {
        collectors.add(new ElementsFromArray<T>(elements));
    }

    @Override
    public void addAll(Iterable<? extends T> elements) {
        collectors.add(new ElementsFromCollection<T>(elements));
    }

    @Override
    public void addAll(Provider<? extends Iterable<? extends T>> provider) {
        collectors.add(new ElementsFromCollectionProvider<T>(provider));
    }

    @Nullable
    @Override
    public Class<C> getType() {
        return null;
    }

    @Override
    public boolean isPresent() {
        if (!value.present()) {
            return false;
        }
        for (Collector<T> collector : collectors) {
            if (!collector.present()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public C get() {
        List<T> values = new ArrayList<T>(1 + collectors.size());
        value.collectInto(valueCollector, values);
        for (Collector<T> collector : collectors) {
            collector.collectInto(valueCollector, values);
        }
        return fromValue(values);
    }

    @Nullable
    @Override
    public C getOrNull() {
        List<T> values = new ArrayList<T>(1 + collectors.size());
        if (!value.maybeCollectInto(valueCollector, values)) {
            return null;
        }
        for (Collector<T> collector : collectors) {
            if (!collector.maybeCollectInto(valueCollector, values)) {
                return null;
            }
        }
        return fromValue(values);
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
        collectors.clear();
        if (elements == null) {
            this.value = (Collector<T>) NO_VALUE_COLLECTOR;
        } else {
            this.value = new ElementsFromCollection<T>(elements);
        }
    }

    @Override
    public void set(final Provider<? extends Iterable<? extends T>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        collectors.clear();
        value = new ElementsFromCollectionProvider<T>(provider);
    }

    @Override
    public String toString() {
        final String valueState;
        if (value == EMPTY_COLLECTION) {
            valueState = "empty";
        } else if (value == NO_VALUE_COLLECTOR) {
            valueState = "undefined";
        } else {
            valueState = "defined";
        }
        return String.format("%s(%s, %s)", collectionType.getSimpleName().toLowerCase(), elementType, valueState);
    }

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super C> transformer) {
        return new TransformBackedProvider<S, C>(transformer, this);
    }

    private interface ValueCollector<T> {
        void add(T value, Collection<T> dest);

        void addAll(Iterable<? extends T> values, Collection<T> dest);
    }

    /**
     * This could move to the public API.
     */
    private interface Collector<T> {
        boolean present();

        void collectInto(ValueCollector<T> collector, Collection<T> dest);

        boolean maybeCollectInto(ValueCollector<T> collector, Collection<T> dest);
    }

    private static class IdentityValueCollector implements ValueCollector<Object> {
        @Override
        public void add(Object value, Collection<Object> dest) {
            dest.add(value);
        }

        @Override
        public void addAll(Iterable<?> values, Collection<Object> dest) {
            CollectionUtils.addAll(dest, values);
        }
    }

    private static class StringValueCollector implements ValueCollector<Object> {
        @Override
        public void add(Object value, Collection<Object> dest) {
            if (value instanceof GString) {
                dest.add(value.toString());
            } else {
                dest.add(value);
            }
        }

        @Override
        public void addAll(Iterable<?> values, Collection<Object> dest) {
            for (Object value : values) {
                add(value, dest);
            }
        }
    }

    private static class EmptyCollection implements Collector<Object> {
        @Override
        public boolean present() {
            return true;
        }

        @Override
        public void collectInto(ValueCollector<Object> collector, Collection<Object> dest) {
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<Object> collector, Collection<Object> dest) {
            return true;
        }
    }

    private static class SingleElement<T> implements Collector<T> {
        private final T element;

        SingleElement(T element) {
            this.element = element;
        }

        @Override
        public boolean present() {
            return true;
        }

        @Override
        public void collectInto(ValueCollector<T> collector, Collection<T> dest) {
            collector.add(element, dest);
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<T> collector, Collection<T> dest) {
            collectInto(collector, dest);
            return true;
        }
    }

    private static class ElementFromProvider<T> implements Collector<T> {
        private final Provider<? extends T> providerOfElement;

        ElementFromProvider(Provider<? extends T> providerOfElement) {
            this.providerOfElement = providerOfElement;
        }

        @Override
        public boolean present() {
            return providerOfElement.isPresent();
        }

        @Override
        public void collectInto(ValueCollector<T> collector, Collection<T> dest) {
            T value = providerOfElement.get();
            collector.add(value, dest);
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<T> collector, Collection<T> dest) {
            T value = providerOfElement.getOrNull();
            if (value == null) {
                return false;
            }
            collector.add(value, dest);
            return true;
        }
    }

    private static class ElementsFromCollectionProvider<T> implements Collector<T> {
        private final Provider<? extends Iterable<? extends T>> providerOfElements;

        ElementsFromCollectionProvider(Provider<? extends Iterable<? extends T>> providerOfElements) {
            this.providerOfElements = providerOfElements;
        }

        @Override
        public boolean present() {
            return providerOfElements.isPresent();
        }

        @Override
        public void collectInto(ValueCollector<T> collector, Collection<T> dest) {
            Iterable<? extends T> value = providerOfElements.get();
            collector.addAll(value, dest);
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<T> collector, Collection<T> dest) {
            Iterable<? extends T> value = providerOfElements.getOrNull();
            if (value == null) {
                return false;
            }
            collector.addAll(value, dest);
            return true;
        }
    }

    private static class ElementsFromArray<T> implements Collector<T> {
        private final T[] value;

        ElementsFromArray(T[] value) {
            this.value = value;
        }

        @Override
        public boolean present() {
            return true;
        }

        @Override
        public void collectInto(ValueCollector<T> collector, Collection<T> dest) {
            for (T t : value) {
                collector.add(t, dest);
            }
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<T> collector, Collection<T> dest) {
            collectInto(collector, dest);
            return true;
        }
    }

    private static class ElementsFromCollection<T> implements Collector<T> {
        private final Iterable<? extends T> value;

        ElementsFromCollection(Iterable<? extends T> value) {
            this.value = value;
        }

        @Override
        public boolean present() {
            return true;
        }

        @Override
        public void collectInto(ValueCollector<T> collector, Collection<T> dest) {
            collector.addAll(value, dest);
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<T> collector, Collection<T> dest) {
            collectInto(collector, dest);
            return true;
        }
    }

    private static class NoValueCollector implements Collector<Object> {
        @Override
        public boolean present() {
            return false;
        }

        @Override
        public void collectInto(ValueCollector<Object> collector, Collection<Object> dest) {
            throw new IllegalStateException(Providers.NULL_VALUE);
        }

        @Override
        public boolean maybeCollectInto(ValueCollector<Object> collector, Collection<Object> dest) {
            return false;
        }
    }
}
