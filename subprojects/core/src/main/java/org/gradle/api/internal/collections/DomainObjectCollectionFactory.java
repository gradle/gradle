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

import groovy.lang.Closure;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.CompositeDomainObjectSet;

public interface DomainObjectCollectionFactory {
    /**
     * Creates a {@link NamedDomainObjectContainer} for managing named objects of the specified type.
     *
     * Note that this method is here because {@link org.gradle.api.Project#container(Class)} cannot decorate the elements because of backwards compatibility.
     */
    <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainerUndecorated(Class<T> elementType);

    /**
     * Creates a {@link NamedDomainObjectContainer} for managing named objects of the specified type.
     */
    <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> elementType);

    /**
     * Creates a {@link NamedDomainObjectContainer} for managing named objects of the specified type created with the given factory.
     */
    <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> elementType, NamedDomainObjectFactory<T> factory);

    /**
     * Creates a {@link NamedDomainObjectContainer} for managing named objects of the specified type. The given closure is used to create object instances. The name of the instance to be created is passed as a parameter to the closure.
     */
    <T> NamedDomainObjectContainer<T> newNamedDomainObjectContainer(Class<T> type, Closure factoryClosure);

    /**
     * Creates a {@link DomainObjectCollection} for managing objects of the specified type.
     */
    <T> DomainObjectCollection<T> newDomainObjectCollection(Class<T> elementType);

    /**
     * Creates a {@link DomainObjectSet} for managing objects of the specified type.
     */
    <T> DomainObjectSet<T> newDomainObjectSet(Class<T> elementType);

    <T> NamedDomainObjectSet<T> newNamedDomainObjectSet(Class<T> elementType);

    <T> NamedDomainObjectList<T> newNamedDomainObjectList(Class<T> elementType);

    /**
     * Creates a {@link CompositeDomainObjectSet} for managing a collection of {@link DomainObjectCollection} of the specified type.
     */
    <T> CompositeDomainObjectSet<T> newDomainObjectSet(Class<T> elementType, DomainObjectCollection<? extends T> collection);

    <T> ExtensiblePolymorphicDomainObjectContainer<T> newPolymorphicDomainObjectContainer(Class<T> elementType);
}
