/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal;


import com.google.common.collect.Sets;
import org.gradle.api.Action;

import java.util.LinkedHashSet;

import static org.gradle.internal.Actions.DO_NOTHING;

/**
 * An {@link Action} implementation which has set semantics, but avoids creating
 * an internal set when possible, especially if the set is empty or there's a
 * single action in the set.
 *
 * This set also INTENTIONNALY ignores {@link Actions#doNothing()} actions as to
 * avoid growing for something that would never do anything.
 *
 * This is done for memory and CPU efficiency, as seen
 * in traces. This class is NOT thread-safe. Actions are executed in order of
 * insertion.
 *
 * @param <T> the type of the subject of the action
 */
public class FastActionSet<T> implements Action<T> {
    private Action<T> singleAction;
    private LinkedHashSet<Action<T>> multipleActions;

    public void add(Action<T> action) {
        if (action instanceof FastActionSet) {
            addOtherSet((FastActionSet<T>) action);
            return;
        }
        if (action == DO_NOTHING) {
            return;
        }
        if (singleAction == null && multipleActions==null) {
            // first element in the set
            this.singleAction = action;
            return;
        }
        if (multipleActions != null) {
            // already a composite set
            multipleActions.add(action);
            return;
        }
        if (singleAction == action || singleAction.equals(action)) {
            // de-duplicate
            return;
        }
        // at least 2 elements
        multipleActions = Sets.newLinkedHashSet();
        multipleActions.add(singleAction);
        multipleActions.add(action);
        singleAction = null;
    }

    protected void addOtherSet(FastActionSet<T> action) {
        FastActionSet<T> sas = action;
        if (sas.singleAction!=null) {
            add(sas.singleAction);
        } else if (sas.multipleActions!=null) {
            for (Action<T> tAction : sas.multipleActions) {
                add(tAction);
            }
        }
    }

    @Override
    public void execute(T t) {
        // if both singleAction and multipleActions are null then the set is empty
        if (singleAction != null) {
            singleAction.execute(t);
        } else if (multipleActions != null) {
            for (Action<T> action : multipleActions) {
                action.execute(t);
            }
        }
    }

    boolean isEmpty() {
        return singleAction == null && multipleActions == null;
    }

    int size() {
        if (singleAction == null) {
            if (multipleActions == null) {
                return 0;
            }
            return multipleActions.size();
        }
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FastActionSet<?> that = (FastActionSet<?>) o;

        if (singleAction != null ? !singleAction.equals(that.singleAction) : that.singleAction != null) {
            return false;
        }
        return multipleActions != null ? multipleActions.equals(that.multipleActions) : that.multipleActions == null;
    }

    @Override
    public int hashCode() {
        int result = singleAction != null ? singleAction.hashCode() : 0;
        result = 31 * result + (multipleActions != null ? multipleActions.hashCode() : 0);
        return result;
    }
}
