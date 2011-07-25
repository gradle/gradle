/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.DomainObjectCollection;

import org.apache.commons.collections.collection.CompositeCollection;

/**
 * A domain object collection that presents a combined view of one or more collections.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectCollection<T> extends DefaultDomainObjectCollection<T> {
    public CompositeDomainObjectCollection(Class<T> type) {
        super(type, new CompositeCollection());
    }

    public CompositeDomainObjectCollection(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        this(type);
        for (DomainObjectCollection<? extends T> collection : collections) {
            addCollection(collection);
        }
    }

    protected CompositeCollection getStore() {
        return (CompositeCollection)super.getStore();
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        getStore().addComposited(collection);
        collection.all(getEventRegister().getAddAction());
        collection.whenObjectRemoved(getEventRegister().getRemoveAction());
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        getStore().removeComposited(collection);
        for (T item : collection) {
            getEventRegister().getRemoveAction().execute(item);
        }
    }

}