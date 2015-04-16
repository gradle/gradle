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

import org.gradle.internal.BiActions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;

public class SpecializedCollectionBuilderProjection<P extends CollectionBuilder<E>, E, S extends P> extends TypeCompatibilityModelProjectionSupport<P> {

    private final ModelType<P> publicType;
    private final ModelType<E> elementType;
    private final Class<S> implementationClass;
    private final Instantiator instantiator;

    public SpecializedCollectionBuilderProjection(ModelType<P> publicType, ModelType<E> elementType, Class<S> implementationClass, Instantiator instantiator) {
        super(publicType, true, true);
        this.publicType = publicType;
        this.elementType = elementType;
        this.implementationClass = implementationClass;
        this.instantiator = instantiator;
    }

    @Override
    protected ModelView<P> toView(MutableModelNode modelNode, ModelRuleDescriptor ruleDescriptor, boolean writable) {
        S specializedCollectionBuilder = instantiator.newInstance(implementationClass, elementType, ruleDescriptor, modelNode, DefaultCollectionBuilder.createUsingParentNode(elementType, BiActions.doNothing()));
        return InstanceModelView.of(modelNode.getPath(), publicType, specializedCollectionBuilder);
    }

    @Override
    public <T> boolean canBeViewedAsWritable(ModelType<T> targetType) {
        return targetType.equals(publicType) && super.canBeViewedAsWritable(targetType);
    }

    @Override
    public <T> boolean canBeViewedAsReadOnly(ModelType<T> targetType) {
        return targetType.equals(publicType) && super.canBeViewedAsReadOnly(targetType);
    }
}
