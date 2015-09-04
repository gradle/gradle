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

package org.gradle.model.internal.core;

import com.google.common.base.Optional;
import org.gradle.api.Nullable;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;
import java.util.List;

/**
 * Should be used along with {@code PolymorphicModelMapProjection}.
 */
public class SpecializedModelMapProjection<P extends ModelMap<E>, E> implements ModelProjection {

    private final ModelType<P> publicType;
    private final ModelType<E> elementType;

    private final Class<? extends P> viewImpl;
    private final ChildNodeInitializerStrategy<E> creatorStrategy;

    public SpecializedModelMapProjection(ModelType<P> publicType, ModelType<E> elementType, Class<? extends P> viewImpl, ChildNodeInitializerStrategy<E> creatorStrategy) {
        this.publicType = publicType;
        this.elementType = elementType;
        this.viewImpl = viewImpl;
        this.creatorStrategy = creatorStrategy;
    }

    @Override
    public Iterable<String> getReadableTypeDescriptions(MutableModelNode node) {
        return getWritableTypeDescriptions(node);
    }

    @Override
    public Iterable<String> getWritableTypeDescriptions(MutableModelNode node) {
        return Collections.singleton(publicType.toString());
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asReadOnly(ModelType<T> type, MutableModelNode node, @Nullable ModelRuleDescriptor ruleDescriptor) {
        if (canBeViewedAsReadOnly(type)) {
            return Cast.uncheckedCast(toView(node, ruleDescriptor, false));
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asWritable(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor, List<ModelView<?>> implicitDependencies) {
        if (canBeViewedAsWritable(type)) {
            return Cast.uncheckedCast(toView(node, ruleDescriptor, true));
        } else {
            return null;
        }
    }

    private ModelView<P> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean mutable) {
        DefaultModelViewState state = new DefaultModelViewState(publicType, ruleDescriptor, mutable, true);
        String description = publicType.getSimpleName() + " '" + modelNode.getPath() + "'";
        ModelMap<E> rawView = new NodeBackedModelMap<E>(description, elementType, ruleDescriptor, modelNode, false, state, creatorStrategy);
        P instance = DirectInstantiator.instantiate(viewImpl, rawView);
        return InstanceModelView.of(modelNode.getPath(), publicType, instance, state.closer());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        SpecializedModelMapProjection<?, ?> that = (SpecializedModelMapProjection<?, ?>) o;
        return publicType.equals(that.publicType) && viewImpl.equals(that.viewImpl);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + publicType.hashCode();
        result = 31 * result + viewImpl.hashCode();
        return result;
    }

    @Override
    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return targetType.equals(publicType) || targetType.equals(ModelType.of(Object.class));
    }

    @Override
    public <T> boolean canBeViewedAsReadOnly(ModelType<T> targetType) {
        return canBeViewedAsWritable(targetType);
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }
}
