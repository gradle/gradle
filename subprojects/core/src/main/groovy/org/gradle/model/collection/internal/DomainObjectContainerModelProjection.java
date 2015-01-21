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

package org.gradle.model.collection.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.gradle.api.Nullable;
import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.util.Collection;

public abstract class DomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> implements ModelProjection {

    protected final Class<M> itemType;

    public DomainObjectContainerModelProjection(Class<M> itemType) {
        this.itemType = itemType;
    }

    protected abstract C getContainer(MutableModelNode node);

    public <T> ModelView<? extends T> asWritable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, Inputs inputs,
                                                 ModelRuleSourceApplicator modelRuleSourceApplicator, ModelRegistrar modelRegistrar, PluginClassApplicator pluginClassApplicator) {
        Class<? extends M> itemType = itemType(targetType);
        if (itemType != null) {
            return toView(ruleDescriptor, node, itemType, getContainer(node), modelRuleSourceApplicator, modelRegistrar, pluginClassApplicator);
        }
        return null;
    }

    protected <T, S extends M> ModelView<? extends T> toView(ModelRuleDescriptor sourceDescriptor, MutableModelNode node, Class<S> itemClass, C container,
                                                             ModelRuleSourceApplicator modelRuleSourceApplicator, ModelRegistrar modelRegistrar, PluginClassApplicator pluginClassApplicator) {
        ModelType<S> itemType = ModelType.of(itemClass);
        ModelType<CollectionBuilder<S>> viewType = new ModelType.Builder<CollectionBuilder<S>>() {
        }.where(new ModelType.Parameter<S>() {
        }, itemType).build();
        CollectionBuilder<S> builder = new DefaultCollectionBuilder<S>(itemType, container.getEntityInstantiator(), container, sourceDescriptor, node, modelRuleSourceApplicator, modelRegistrar,
                pluginClassApplicator);
        CollectionBuilderModelView<S> view = new CollectionBuilderModelView<S>(viewType, builder, sourceDescriptor);
        @SuppressWarnings("unchecked") ModelView<T> cast = (ModelView<T>) view;
        return cast;
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

    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return itemType(targetType) != null;
    }

    protected Class<? extends M> itemType(ModelType<?> targetType) {
        Class<?> targetClass = targetType.getRawClass();
        if (targetClass.equals(CollectionBuilder.class)) {
            Class<?> targetItemClass = targetType.getTypeVariables().get(0).getRawClass();
            if (targetItemClass.isAssignableFrom(itemType)) {
                return itemType;
            }
            if (itemType.isAssignableFrom(targetItemClass)) {
                return targetItemClass.asSubclass(itemType);
            }
            return null;
        }
        if (targetClass.isAssignableFrom(CollectionBuilder.class)) {
            return itemType;
        }
        return null;
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> type) {
        return canBeViewedAsWritable(type);
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode modelNode, @Nullable ModelRuleDescriptor ruleDescriptor, ModelRuleSourceApplicator modelRuleSourceApplicator,
                                                 ModelRegistrar modelRegistrar, PluginClassApplicator pluginClassApplicator) {
        return asWritable(type, modelNode, ruleDescriptor, null, modelRuleSourceApplicator, modelRegistrar, pluginClassApplicator);
    }

    public Iterable<String> getReadableTypeDescriptions() {
        return getWritableTypeDescriptions();
    }

}
