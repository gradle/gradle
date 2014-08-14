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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.CollectionBuilderModelView;
import org.gradle.model.collection.internal.DefaultCollectionBuilder;
import org.gradle.model.entity.internal.NamedEntityInstantiator;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;

public class PolymorphicDomainObjectContainerModelAdapter<I, C extends PolymorphicDomainObjectContainerInternal<I>> implements ModelAdapter {

    private final C container;
    private final ModelType<? super C> containerType;
    private final Class<I> itemType;

    public PolymorphicDomainObjectContainerModelAdapter(C container, ModelType<? super C> containerType, final Class<I> itemType) {
        this.container = container;
        this.containerType = containerType;
        this.itemType = itemType;
    }

    public <T> ModelView<? extends T> asWritable(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar) {
        ModelType<T> bindingType = binding.getReference().getType();
        if (bindingType.isAssignableFrom(containerType)) {
            @SuppressWarnings("unchecked") ModelView<? extends T> cast = (ModelView<? extends T>) InstanceModelView.of(containerType, container);
            return cast;
        } else if (bindingType.getRawClass().equals(CollectionBuilder.class)) {
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
        CollectionBuilder<S> builder = new DefaultCollectionBuilder<S>(binding.getPath(), new Instantiator<S>(itemType, container), sourceDescriptor, inputs, modelRuleRegistrar);
        ModelType<CollectionBuilder<S>> viewType = new ModelType.Builder<CollectionBuilder<S>>() {
        }.where(new ModelType.Parameter<S>() {
        }, ModelType.of(itemType)).build();
        CollectionBuilderModelView<S> view = new CollectionBuilderModelView<S>(viewType, builder, binding.getPath(), sourceDescriptor);
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
        return new Promise();
    }

    public static String getBuilderTypeDescriptionForCreatableTypes(Collection<? extends Class<?>> createableTypes) {
        StringBuilder sb = new StringBuilder(CollectionBuilder.class.getName());
        if (createableTypes.size() == 1) {
            @SuppressWarnings("ConstantConditions")
            String onlyType = Iterables.getFirst(createableTypes, null).getName();
            sb.append("<").append(onlyType).append(">");
        } else {
            sb.append("<T>; where T is one of [");
            Joiner.on(", ").appendTo(sb, CollectionUtils.sort(Iterables.transform(createableTypes, new Function<Class<?>, String>() {
                public String apply(Class<?> input) {
                    return input.getName();
                }
            })));
            sb.append("]");
        }
        return sb.toString();
    }

    public class Promise implements ModelPromise {
        private final ModelPromise basePromise = new SingleTypeModelPromise(containerType);

        public <T> boolean asWritable(ModelType<T> type) {
            return basePromise.asWritable(type) || isContainerView(type);
        }

        private <T> boolean isContainerView(ModelType<T> type) {
            if (type.getRawClass().equals(CollectionBuilder.class)) {
                ModelType<?> targetItemType = type.getTypeVariables().get(0);
                return targetItemType.getRawClass().isAssignableFrom(itemType) || itemType.isAssignableFrom(targetItemType.getRawClass());
            } else {
                return false;
            }
        }

        public <T> boolean asReadOnly(ModelType<T> type) {
            return basePromise.asReadOnly(type);
        }

        private String getBuilderTypeDescription() {
            return PolymorphicDomainObjectContainerModelAdapter.getBuilderTypeDescriptionForCreatableTypes(container.getCreateableTypes());
        }

        public Iterable<String> getWritableTypeDescriptions() {
            return Iterables.concat(
                    basePromise.getWritableTypeDescriptions(),
                    Collections.singleton(getBuilderTypeDescription())
            );
        }

        public Iterable<String> getReadableTypeDescriptions() {
            return basePromise.getReadableTypeDescriptions();
        }
    }
}
