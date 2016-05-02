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

import java.util.ArrayList;
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

    protected void dispatch(MethodInvocation invocation, Iterator<? extends Dispatch<MethodInvocation>> handlers) {
        List<Throwable> failures = new ArrayList<Throwable>();
        while (handlers.hasNext()) {
            Dispatch<MethodInvocation> handler = handlers.next();
            try {
                handler.dispatch(invocation);
            } catch (UncheckedException e) {
                failures.add(e.getCause());
            } catch (Throwable t) {
                failures.add(t);
            }
        }
        if (failures.size() == 1 && failures.get(0) instanceof RuntimeException) {
            throw (RuntimeException) failures.get(0);
        }
        if (!failures.isEmpty()) {
            throw new ListenerNotificationException(getErrorMessage(), failures);
        }
    }
}
