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

import org.gradle.api.internal.DynamicTypesNamedEntityInstantiator;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public class DynamicTypesCollectionBuilderProjection<T> extends CollectionBuilderModelProjection<T> {

    public DynamicTypesCollectionBuilderProjection(ModelType<T> baseItemType) {
        super(baseItemType);
    }

    @Override
    protected BiFunction<? extends ModelCreators.Builder, MutableModelNode, ModelReference<? extends T>> getCreatorFunction() {
        return DefaultCollectionBuilder.createUsingParentNode(baseItemModelType);
    }

    @Override
    protected Collection<? extends Class<?>> getCreatableTypes(MutableModelNode node) {
        ModelType<DynamicTypesNamedEntityInstantiator<T>> instantiatorType = new ModelType.Builder<DynamicTypesNamedEntityInstantiator<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, baseItemModelType).build();

        DynamicTypesNamedEntityInstantiator<T> instantiator = node.getPrivateData(instantiatorType);
        return instantiator.getCreatableTypes();
    }
}
