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

import org.apache.commons.collections.collection.CompositeCollection;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;

import java.util.*;

/**
 * A domain object collection that presents a combined view of one or more collections.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectSet<T> extends DefaultDomainObjectSet<T> {

    private Spec<T> uniqueSpec = new ItemIsUniqueInCompositeSpec();
    private Spec<T> notInSpec = new ItemNotInCompositeSpec();

    public CompositeDomainObjectSet(Class<T> type) {
        //noinspection unchecked
        super(type, new CompositeCollection());
    }

    public CompositeDomainObjectSet(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        this(type);
        for (DomainObjectCollection<? extends T> collection : collections) {
            addCollection(collection);
        }
    }

    public class ItemIsUniqueInCompositeSpec implements Spec<T> {
        public boolean isSatisfiedBy(T element) {
            int matches = 0;
            for (Object collection : getStore().getCollections()) {
                if (((Collection)collection).contains(element)) {
                    if (++matches > 1) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public class ItemNotInCompositeSpec implements Spec<T> {
        public boolean isSatisfiedBy(T element) {
            return !getStore().contains(element);
        }
    }

    @SuppressWarnings("unchecked")
    protected CompositeCollection getStore() {
        return (CompositeCollection)super.getStore();
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.<T>filter(action, uniqueSpec));
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.<T>filter(action, notInSpec));
    }
    
    public CompositeDomainObjectSet<T> addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().getCollections().contains(collection)) {
            getStore().addComposited(collection);
            collection.all(getEventRegister().getAddAction());
            collection.whenObjectRemoved(getEventRegister().getRemoveAction());
        }
        return this;
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        getStore().removeComposited(collection);
        Action<? super T> action = getEventRegister().getRemoveAction();
        for (T item : collection) {
            action.execute(item);
        }
    }

    public Iterator<T> iterator() {
        //noinspection unchecked
        return new LinkedHashSet<T>(getStore()).iterator();
    }

    public int size() {
        //noinspection unchecked
        return new LinkedHashSet<T>(getStore()).size();
    }
    
    public void all(Action<? super T> action) {
        whenObjectAdded(action);

        for (T t : this) {
            action.execute(t);
        }
    }

    /**
     * Only allows adding beforeChange actions before any collections are composited.
     * It can be improved in future but for now it is sufficient.
     */
    public CompositeDomainObjectSet<T> beforeChange(Runnable action) {
        if(!getStore().getCollections().isEmpty()) {
            throw new IllegalStateException("beforeChange action can only be added before any collections are composited.");
        }
        whenObjectAdded(Actions.toAction(action));
        return this;
    }
}