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

package org.gradle.model.collection.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.api.Nullable;
import org.gradle.model.ModelMap;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public class ModelMapModelProjection<I> implements ModelProjection {

    @SuppressWarnings("deprecation")
    private final static Set<Class<?>> SUPPORTED_CONTAINER_TYPES = ImmutableSet.<Class<?>>of(ModelMap.class, CollectionBuilder.class);
    private static final ModelType<ManagedInstance> MANAGED_INSTANCE_TYPE = ModelType.of(ManagedInstance.class);

    public static <T> ModelProjection unmanaged(ModelType<T> itemType, ChildNodeInitializerStrategyAccessor<? super T> creatorStrategyAccessor) {
        return new ModelMapModelProjection<T>(itemType, false, creatorStrategyAccessor);
    }

    public static <T> ModelProjection unmanaged(Class<T> itemType, ChildNodeInitializerStrategyAccessor<? super T> creatorStrategyAccessor) {
        return unmanaged(ModelType.of(itemType), creatorStrategyAccessor);
    }

    public static <T> ModelProjection managed(ModelType<T> itemType, ChildNodeInitializerStrategyAccessor<? super T> creatorStrategyAccessor) {
        return new ModelMapModelProjection<T>(itemType, true, creatorStrategyAccessor);
    }

    protected final Class<I> baseItemType;
    protected final ModelType<I> baseItemModelType;
    private final ChildNodeInitializerStrategyAccessor<? super I> creatorStrategyAccessor;
    private final boolean managed;

    private ModelMapModelProjection(ModelType<I> baseItemModelType, boolean managed, ChildNodeInitializerStrategyAccessor<? super I> creatorStrategyAccessor) {
        this.baseItemModelType = baseItemModelType;
        this.managed = managed;
        this.baseItemType = baseItemModelType.getConcreteClass();
        this.creatorStrategyAccessor = creatorStrategyAccessor;
    }

    private Collection<? extends Class<?>> getCreatableTypes() {
        return Collections.singleton(baseItemType);
    }

    private String getContainerTypeDescription(Class<?> containerType, Collection<? extends Class<?>> creatableTypes) {
        StringBuilder sb = new StringBuilder(containerType.getName());
        if (creatableTypes.size() == 1) {
            @SuppressWarnings("ConstantConditions")
            String onlyType = Iterables.getFirst(creatableTypes, null).getName();
            sb.append("<").append(onlyType).append(">");
        } else {
            sb.append("<T>; where T is one of [");
            Joiner.on(", ").appendTo(sb, CollectionUtils.sort(Iterables.transform(creatableTypes, new Function<Class<?>, String>() {
                public String apply(Class<?> input) {
                    return input.getName();
                }
            })));
            sb.append("]");
        }
        return sb.toString();
    }

    public Iterable<String> getReadableTypeDescriptions(MutableModelNode node) {
        return getWritableTypeDescriptions(node);
    }

    private Class<? extends I> itemType(ModelType<?> targetType) {
        Class<?> targetClass = targetType.getRawClass();
        if (SUPPORTED_CONTAINER_TYPES.contains(targetClass)) {
            Class<?> targetItemClass = targetType.getTypeVariables().get(0).getRawClass();
            if (targetItemClass.isAssignableFrom(baseItemType)) {
                return baseItemType;
            }
            if (baseItemType.isAssignableFrom(targetItemClass)) {
                return targetItemClass.asSubclass(baseItemType);
            }
            return null;
        }
        if (targetClass.isAssignableFrom(ModelMap.class)) {
            return baseItemType;
        }
        return null;
    }

    public <T> boolean canBeViewedAsMutable(ModelType<T> targetType) {
        return itemType(targetType) != null || targetType.equals(MANAGED_INSTANCE_TYPE);
    }

    public <T> boolean canBeViewedAsImmutable(ModelType<T> type) {
        return canBeViewedAsMutable(type);
    }

    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, MutableModelNode modelNode, @Nullable ModelRuleDescriptor ruleDescriptor) {
        return doAs(type, modelNode, ruleDescriptor, false);
    }

    public <T> ModelView<? extends T> asMutable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        return doAs(targetType, node, ruleDescriptor, true);
    }

    @Nullable
    private <T> ModelView<? extends T> doAs(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, boolean mutable) {
        Class<? extends I> itemType = itemType(targetType);
        if (itemType != null) {
            return uncheckedCast(toView(targetType, ruleDescriptor, node, itemType, mutable, !managed || !mutable));
        }
        return null;
    }

    private <T, S extends I> ModelView<ModelMap<S>> toView(ModelType<T> targetType, ModelRuleDescriptor sourceDescriptor, MutableModelNode node, Class<S> itemClass, boolean mutable, boolean canReadChildren) {
        ChildNodeInitializerStrategy<? super I> creatorStrategy = creatorStrategyAccessor.getStrategy(node);
        DefaultModelViewState state = new DefaultModelViewState(targetType, sourceDescriptor, mutable, canReadChildren);
        ModelType<S> itemType = ModelType.of(itemClass);
        ModelMap<I> builder = new NodeBackedModelMap<I>(baseItemModelType, sourceDescriptor, node, false, state, creatorStrategy);

        return InstanceModelView.of(
            node.getPath(),
            ModelTypes.modelMap(itemType),
            builder.withType(itemClass),
            state.closer()
        );
    }

    @Override
    public Iterable<String> getWritableTypeDescriptions(final MutableModelNode node) {
        final Collection<? extends Class<?>> creatableTypes = getCreatableTypes();
        return Iterables.transform(SUPPORTED_CONTAINER_TYPES, new Function<Class<?>, String>() {
            public String apply(Class<?> containerType) {
                return getContainerTypeDescription(containerType, creatableTypes);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelMapModelProjection<?> that = (ModelMapModelProjection<?>) o;

        return baseItemType.equals(that.baseItemType) && baseItemModelType.equals(that.baseItemModelType);
    }

    @Override
    public int hashCode() {
        int result = baseItemType.hashCode();
        result = 31 * result + baseItemModelType.hashCode();
        return result;
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }
}
