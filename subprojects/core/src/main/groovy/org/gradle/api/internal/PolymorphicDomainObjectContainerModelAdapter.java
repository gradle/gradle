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
    private final Class<I> itemType;

    public PolymorphicDomainObjectContainerModelAdapter(C container, ModelType<C> containerType, final Class<I> itemType) {
        this.container = container;
        this.containerType = containerType;
        this.itemType = itemType;
    }

    public <T> ModelView<? extends T> asWritable(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar) {
        ModelType<T> bindingType = binding.getReference().getType();
        if (bindingType.isAssignableFrom(containerType)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(containerType, container);
            return cast;
        } else if (bindingType.getRawClass().equals(NamedItemCollectionBuilder.class)) {
            ModelType<?> bindingItemType = bindingType.getTypeVariables().get(0);
            if (bindingItemType.getRawClass().isAssignableFrom(itemType)) { // item type is super of base
                return toView(binding, sourceDescriptor, inputs, modelRuleRegistrar, itemType);
            } else if (itemType.isAssignableFrom(bindingItemType.getRawClass())) { // item type is sub type
                Class<? extends I> subType = bindingItemType.getRawClass().asSubclass(itemType);
                return toSubModelView(binding, sourceDescriptor, inputs, modelRuleRegistrar, subType);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private <T, S extends I> ModelView<? extends T> toSubModelView(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar, Class<S> subType) {
        return toView(binding, sourceDescriptor, inputs, modelRuleRegistrar, subType);
    }

    private <T, S extends I> ModelView<? extends T> toView(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar, Class<S> itemType) {
        NamedItemCollectionBuilder<S> builder = new DefaultNamedItemCollectionBuilder<S>(binding.getPath(), new Instantiator<S>(itemType, container), sourceDescriptor, inputs, modelRuleRegistrar);
        ModelType<NamedItemCollectionBuilder<S>> viewType = new ModelType.Builder<NamedItemCollectionBuilder<S>>() {
        }.where(new ModelType.Parameter<S>() {
        }, ModelType.of(itemType)).build();
        NamedItemCollectionBuilderModelView<S> view = new NamedItemCollectionBuilderModelView<S>(viewType, builder, binding.getPath(), sourceDescriptor);
        @SuppressWarnings("unchecked") ModelView<T> cast = (ModelView<T>) view;
        return cast;
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type) {
        if (type.isAssignableFrom(containerType)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(containerType, container);
            return cast;
        } else {
            return null;
        }
    }

    static class Instantiator<I> implements NamedEntityInstantiator<I> {

        private final Class<I> defaultType;
        private final ModelType<I> itemType;
        private final PolymorphicDomainObjectContainer<? super I> container;

        Instantiator(Class<I> defaultType, PolymorphicDomainObjectContainer<? super I> container) {
            this.defaultType = defaultType;
            this.itemType = ModelType.of(defaultType);
            this.container = container;
        }

        public ModelType<I> getType() {
            return itemType;
        }

        public I create(String name) {
            return container.create(name, defaultType);
        }

        public <S extends I> S create(String name, Class<S> type) {
            return container.create(name, type);
        }
    }

    public ModelPromise asPromise() {
        return new ModelPromise() {
            public <T> boolean asWritable(ModelType<T> type) {
                return type.isAssignableFrom(containerType) || isContainerView(type);
            }

            private <T> boolean isContainerView(ModelType<T> type) {
                if (type.getRawClass().equals(NamedItemCollectionBuilder.class)) {
                    ModelType<?> targetItemType = type.getTypeVariables().get(0);
                    return targetItemType.getRawClass().isAssignableFrom(itemType) || itemType.isAssignableFrom(targetItemType.getRawClass());
                } else {
                    return false;
                }
            }

            public <T> boolean asReadOnly(ModelType<T> type) {
                return type.isAssignableFrom(containerType);
            }
        };
    }

}
