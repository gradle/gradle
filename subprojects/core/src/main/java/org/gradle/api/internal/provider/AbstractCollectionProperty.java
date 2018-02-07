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
    private final Class<? extends Collection> collectionType;
    private Collector<T> value = (Collector<T>) EMPTY_COLLECTION;
    private List<Collector<T>> collectors = new LinkedList<Collector<T>>();

    AbstractCollectionProperty(Class<? extends Collection> collectionType) {
        this.collectionType = collectionType;
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
    public void addAll(final Provider<? extends Iterable<T>> providerOfElements) {
        collectors.add(new ElementsFromCollectionProvider<T>(providerOfElements));
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
        value.collectInto(values);
        for (Collector<T> collector : collectors) {
            collector.collectInto(values);
        }
        return fromValue(values);
    }

    @Nullable
    @Override
    public C getOrNull() {
        List<T> values = new ArrayList<T>(1 + collectors.size());
        if (!value.maybeCollectInto(values)) {
            return null;
        }
        for (Collector<T> collector : collectors) {
            if (!collector.maybeCollectInto(values)) {
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
    public void set(@Nullable final Iterable<? extends T> value) {
        collectors.clear();
        if (value == null) {
            this.value = (Collector<T>) NO_VALUE_COLLECTOR;
        } else {
            this.value = new ElementsFromCollection<T>(value);
        }
    }

    @Override
    public void set(final Provider<? extends Iterable<? extends T>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        collectors.clear();
        value = new ElementsFromProvider<T>(provider);
    }

    /**
     * This could move to the public API.
     */
    private interface Collector<T> {
        boolean present();

        void collectInto(Collection<T> collection);

        boolean maybeCollectInto(Collection<T> collection);
    }

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super C> transformer) {
        return new AbstractProvider<S>() {
            @Nullable
            @Override
            public Class<S> getType() {
                return null;
            }

            @Nullable
            @Override
            public S getOrNull() {
                Collection<T> value = AbstractCollectionProperty.this.getOrNull();
                if (value == null) {
                    return null;
                }
                S result = transformer.transform(fromValue(value));
                if (result == null) {
                    throw new IllegalStateException(Providers.NULL_TRANSFORMER_RESULT);
                }
                return result;
            }
        };
    }

    private static class EmptyCollection implements Collector<Object> {
        @Override
        public boolean present() {
            return true;
        }

        @Override
        public boolean maybeCollectInto(Collection<Object> collection) {
            return true;
        }

        @Override
        public void collectInto(Collection<Object> collection) {
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
        public void collectInto(Collection<T> collection) {
            collection.add(element);
        }

        @Override
        public boolean maybeCollectInto(Collection<T> collection) {
            collection.add(element);
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
        public void collectInto(Collection<T> collection) {
            T value = providerOfElement.get();
            collection.add(value);
        }

        @Override
        public boolean maybeCollectInto(Collection<T> collection) {
            T value = providerOfElement.getOrNull();
            if (value == null) {
                return false;
            }
            collection.add(value);
            return true;
        }
    }

    private static class ElementsFromCollectionProvider<T> implements Collector<T> {
        private final Provider<? extends Iterable<T>> providerOfElements;

        ElementsFromCollectionProvider(Provider<? extends Iterable<T>> providerOfElements) {
            this.providerOfElements = providerOfElements;
        }

        @Override
        public boolean present() {
            return providerOfElements.isPresent();
        }

        @Override
        public void collectInto(Collection<T> collection) {
            Iterable<T> value = providerOfElements.get();
            CollectionUtils.addAll(collection, value);
        }

        @Override
        public boolean maybeCollectInto(Collection<T> collection) {
            Iterable<T> value = providerOfElements.getOrNull();
            if (value == null) {
                return false;
            }
            CollectionUtils.addAll(collection, value);
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
        public void collectInto(Collection<T> collection) {
            CollectionUtils.addAll(collection, value);
        }

        @Override
        public boolean maybeCollectInto(Collection<T> collection) {
            CollectionUtils.addAll(collection, value);
            return true;
        }
    }

    private static class ElementsFromProvider<T> implements Collector<T> {
        private final Provider<? extends Iterable<? extends T>> provider;

        ElementsFromProvider(Provider<? extends Iterable<? extends T>> provider) {
            this.provider = provider;
        }

        @Override
        public boolean present() {
            return provider.isPresent();
        }

        @Override
        public void collectInto(Collection<T> collection) {
            Iterable<? extends T> value = provider.get();
            CollectionUtils.addAll(collection, value);
        }

        @Override
        public boolean maybeCollectInto(Collection<T> collection) {
            Iterable<? extends T> value = provider.getOrNull();
            if (value == null) {
                return false;
            }
            CollectionUtils.addAll(collection, value);
            return true;
        }
    }

    private static class NoValueCollector implements Collector<Object> {
        @Override
        public boolean present() {
            return false;
        }

        @Override
        public void collectInto(Collection<Object> collection) {
            throw new IllegalStateException(Providers.NULL_VALUE);
        }

        @Override
        public boolean maybeCollectInto(Collection<Object> collection) {
            return false;
        }
    }
}
