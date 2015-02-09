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
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.NamedEntityInstantiator;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Collections;

public class StaticTypeDomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> extends DomainObjectContainerModelProjection<C, M> {

    private final ModelType<C> collectionType;

    public StaticTypeDomainObjectContainerModelProjection(ModelType<C> collectionType, ModelType<M> itemType, ModelReference<NamedEntityInstantiator<M>> instantiatorModelReference, ModelReference<? extends Collection<? super M>> storeReference) {
        super(itemType, instantiatorModelReference, storeReference);
        this.collectionType = collectionType;
    }

    @Override
    public Iterable<String> getWritableTypeDescriptions() {
        return Collections.singleton(getBuilderTypeDescriptionForCreatableTypes(Collections.singleton(baseItemType)));
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

        StaticTypeDomainObjectContainerModelProjection<?, ?> that = (StaticTypeDomainObjectContainerModelProjection<?, ?>) o;

        return collectionType.equals(that.collectionType);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + collectionType.hashCode();
        return result;
    }
}
