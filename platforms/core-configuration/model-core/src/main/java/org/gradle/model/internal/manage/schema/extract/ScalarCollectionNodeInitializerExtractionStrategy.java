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
import org.gradle.internal.Cast;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelViewState;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerContext;
import org.gradle.model.internal.core.TypeCompatibilityModelProjectionSupport;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ModelElementProjection;
import org.gradle.model.internal.inspect.ProjectionOnlyNodeInitializer;
import org.gradle.model.internal.manage.schema.CollectionSchema;
import org.gradle.model.internal.manage.schema.ScalarValueSchema;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

public class ScalarCollectionNodeInitializerExtractionStrategy extends CollectionNodeInitializerExtractionSupport {
    public final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
        ModelType.of(List.class),
        ModelType.of(Set.class)
    );

    @Override
    protected <T, E> NodeInitializer extractNodeInitializer(CollectionSchema<T, E> schema, NodeInitializerContext<T> context) {
        ModelType<T> type = schema.getType();
        Class<? super T> rawClass = type.getRawClass();
        ModelType<? super T> rawCollectionType = ModelType.of(rawClass);
        if (TYPES.contains(rawCollectionType) && (schema.getElementTypeSchema() instanceof ScalarValueSchema)) {
            Optional<NodeInitializerContext.PropertyContext> propertyContext = context.getPropertyContextOptional();
            boolean writable = !propertyContext.isPresent() || propertyContext.get().isWritable();
            if (schema.getType().getRawClass() == List.class) {
                return new ProjectionOnlyNodeInitializer(
                    ScalarCollectionModelProjection.forList(schema.getElementType(), !writable),
                    new ModelElementProjection(schema.getType())
                );
            } else {
                return new ProjectionOnlyNodeInitializer(
                    ScalarCollectionModelProjection.forSet(schema.getElementType(), !writable),
                    new ModelElementProjection(schema.getType())
                );
            }
        }
        return null;
    }

    @Override
    public Iterable<ModelType<?>> supportedTypes() {
        return ImmutableList.copyOf(TYPES);
    }

    private abstract static class ScalarCollectionModelProjection<E, C extends Collection<E>> extends TypeCompatibilityModelProjectionSupport<C> {

        public ScalarCollectionModelProjection(ModelType<C> type) {
            super(type);
        }

        @Override
        public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
            Collection<?> values = modelNodeInternal.asImmutable(getType(), null).getInstance();
            if (values == null) {
                return Optional.of("null");
            }
            return Optional.of(values.toString());
        }

        @Override
        protected abstract ScalarCollectionModelView<E, C> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean readOnly);

        public static <E> ScalarCollectionModelProjection<E, List<E>> forList(final ModelType<E> elementType, final boolean readOnly) {
            return new ScalarCollectionModelProjection<E, List<E>>(ModelTypes.list(elementType)) {
                @Override
                protected ScalarCollectionModelView<E, List<E>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean mutable) {
                    return new ListModelView<E>(modelNode.getPath(), elementType, modelNode, ruleDescriptor, readOnly, mutable);
                }
            };
        }

        public static <E> ScalarCollectionModelProjection<E, Set<E>> forSet(final ModelType<E> elementType, final boolean readOnly) {
            return new ScalarCollectionModelProjection<E, Set<E>>(ModelTypes.set(elementType)) {
                @Override
                protected ScalarCollectionModelView<E, Set<E>> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean mutable) {
                    return new SetModelView<E>(modelNode.getPath(), elementType, modelNode, ruleDescriptor, readOnly, mutable);
                }
            };
        }
    }

    private static class ListModelView<T> extends ScalarCollectionModelView<T, List<T>> {

        public ListModelView(ModelPath path, ModelType<T> elementType, MutableModelNode modelNode, ModelRuleDescriptor descriptor, boolean overwritable, boolean mutable) {
            super(path, ModelTypes.list(elementType), elementType, modelNode, descriptor, overwritable, mutable);
        }

        @Override
        protected List<T> initialValue() {
            return new LinkedList<T>();
        }

        @Override
        protected List<T> toMutationSafe(Collection<?> backingCollection) {
            return new ListBackedCollection<T>(Cast.<List<T>>uncheckedCast(backingCollection), state, elementType);
        }
    }

    private static class SetModelView<T> extends ScalarCollectionModelView<T, Set<T>> {

        public SetModelView(ModelPath path, ModelType<T> elementType, MutableModelNode modelNode, ModelRuleDescriptor descriptor, boolean overwritable, boolean mutable) {
            super(path, ModelTypes.set(elementType), elementType, modelNode, descriptor, overwritable, mutable);
        }

        @Override
        protected Set<T> initialValue() {
            return new LinkedHashSet<T>();
        }

        @Override
        protected Set<T> toMutationSafe(Collection<?> backingCollection) {
            return new SetBackedCollection<T>(Cast.<Set<T>>uncheckedCast(backingCollection), state, elementType);
        }
    }

    private abstract static class MutationSafeCollection<T, C extends Collection<T>> implements Collection<T> {
        private final C delegate;
        private final ModelViewState state;
        private final ModelType<T> elementType;

        public MutationSafeCollection(C delegate, ModelViewState state, ModelType<T> elementType) {
            this.delegate = delegate;
            this.state = state;
            this.elementType = elementType;
        }

        public C getDelegate(boolean forMutation) {
            if (forMutation) {
                state.assertCanMutate();
            }
            return delegate;
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
        public abstract boolean equals(Object o);

        @Override
        public abstract int hashCode();

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
        public <E> E[] toArray(E[] a) {
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

        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    private static class ListBackedCollection<T> extends MutationSafeCollection<T, List<T>> implements List<T> {
        public ListBackedCollection(List<T> delegate, ModelViewState state, ModelType<T> elementType) {
            super(delegate, state, elementType);
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

        @Override
        public boolean equals(Object o) {
            return getDelegate(false).equals(o);
        }

        @Override
        public int hashCode() {
            return getDelegate(false).hashCode();
        }
    }

    private static class SetBackedCollection<T> extends MutationSafeCollection<T, Set<T>> implements Set<T> {

        public SetBackedCollection(Set<T> delegate, ModelViewState state, ModelType<T> elementType) {
            super(delegate, state, elementType);
        }

        @Override
        public boolean equals(Object o) {
            return getDelegate(false).equals(o);
        }

        @Override
        public int hashCode() {
            return getDelegate(false).hashCode();
        }
    }
}
