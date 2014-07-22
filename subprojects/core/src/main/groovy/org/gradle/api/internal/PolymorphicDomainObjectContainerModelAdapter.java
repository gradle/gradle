/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.model.collection.NamedItemCollectionBuilder;
import org.gradle.model.collection.NamedItemCollectionBuilderModelView;
import org.gradle.model.collection.internal.DefaultNamedItemCollectionBuilder;
import org.gradle.model.entity.internal.NamedEntityInstantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

public class PolymorphicDomainObjectContainerModelAdapter<I, C extends PolymorphicDomainObjectContainer<I>> implements ModelAdapter {

    private final C container;
    private final ModelType<C> containerType;
    private final ModelType<I> itemType;
    private final ModelType<NamedItemCollectionBuilder<I>> collectionBuilderModelType;

    public PolymorphicDomainObjectContainerModelAdapter(C container, ModelType<C> containerType, final ModelType<I> itemType) {
        this.container = container;
        this.containerType = containerType;
        this.itemType = itemType;
        this.collectionBuilderModelType = new ModelType.Builder<NamedItemCollectionBuilder<I>>() {
        }.where(new ModelType.Parameter<I>() {}, itemType).build();
    }

    public <T> ModelView<? extends T> asWritable(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar) {
        if (binding.getReference().getType().isAssignableFrom(containerType)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(containerType, container);
            return cast;
        } else if (binding.getReference().getType().isAssignableFrom(collectionBuilderModelType)) {
            NamedItemCollectionBuilder<I> builder = new DefaultNamedItemCollectionBuilder<I>(binding.getPath(), new Instantiator(), sourceDescriptor, inputs, modelRuleRegistrar);
            NamedItemCollectionBuilderModelView<I> view = new NamedItemCollectionBuilderModelView<I>(collectionBuilderModelType, builder, binding.getPath(), sourceDescriptor);
            @SuppressWarnings("unchecked") ModelView<T> cast = (ModelView<T>) view;
            return cast;
        } else {
            return null;
        }
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type) {
        if (type.isAssignableFrom(containerType)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(containerType, container);
            return cast;
        } else {
            return null;
        }
    }

    class Instantiator implements NamedEntityInstantiator<I> {
        public ModelType<I> getType() {
            return itemType;
        }

        public I create(String name) {
            return container.create(name);
        }

        public <S extends I> S create(String name, Class<S> type) {
            return container.create(name, type);
        }
    }

    public ModelPromise asPromise() {
        return new ModelPromise() {
            public <T> boolean asWritable(ModelType<T> type) {
                return type.isAssignableFrom(containerType) || type.isAssignableFrom(collectionBuilderModelType);
            }

            public <T> boolean asReadOnly(ModelType<T> type) {
                return type.isAssignableFrom(containerType);
            }
        };
    }

}
