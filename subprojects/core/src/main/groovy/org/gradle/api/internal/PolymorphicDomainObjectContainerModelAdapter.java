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

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.model.collection.NamedItemCollectionBuilder;
import org.gradle.model.collection.internal.DefaultNamedItemCollectionBuilder;
import org.gradle.model.entity.internal.NamedEntityInstantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor;

public class PolymorphicDomainObjectContainerModelAdapter<I, C extends PolymorphicDomainObjectContainer<I>> implements ModelAdapter {

    private final C container;
    private final ModelType<C> containerType;
    private final ModelType<I> itemType;
    private final ModelType<NamedItemCollectionBuilder<I>> collectionBuilderModelType;

    public PolymorphicDomainObjectContainerModelAdapter(C container, ModelType<C> containerType, ModelType<I> itemType) {
        this.container = container;
        this.containerType = containerType;
        this.itemType = itemType;
        this.collectionBuilderModelType = ModelType.of(new TypeToken<NamedItemCollectionBuilder<I>>() {
        }.where(new TypeParameter<I>() {
        }, itemType.getToken()));
    }

    public <T> ModelView<? extends T> asWritable(ModelReference<T> reference, ModelRuleSourceDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar) {
        if (reference.getType().isAssignableFrom(containerType)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(containerType, container);
            return cast;
        } else if (reference.getType().isAssignableFrom(collectionBuilderModelType)) {
            DefaultNamedItemCollectionBuilder<I> builder = new DefaultNamedItemCollectionBuilder<I>(reference.getPath(), new Instantiator(), sourceDescriptor, inputs, modelRuleRegistrar);
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(collectionBuilderModelType, builder);
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
