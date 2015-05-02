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

import org.gradle.api.Action;
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

    private final ModelAdapter delegateAdapter;
    private final Class<? extends P> viewImpl;

    public SpecializedModelMapProjection(ModelType<P> publicType, ModelType<E> elementType, ModelAdapter delegateAdapter, Class<? extends P> viewImpl) {
        this.publicType = publicType;
        this.elementType = elementType;
        this.delegateAdapter = delegateAdapter;
        this.viewImpl = viewImpl;
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

    private ModelView<P> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
        final ModelView<? extends ModelMap<E>> rawView;
        ModelType<ModelMap<E>> type = DefaultModelMap.modelMapTypeOf(elementType);
        if (writable) {
            rawView = delegateAdapter.asWritable(type, modelNode, ruleDescriptor, Collections.<ModelView<?>>emptyList());
        } else {
            rawView = delegateAdapter.asReadOnly(type, modelNode, ruleDescriptor);
        }

        if (rawView == null) {
            throw new IllegalStateException("delegateAdapter " + delegateAdapter + " returned null for type " + type);
        }

        P instance = DirectInstantiator.instantiate(viewImpl, publicType.getSimpleName() + " '" + modelNode.getPath() + "'", rawView.getInstance());
        return InstanceModelView.of(modelNode.getPath(), publicType, instance, new Action<P>() {
            @Override
            public void execute(P es) {
                rawView.close();
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
        if (!super.equals(o)) {
            return false;
        }

        SpecializedModelMapProjection<?, ?> that = (SpecializedModelMapProjection<?, ?>) o;
        if (!publicType.equals(that.publicType)) {
            return false;
        }
        if (!delegateAdapter.equals(that.delegateAdapter)) {
            return false;
        }
        return viewImpl.equals(that.viewImpl);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + publicType.hashCode();
        result = 31 * result + delegateAdapter.hashCode();
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
}
