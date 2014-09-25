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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.PolymorphicDomainObjectContainer;
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.collection.CollectionBuilderModelView;
import org.gradle.model.collection.internal.DefaultCollectionBuilder;
import org.gradle.model.entity.internal.NamedEntityInstantiator;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;

public class PolymorphicDomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> implements ModelProjection<C> {

    private final C container;
    private final Class<M> itemType;

    public PolymorphicDomainObjectContainerModelProjection(C container, final Class<M> itemType) {
        this.container = container;
        this.itemType = itemType;
    }

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        if (targetType.getRawClass().equals(CollectionBuilder.class)) {
            ModelType<?> targetItemType = targetType.getTypeVariables().get(0);
            return targetItemType.getRawClass().isAssignableFrom(itemType) || itemType.isAssignableFrom(targetItemType.getRawClass());
        } else {
            return false;
        }
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> type) {
        return false;
    }

    public <T> ModelView<? extends T> asWritable(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar, C instance) {
        ModelType<T> targetType = binding.getReference().getType();
        if (canBeViewedAsWritable(targetType)) {
            ModelType<?> targetItemType = targetType.getTypeVariables().get(0);
            if (targetItemType.getRawClass().isAssignableFrom(itemType)) { // item type is super of base
                return toView(binding, sourceDescriptor, inputs, modelRuleRegistrar, itemType);
            } else { // item type is sub type
                Class<? extends M> subType = targetItemType.getRawClass().asSubclass(itemType);
                return toView(binding, sourceDescriptor, inputs, modelRuleRegistrar, subType);
            }
        } else {
            return null;
        }
    }

    private <T, S extends M> ModelView<? extends T> toView(ModelBinding<T> binding, ModelRuleDescriptor sourceDescriptor, Inputs inputs, ModelRuleRegistrar modelRuleRegistrar, Class<S> itemType) {
        CollectionBuilder<S> builder = new DefaultCollectionBuilder<S>(binding.getPath(), new Instantiator<S>(itemType, container), sourceDescriptor, inputs, modelRuleRegistrar);
        ModelType<CollectionBuilder<S>> viewType = new ModelType.Builder<CollectionBuilder<S>>() {
        }.where(new ModelType.Parameter<S>() {
        }, ModelType.of(itemType)).build();
        CollectionBuilderModelView<S> view = new CollectionBuilderModelView<S>(viewType, builder, binding.getPath(), sourceDescriptor);
        @SuppressWarnings("unchecked") ModelView<T> cast = (ModelView<T>) view;
        return cast;
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, C instance) {
        return null;
    }

    public Iterable<String> getWritableTypeDescriptions() {
        return Collections.singleton(getBuilderTypeDescriptionForCreatableTypes(container.getCreateableTypes()));
    }

    public Iterable<String> getReadableTypeDescriptions() {
        return Collections.emptySet();
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
}
