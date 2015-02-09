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
import java.util.List;

public abstract class DomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> implements ModelProjection {

    protected final Class<M> baseItemType;
    private final ModelReference<NamedEntityInstantiator<M>> instantiatorModelReference;
    private final ModelType<M> baseItemModelType;
    private final ModelReference<? extends Collection<? super M>> storeReference;

    public DomainObjectContainerModelProjection(ModelType<M> baseItemType, ModelReference<NamedEntityInstantiator<M>> instantiatorModelReference, ModelReference<? extends Collection<? super M>> storeReference) {
        this.baseItemModelType = baseItemType;
        this.storeReference = storeReference;
        this.baseItemType = baseItemType.getConcreteClass();
        this.instantiatorModelReference = instantiatorModelReference;
    }

    public <T> ModelView<? extends T> asWritable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> inputs) {
        Class<? extends M> itemType = itemType(targetType);
        if (itemType != null) {
            return toView(ruleDescriptor, node, itemType);
        }
        return null;
    }

    protected <T, S extends M> ModelView<? extends T> toView(ModelRuleDescriptor sourceDescriptor, MutableModelNode node, Class<S> itemClass) {
        ModelType<S> itemType = ModelType.of(itemClass);
        CollectionBuilder<M> builder = new DefaultCollectionBuilder<M>(baseItemModelType, sourceDescriptor, node, DefaultCollectionBuilder.createAndStoreVia(instantiatorModelReference, storeReference));

        CollectionBuilder<S> subBuilder = builder.withType(itemClass);
        CollectionBuilderModelView<S> view = new CollectionBuilderModelView<S>(node.getPath(), DefaultCollectionBuilder.typeOf(itemType), subBuilder, sourceDescriptor);
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
            if (targetItemClass.isAssignableFrom(baseItemType)) {
                return baseItemType;
            }
            if (baseItemType.isAssignableFrom(targetItemClass)) {
                return targetItemClass.asSubclass(baseItemType);
            }
            return null;
        }
        if (targetClass.isAssignableFrom(CollectionBuilder.class)) {
            return baseItemType;
        }
        return null;
    }

    public <T> boolean canBeViewedAsReadOnly(ModelType<T> type) {
        return canBeViewedAsWritable(type);
    }

    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode modelNode, @Nullable ModelRuleDescriptor ruleDescriptor) {
        return asWritable(type, modelNode, ruleDescriptor, null);
    }

    public Iterable<String> getReadableTypeDescriptions() {
        return getWritableTypeDescriptions();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DomainObjectContainerModelProjection that = (DomainObjectContainerModelProjection) o;

        return baseItemType.equals(that.baseItemType) && instantiatorModelReference.equals(that.instantiatorModelReference);
    }

    @Override
    public int hashCode() {
        int result = baseItemType.hashCode();
        result = 31 * result + instantiatorModelReference.hashCode();
        return result;
    }
}
