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

package org.gradle.initialization;

import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DefaultBuildCancellationToken implements BuildCancellationToken {

    private final Object lock = new Object();
    private boolean cancelled;
    private List<Runnable> callbacks = new LinkedList<Runnable>();

    public boolean isCancellationRequested() {
        synchronized (lock) {
            return cancelled;
        }
    }

    public boolean addCallback(Runnable cancellationHandler) {
        boolean returnValue;
        synchronized (lock) {
            returnValue = cancelled;
            if (!cancelled) {
                callbacks.add(cancellationHandler);
            }
        }
        if (returnValue) {
            cancellationHandler.run();
        }
        return returnValue;
    }

    public void removeCallback(Runnable cancellationHandler) {
        synchronized (lock) {
            callbacks.remove(cancellationHandler);
        }
    }

    public void cancel() {
        List<Runnable> toCall = new ArrayList<Runnable>();
        synchronized (lock) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            toCall.addAll(callbacks);
            callbacks.clear();
        }

        List<Throwable> failures = new ArrayList<Throwable>();
        for (Runnable callback : toCall) {
            try {
                callback.run();
            } catch (Throwable ex) {
                failures.add(ex);
            }
        }
        if (!failures.isEmpty()) {
            throw new DefaultMultiCauseException("Failed to run cancellation actions.", failures);
        }
    }
}
