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

package org.gradle.model.internal.core;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;

import static org.gradle.internal.Cast.uncheckedCast;

public class ModelMapModelProjection<I> implements ModelProjection {
    private static final ModelType<ManagedInstance> MANAGED_INSTANCE_TYPE = ModelType.of(ManagedInstance.class);

    public static <T> ModelProjection unmanaged(ModelType<T> itemType, ChildNodeInitializerStrategyAccessor<? super T> creatorStrategyAccessor) {
        return new ModelMapModelProjection<T>(ModelTypes.modelMap(itemType), itemType, false, creatorStrategyAccessor);
    }

    public static <T> ModelProjection unmanaged(Class<T> itemType, ChildNodeInitializerStrategyAccessor<? super T> creatorStrategyAccessor) {
        return unmanaged(ModelType.of(itemType), creatorStrategyAccessor);
    }

    public static <T> ModelProjection managed(ModelType<?> publicType, ModelType<T> itemType, ChildNodeInitializerStrategyAccessor<? super T> creatorStrategyAccessor) {
        return new ModelMapModelProjection<T>(publicType, itemType, true, creatorStrategyAccessor);
    }

    private final ModelType<?> publicType;
    private final ModelType<I> baseItemModelType;
    private final ChildNodeInitializerStrategyAccessor<? super I> creatorStrategyAccessor;
    private final boolean managed;

    private ModelMapModelProjection(ModelType<?> publicType, ModelType<I> baseItemModelType, boolean managed, ChildNodeInitializerStrategyAccessor<? super I> creatorStrategyAccessor) {
        this.publicType = publicType;
        this.baseItemModelType = baseItemModelType;
        this.managed = managed;
        this.creatorStrategyAccessor = creatorStrategyAccessor;
    }

    private Collection<? extends Class<?>> getCreatableTypes() {
        return Collections.singleton(baseItemModelType.getConcreteClass());
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
                @Override
                public String apply(Class<?> input) {
                    return input.getName();
                }
            })));
            sb.append("]");
        }
        return sb.toString();
    }

    private ModelType<? extends I> itemType(ModelType<?> targetType) {
        Class<?> targetClass = targetType.getRawClass();
        if (targetClass.equals(ModelMap.class)) {
            ModelType<?> targetItemClass = targetType.getTypeVariables().get(0);
            if (targetItemClass.isAssignableFrom(baseItemModelType)) {
                return baseItemModelType;
            }
            if (baseItemModelType.isAssignableFrom(targetItemClass)) {
                return targetItemClass.asSubtype(baseItemModelType);
            }
            return null;
        }
        if (targetClass.isAssignableFrom(ModelMap.class)) {
            return baseItemModelType;
        }
        return null;
    }

    @Override
    public <T> boolean canBeViewedAs(ModelType<T> targetType) {
        return itemType(targetType) != null || targetType.equals(MANAGED_INSTANCE_TYPE);
    }

    @Override
    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, MutableModelNode modelNode, @Nullable ModelRuleDescriptor ruleDescriptor) {
        return doAs(type, modelNode, ruleDescriptor, false);
    }

    @Override
    public <T> ModelView<? extends T> asMutable(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        return doAs(targetType, node, ruleDescriptor, true);
    }

    @Nullable
    private <T> ModelView<? extends T> doAs(ModelType<T> targetType, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, boolean mutable) {
        ModelType<? extends I> itemType = itemType(targetType);
        if (itemType != null) {
            return uncheckedCast(toView(targetType, ruleDescriptor, node, itemType, mutable, !managed || !mutable));
        }
        return null;
    }

    private <T, S extends I> ModelView<ModelMap<S>> toView(ModelType<T> targetType, ModelRuleDescriptor sourceDescriptor, MutableModelNode node, ModelType<S> itemType, boolean mutable, boolean canReadChildren) {
        ChildNodeInitializerStrategy<? super I> creatorStrategy = creatorStrategyAccessor.getStrategy(node);
        DefaultModelViewState state = new DefaultModelViewState(node.getPath(), targetType, sourceDescriptor, mutable, canReadChildren);
        NodeBackedModelMap<I> builder = new NodeBackedModelMap<I>(publicType, baseItemModelType, sourceDescriptor, node, state, creatorStrategy);

        return InstanceModelView.of(
            node.getPath(),
            ModelTypes.modelMap(itemType),
            builder.withType(itemType),
            state.closer()
        );
    }

    @Override
    public Iterable<String> getTypeDescriptions(MutableModelNode node) {
        final Collection<? extends Class<?>> creatableTypes = getCreatableTypes();
        return Collections.singleton(getContainerTypeDescription(ModelMap.class, creatableTypes));
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }
}
