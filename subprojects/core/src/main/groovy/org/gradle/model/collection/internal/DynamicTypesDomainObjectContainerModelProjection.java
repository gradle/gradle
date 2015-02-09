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

/**
 * When reporting its writable views, considers what factories have been registered.
 *
 * @see StaticTypeDomainObjectContainerModelProjection
 */
public class DynamicTypesDomainObjectContainerModelProjection<C extends PolymorphicDomainObjectContainerInternal<M>, M> extends DomainObjectContainerModelProjection<C, M> {

    private final C container;

    public DynamicTypesDomainObjectContainerModelProjection(C container, ModelType<M> itemType, ModelReference<NamedEntityInstantiator<M>> instantiatorModelReference, ModelReference<? extends Collection<? super M>> storeReference) {
        super(itemType, instantiatorModelReference, storeReference);
        this.container = container;
    }

    public Iterable<String> getWritableTypeDescriptions() {
        return Collections.singleton(getBuilderTypeDescriptionForCreatableTypes(container.getCreateableTypes()));
    }

}
