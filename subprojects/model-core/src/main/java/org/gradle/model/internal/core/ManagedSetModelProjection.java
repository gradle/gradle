/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.core;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.internal.BiAction;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.WriteOnlyModelViewException;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.inspect.ModelCreatorFactory;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

import java.util.*;

public class ManagedSetModelProjection<I> extends TypeCompatibilityModelProjectionSupport<ManagedSet<I>> {
    private final ModelType<I> elementType;
    private final ModelCreatorFactory modelCreatorFactory;
    private final ModelSchema<I> elementSchema;

    private ManagedSetModelProjection(ModelType<ManagedSet<I>> type, ModelSchema<I> elementSchema, ModelCreatorFactory modelCreatorFactory) {
        super(type, true, true);
        this.elementSchema = elementSchema;
        this.elementType = elementSchema.getType();
        this.modelCreatorFactory = modelCreatorFactory;
    }

    public static <I> ManagedSetModelProjection<I> of(ModelType<I> elementType, ModelSchemaStore modelSchemaStore, ModelCreatorFactory modelCreatorFactory) {
        ModelSchema<I> elementSchema = modelSchemaStore.getSchema(elementType);
        return new ManagedSetModelProjection<I>(new ModelType.Builder<ManagedSet<I>>() {
        }.where(new ModelType.Parameter<I>() {
        }, elementType).build(), elementSchema, modelCreatorFactory);
    }

    @Override
    protected ModelView<ManagedSet<I>> toView(final MutableModelNode modelNode, final ModelRuleDescriptor ruleDescriptor, final boolean writable) {
        return new ModelView<ManagedSet<I>>() {

            private boolean closed;

            @Override
            public ModelType<ManagedSet<I>> getType() {
                return ManagedSetModelProjection.this.getType();
            }

            @Override
            public ManagedSet<I> getInstance() {
                return new DelegatingManagedSet();
            }

            @Override
            public void close() {
                closed = true;
            }

            class DelegatingManagedSet implements ManagedSet<I>, ManagedInstance {
                @Override
                public void create(final Action<? super I> action) {
                    if (!writable || closed) {
                        throw new ModelViewClosedException(getType(), ruleDescriptor);
                    }

                    String name = String.valueOf(modelNode.getLinkCount(elementType));
                    ModelPath path = modelNode.getPath().child(name);
                    modelNode.addLink(modelCreatorFactory.creator(ruleDescriptor, path, elementSchema, Collections.<ModelReference<?>>emptyList(), new BiAction<I, Inputs>() {
                        @Override
                        public void execute(I i, Inputs inputs) {
                            action.execute(i);
                        }
                    }));
                }

                private void ensureReadable() {
                    if (writable && !closed) {
                        throw new WriteOnlyModelViewException(getType(), ruleDescriptor);
                    }
                }

                @Override
                public int size() {
                    ensureReadable();
                    return modelNode.getLinkCount(elementType);
                }

                @Override
                public boolean isEmpty() {
                    ensureReadable();
                    return modelNode.getLinkCount(elementType) == 0;
                }

                @Override
                public boolean contains(Object o) {
                    ensureReadable();
                    throw new UnsupportedOperationException();
                }

                @Override
                public Iterator<I> iterator() {
                    ensureReadable();
                    List<I> values = new ArrayList<I>();
                    for (String name : modelNode.getLinkNames(elementType)) {
                        values.add(modelNode.getLink(name).asReadOnly(elementType, ruleDescriptor).getInstance());
                    }
                    return values.iterator();
                }

                @Override
                public Object[] toArray() {
                    ensureReadable();
                    throw new UnsupportedOperationException();
                }

                @Override
                public <T> T[] toArray(T[] a) {
                    ensureReadable();
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean add(I e) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean remove(Object o) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean containsAll(Collection<?> c) {
                    ensureReadable();
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean addAll(Collection<? extends I> c) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean retainAll(Collection<?> c) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean removeAll(Collection<?> c) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void clear() {
                    throw new UnsupportedOperationException();
                }

                // TODO - mix this in using decoration. Also validate closure parameter types, if declared
                public void create(Closure<?> closure) {
                    create(new ClosureBackedAction<I>(closure));
                }
            }
        };
    }
}
