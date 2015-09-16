/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.schema.ModelCollectionSchema;
import org.gradle.model.internal.manage.schema.ScalarCollectionSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.*;

public class ScalarCollectionNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    public final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
        ModelType.of(List.class),
        ModelType.of(Set.class)
    );

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(ModelCollectionSchema<T, E> schema, NodeInitializerRegistry nodeInitializerRegistry) {
        ModelType<T> type = schema.getType();
        Class<? super T> rawClass = type.getRawClass();
        ModelType<? super T> rawCollectionType = ModelType.of(rawClass);
        if (TYPES.contains(rawCollectionType)) {
            if (schema.getType().getRawClass() == List.class) {
                return new ProjectionOnlyNodeInitializer(
                    ScalarCollectionModelProjection.get(
                        ModelTypes.list(schema.getElementType()),
                        new ListViewFactory<E>(schema.getElementType())
                    )
                );
            } else {
                return new ProjectionOnlyNodeInitializer(
                    ScalarCollectionModelProjection.get(
                        ModelTypes.set(schema.getElementType()),
                        new SetViewFactory<E>(schema.getElementType())
                    )
                );
            }
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.<ModelType<?>>builder().addAll(TYPES).build();
    }

    private static class ScalarCollectionModelProjection<E> extends TypedModelProjection<E> {

        public static <E, U extends Collection<E>> ScalarCollectionModelProjection<U> get(ModelType<U> type, ModelViewFactory<U> viewFactory) {
            return new ScalarCollectionModelProjection<U>(type, viewFactory);
        }

        public ScalarCollectionModelProjection(ModelType<E> type, ModelViewFactory<E> viewFactory) {
            super(type, viewFactory, true, true);
        }

        @Override
        public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
            Collection<?> values = ScalarCollectionSchema.get(modelNodeInternal);
            if (values == null) {
                return Optional.absent();
            }
            return Optional.of(values.toString());
        }
    }


    public static class ListViewFactory<T> implements ModelViewFactory<List<T>> {
        private final ModelType<T> elementType;

        public ListViewFactory(ModelType<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public ModelView<List<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<List<T>> listType = ModelTypes.list(elementType);
            DefaultModelViewState state = new DefaultModelViewState(listType, ruleDescriptor, writable, !writable);
            ListBackedCollection<T> list = new ListBackedCollection<T>(modelNode, state, elementType);
            return InstanceModelView.of(modelNode.getPath(), listType, list, state.closer());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ListViewFactory<?> that = (ListViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }

    public static class SetViewFactory<T> implements ModelViewFactory<Set<T>> {
        private final ModelType<T> elementType;

        public SetViewFactory(ModelType<T> elementType) {
            this.elementType = elementType;
        }

        @Override
        public ModelView<Set<T>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
            ModelType<Set<T>> setType = ModelTypes.set(elementType);
            DefaultModelViewState state = new DefaultModelViewState(setType, ruleDescriptor, writable, !writable);
            SetBackedCollection<T> set = new SetBackedCollection<T>(modelNode, state, elementType);
            return InstanceModelView.of(modelNode.getPath(), setType, set, state.closer());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ListViewFactory<?> that = (ListViewFactory<?>) o;
            return elementType.equals(that.elementType);

        }

        @Override
        public int hashCode() {
            return elementType.hashCode();
        }
    }

    private abstract static class NodeBackedCollection<T, C extends Collection<T>> implements Collection<T>, ManagedInstance {
        private final MutableModelNode modelNode;
        private final ModelViewState state;
        private final ModelType<T> elementType;

        public NodeBackedCollection(MutableModelNode modelNode, ModelViewState state, ModelType<T> elementType) {
            this.modelNode = modelNode;
            this.state = state;
            this.elementType = elementType;
        }

        protected C getDelegate(boolean write) {
            if (write) {
                state.assertCanMutate();
            }
            Collection<T> delegate = Cast.uncheckedCast(ScalarCollectionSchema.get(modelNode));
            return initialValue(write, delegate);
        }

        protected abstract C createPrivateData(boolean mutable);

        private C initialValue(boolean write, Collection<T> delegate) {
            if (delegate == null) {
                if (write) {
                    delegate = createPrivateData(true);
                    ScalarCollectionSchema.set(modelNode, delegate);
                } else {
                    delegate = createPrivateData(false);
                }
            }
            return Cast.uncheckedCast(delegate);
        }

        @Override
        public MutableModelNode getBackingNode() {
            return modelNode;
        }

        @Override
        public ModelType<?> getManagedType() {
            return ModelType.of(this.getClass());
        }

        protected void validateElementType(Object o) {
            if (o != null) {
                ModelType<?> obType = ModelType.of(o.getClass());
                if (!obType.equals(elementType)) {
                    throw new IllegalArgumentException(String.format("Cannot add an element of type %s to a collection of %s", obType, elementType));
                }
            }
        }

        protected void validateCollection(Collection<? extends T> c) {
            for (T element : c) {
                validateElementType(element);
            }
        }

        @Override
        public boolean add(T t) {
            validateElementType(t);
            return getDelegate(true).add(t);
        }

        @Override
        public boolean addAll(Collection<? extends T> c) {
            validateCollection(c);
            return getDelegate(true).addAll(c);
        }

        @Override
        public void clear() {
            getDelegate(true).clear();
        }

        @Override
        public boolean contains(Object o) {
            return getDelegate(false).contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return getDelegate(false).containsAll(c);
        }

        @Override
        public boolean equals(Object o) {
            return getDelegate(false).equals(o);
        }

        @Override
        public int hashCode() {
            return getDelegate(false).hashCode();
        }

        @Override
        public boolean isEmpty() {
            return getDelegate(false).isEmpty();
        }

        @Override
        public Iterator<T> iterator() {
            return new MutationSafeIterator(getDelegate(false).iterator());
        }

        @Override
        public boolean remove(Object o) {
            return getDelegate(true).remove(o);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return getDelegate(true).removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return getDelegate(true).retainAll(c);
        }

        @Override
        public int size() {
            return getDelegate(false).size();
        }

        @Override
        public Object[] toArray() {
            return getDelegate(false).toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return getDelegate(false).toArray(a);
        }

        private final class MutationSafeIterator implements Iterator<T> {
            private final Iterator<T> delegate;

            private MutationSafeIterator(Iterator<T> delegate) {
                this.delegate = delegate;
            }

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
                return delegate.next();
            }

            @Override
            public void remove() {
                state.assertCanMutate();
                delegate.remove();
            }
        }
    }

    private static class ListBackedCollection<T> extends NodeBackedCollection<T, List<T>> implements List<T> {

        public ListBackedCollection(MutableModelNode modelNode, ModelViewState state, ModelType<T> elementType) {
            super(modelNode, state, elementType);
        }

        @Override
        protected List<T> createPrivateData(boolean mutable) {
            if (mutable) {
                return Lists.newArrayList();
            }
            return Collections.emptyList();
        }

        @Override
        public void add(int index, T element) {
            validateElementType(element);
            getDelegate(true).add(index, element);
        }


        @Override
        public boolean addAll(int index, Collection<? extends T> c) {
            validateCollection(c);
            return getDelegate(true).addAll(index, c);
        }

        @Override
        public T get(int index) {
            return getDelegate(false).get(index);
        }

        @Override
        public int indexOf(Object o) {
            return getDelegate(false).indexOf(o);
        }

        @Override
        public int lastIndexOf(Object o) {
            return getDelegate(false).lastIndexOf(o);
        }

        @Override
        public ListIterator<T> listIterator() {
            return getDelegate(false).listIterator();
        }

        @Override
        public ListIterator<T> listIterator(int index) {
            return getDelegate(false).listIterator(index);
        }

        @Override
        public T remove(int index) {
            return getDelegate(true).remove(index);
        }

        @Override
        public List<T> subList(int fromIndex, int toIndex) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T set(int index, T element) {
            validateElementType(element);
            return getDelegate(true).set(index, element);
        }
    }

    private static class SetBackedCollection<T> extends NodeBackedCollection<T, Set<T>> implements Set<T> {

        public SetBackedCollection(MutableModelNode modelNode, ModelViewState state, ModelType<T> elementType) {
            super(modelNode, state, elementType);
        }

        @Override
        protected Set<T> createPrivateData(boolean mutable) {
            if (mutable) {
                return Sets.newLinkedHashSet();
            } else {
                return Collections.emptySet();
            }
        }
    }
}
