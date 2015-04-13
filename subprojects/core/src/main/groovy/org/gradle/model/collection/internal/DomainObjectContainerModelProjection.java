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

import org.gradle.api.internal.PolymorphicDomainObjectContainerInternal;
import org.gradle.internal.util.BiFunction;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;

public abstract class DomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> extends CollectionBuilderModelProjection<M> {

    private final ModelReference<NamedEntityInstantiator<M>> instantiatorModelReference;
    private final ModelReference<? extends Collection<? super M>> storeReference;

    public DomainObjectContainerModelProjection(ModelType<M> baseItemType, ModelReference<NamedEntityInstantiator<M>> instantiatorModelReference, ModelReference<? extends Collection<? super M>> storeReference) {
        super(baseItemType);
        this.storeReference = storeReference;
        this.instantiatorModelReference = instantiatorModelReference;
    }

    @Override
    protected BiFunction<? extends ModelCreators.Builder, MutableModelNode, ModelReference<? extends M>> getCreatorFunction() {
        return DefaultCollectionBuilder.createAndStoreVia(instantiatorModelReference, storeReference);
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
