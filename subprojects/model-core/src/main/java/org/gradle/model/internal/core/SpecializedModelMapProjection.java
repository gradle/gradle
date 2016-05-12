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
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

import java.util.Collections;

/**
 * Should be used along with {@code PolymorphicModelMapProjection}.
 */
public class SpecializedModelMapProjection<P, E> implements ModelProjection {
    private final ModelType<P> publicType;
    private final ModelType<E> elementType;

    private final Class<? extends P> viewImpl;
    private final ChildNodeInitializerStrategyAccessor<? super E> creatorStrategyAccessor;

    public SpecializedModelMapProjection(ModelType<P> publicType, ModelType<E> elementType, Class<? extends P> viewImpl, ChildNodeInitializerStrategyAccessor<? super E> creatorStrategyAccessor) {
        this.publicType = publicType;
        this.elementType = elementType;
        this.viewImpl = viewImpl;
        this.creatorStrategyAccessor = creatorStrategyAccessor;
    }

    @Override
    public Iterable<String> getTypeDescriptions(MutableModelNode node) {
        return Collections.singleton(publicType.toString());
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asImmutable(ModelType<T> type, MutableModelNode node, @Nullable ModelRuleDescriptor ruleDescriptor) {
        if (canBeViewedAs(type)) {
            return Cast.uncheckedCast(toView(node, ruleDescriptor, false));
        } else {
            return null;
        }
    }

    @Nullable
    @Override
    public <T> ModelView<? extends T> asMutable(ModelType<T> type, MutableModelNode node, ModelRuleDescriptor ruleDescriptor) {
        if (canBeViewedAs(type)) {
            return Cast.uncheckedCast(toView(node, ruleDescriptor, true));
        } else {
            return null;
        }
    }

    private ModelView<P> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean mutable) {
        ChildNodeInitializerStrategy<? super E> creatorStrategy = creatorStrategyAccessor.getStrategy(modelNode);
        DefaultModelViewState state = new DefaultModelViewState(modelNode.getPath(), publicType, ruleDescriptor, mutable, !mutable);
        P instance = DirectInstantiator.instantiate(viewImpl, publicType, elementType, ruleDescriptor, modelNode, state, creatorStrategy);
        return InstanceModelView.of(modelNode.getPath(), publicType, instance, state.closer());
    }

    @Override
    public <T> boolean canBeViewedAs(ModelType<T> targetType) {
        return targetType.isAssignableFrom(publicType);
    }

    @Override
    public Optional<String> getValueDescription(MutableModelNode modelNodeInternal) {
        return Optional.absent();
    }
}
