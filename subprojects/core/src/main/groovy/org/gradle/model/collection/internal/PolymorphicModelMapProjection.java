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

import org.gradle.api.internal.PolymorphicNamedEntityInstantiator;
import org.gradle.internal.BiAction;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public class PolymorphicModelMapProjection<T> extends ModelMapModelProjection<T> {

    private final BiAction<? super MutableModelNode, ? super T> initializer;

    public PolymorphicModelMapProjection(ModelType<T> baseItemType, BiAction<? super MutableModelNode, ? super T> initializer) {
        super(baseItemType);
        this.initializer = initializer;
    }

    @Override
    protected BiFunction<? extends ModelCreators.Builder, MutableModelNode, ModelReference<? extends T>> getCreatorFunction() {
        return DefaultModelMap.createUsingParentNode(baseItemModelType, initializer);
    }

    @Override
    protected Collection<? extends Class<?>> getCreatableTypes(MutableModelNode node) {
        ModelType<PolymorphicNamedEntityInstantiator<T>> instantiatorType = new ModelType.Builder<PolymorphicNamedEntityInstantiator<T>>() {
        }.where(new ModelType.Parameter<T>() {
        }, baseItemModelType).build();

        PolymorphicNamedEntityInstantiator<T> instantiator = node.getPrivateData(instantiatorType);
        return instantiator.getCreatableTypes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        //noinspection EqualsBetweenInconvertibleTypes
        if (!super.equals(o)) {
            return false;
        }

        PolymorphicModelMapProjection<?> that = (PolymorphicModelMapProjection<?>) o;

        return initializer.equals(that.initializer);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + initializer.hashCode();
        return result;
    }
}
