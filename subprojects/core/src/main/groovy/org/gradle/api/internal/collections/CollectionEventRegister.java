/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ActionBroadcast;

public class CollectionEventRegister<T> {

    private final ActionBroadcast<T> addActions;
    private final ActionBroadcast<T> removeActions;

    public CollectionEventRegister() {
        this(new ActionBroadcast<T>(), new ActionBroadcast<T>());
    }

    public CollectionEventRegister(ActionBroadcast<T> addActions, ActionBroadcast<T> removeActions) {
        this.addActions = addActions;
        this.removeActions = removeActions;
    }

    public Action<T> getAddAction() {
        return addActions;
    }   
    
    public Action<T> getRemoveAction() {
        return removeActions;
    }
    
    public Action<? super T> registerAddAction(Action<? super T> addAction) {
        this.addActions.add(addAction);
        return addAction;
    }

    public Action<? super T> registerRemoveAction(Action<? super T> removeAction) {
        this.removeActions.add(removeAction);
        return removeAction;
    }

    public <S extends T> CollectionEventRegister<S> filtered(CollectionFilter<S> filter) {
        return new FilteringCollectionEventRegister<S>(filter, (ActionBroadcast)addActions, (ActionBroadcast)removeActions);
    }

    private static class FilteringCollectionEventRegister<S> extends CollectionEventRegister<S> {
        private final CollectionFilter<? super S> filter;

        public FilteringCollectionEventRegister(CollectionFilter<? super S> filter, ActionBroadcast<S> addActions, ActionBroadcast<S> removeActions) {
            super(addActions, removeActions);
            this.filter = filter;
        }

        public Action<? super S> registerAddAction(Action<? super S> addAction) {
            return super.registerAddAction(Actions.<S>filter(addAction, filter));
        }

        public Action<? super S> registerRemoveAction(Action<? super S> removeAction) {
            return super.registerRemoveAction(Actions.<S>filter(removeAction, filter));
        }

        public <K extends S> CollectionEventRegister<K> filtered(CollectionFilter<K> filter) {
            return super.filtered(new CollectionFilter<K>(filter.getType(), Specs.<K>and(filter, this.filter)));
        }
    }

}
