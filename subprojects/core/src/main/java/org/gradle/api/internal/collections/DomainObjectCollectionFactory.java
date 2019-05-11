/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.collections;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;

public interface DomainObjectCollectionFactory {
    /**
     * Creates a {@link NamedDomainObjectContainer} for managing named objects of the specified type.
     */
    <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> elementType);

    /**
     * Creates a {@link NamedDomainObjectContainer} for managing named objects of the specified type created with the given factory.
     */
    <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> elementType, NamedDomainObjectFactory<T> factory);

    /**
     * Creates a {@link DomainObjectSet} for managing objects of the specified type.
     */
    <T> DomainObjectSet<T> newDomainObjectSet(Class<T> elementType);
}
