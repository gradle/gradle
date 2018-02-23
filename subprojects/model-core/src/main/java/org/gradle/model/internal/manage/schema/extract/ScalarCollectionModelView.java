/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.internal.Cast;
import org.gradle.model.internal.core.DefaultModelViewState;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public abstract class ScalarCollectionModelView<E, C extends Collection<E>> implements ModelView<C> {
    protected final ModelPath path;
    protected final ModelType<E> elementType;
    protected final ModelType<C> type;
    protected final MutableModelNode modelNode;
    protected final boolean overwritable;
    protected final DefaultModelViewState state;

    public ScalarCollectionModelView(ModelPath path, ModelType<C> type, ModelType<E> elementType, MutableModelNode modelNode, ModelRuleDescriptor descriptor, boolean overwritable, boolean mutable) {
        this.path = path;
        this.type = type;
        this.elementType = elementType;
        this.modelNode = modelNode;
        this.overwritable = overwritable;
        this.state = new DefaultModelViewState(path, type, descriptor, mutable, false);
    }

    @Override
    public ModelPath getPath() {
        return path;
    }

    @Override
    public ModelType<C> getType() {
        return type;
    }

    private Collection<E> getBackingValue() {
        return Cast.uncheckedCast(modelNode.getPrivateData(Collection.class));
    }

    private void setBackingValue(Collection<E> values) {
        if (state.isCanMutate()) {
            modelNode.setPrivateData(Collection.class, values);
        }
    }

    protected abstract C toMutationSafe(Collection<?> backingCollection);

    @Override
    public C getInstance() {
        Collection<?> delegate = getBackingValue();
        if (delegate == null) {
            if (overwritable || state.isCanMutate()) {
                // if the collection is a read-only property, it must be initialized first (it will never be null)
                // if the collection is *not* read-only, then we will initialize it only if the collection is the
                // subject of a rule, that is to say that it can be mutated. This may look strange, if a read-write
                // collection is null to initialize it to an empty list, but this is how the specs define reaw-write
                // collections of scalar types.
                delegate = initializeEmptyCollection();
            }
        }
        return delegate == null ? null : toMutationSafe(delegate);
    }

    private Collection<E> initializeEmptyCollection() {
        Collection<E> delegate = initialValue();
        setBackingValue(delegate);
        return delegate;
    }

    @Override
    public void close() {
        C instance = getInstance();
        state.closer().execute(instance);
    }

    protected abstract C initialValue();

    @SuppressWarnings("unchecked")
    public Object setValue(Object values) {
        state.assertCanMutate();
        if (values == null) {
            setBackingValue(null);
            return null;
        } else {
            Collection<E> objects = initializeEmptyCollection();
            objects.addAll((Collection<? extends E>) values);
            return objects;
        }
    }
}
