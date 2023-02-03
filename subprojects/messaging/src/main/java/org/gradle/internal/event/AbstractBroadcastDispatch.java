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

package org.gradle.internal.event;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.operations.BuildOperationInvocationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractBroadcastDispatch<T> implements Dispatch<MethodInvocation> {
    protected final Class<T> type;

    public AbstractBroadcastDispatch(Class<T> type) {
        this.type = type;
    }

    private String getErrorMessage() {
        String typeDescription = type.getSimpleName().replaceAll("(\\p{Upper})", " $1").trim().toLowerCase();
        return "Failed to notify " + typeDescription + ".";
    }

    protected void dispatch(MethodInvocation invocation, Dispatch<MethodInvocation> handler) {
        try {
            handler.dispatch(invocation);
        } catch (UncheckedException e) {
            throw new ListenerNotificationException(invocation, getErrorMessage(), Collections.singletonList(e.getCause()));
        } catch (BuildOperationInvocationException e) {
            throw new ListenerNotificationException(invocation, getErrorMessage(), Collections.singletonList(e.getCause()));
        } catch (RuntimeException t) {
            throw t;
        } catch (Throwable t) {
            throw new ListenerNotificationException(invocation, getErrorMessage(), Collections.singletonList(t));
        }
    }

    /**
     * Dispatch an invocation to the given dispatchers.
     * <p>
     * This method will try to dispatch the invocation in an efficient way based on the number of dispatchers.
     * </p>
     */
    protected void dispatch(MethodInvocation invocation, List<? extends Dispatch<MethodInvocation>> dispatchers) {
        switch (dispatchers.size()) {
            case 0:
                break;
            case 1:
                dispatch(invocation, dispatchers.get(0));
                break;
            default:
                dispatch(invocation, dispatchers.iterator());
                break;
        }
    }

    /**
     * Dispatch an invocation to multiple handlers.
     */
    private void dispatch(MethodInvocation invocation, Iterator<? extends Dispatch<MethodInvocation>> handlers) {
        // Defer creation of failures list, assume dispatch will succeed
        List<Throwable> failures = null;
        while (handlers.hasNext()) {
            Dispatch<MethodInvocation> handler = handlers.next();
            try {
                handler.dispatch(invocation);
            } catch (ListenerNotificationException e) {
                if (failures == null) {
                    failures = new ArrayList<Throwable>();
                }
                if (e.getEvent() == invocation) {
                    failures.addAll(e.getCauses());
                } else {
                    failures.add(e);
                }
            } catch (UncheckedException e) {
                if (failures == null) {
                    failures = new ArrayList<Throwable>();
                }
                failures.add(e.getCause());
            } catch (Throwable t) {
                if (failures == null) {
                    failures = new ArrayList<Throwable>();
                }
                failures.add(t);
            }
        }
        if (failures == null) {
            return;
        }
        if (failures.size() == 1 && failures.get(0) instanceof RuntimeException) {
            throw (RuntimeException) failures.get(0);
        }
        throw new ListenerNotificationException(invocation, getErrorMessage(), failures);
    }
}
