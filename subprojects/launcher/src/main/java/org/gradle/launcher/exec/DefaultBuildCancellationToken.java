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

package org.gradle.launcher.exec;

import org.gradle.initialization.BuildCancellationToken;

import java.util.ArrayList;
import java.util.List;

public class DefaultBuildCancellationToken implements BuildCancellationToken {
    private boolean cancelled;
    private List<Runnable> callbacks = new ArrayList<Runnable>();

    public synchronized boolean isCancellationRequested() {
        return cancelled;
    }

    public synchronized boolean addCallback(Runnable cancellationHandler) {
        callbacks.add(cancellationHandler);
        if (cancelled) {
            cancellationHandler.run();
        }
        return cancelled;
    }

    public boolean canBeCancelled() {
        return true;
    }

    public synchronized void doCancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;
        for (Runnable runnable : callbacks) {
            runnable.run();
        }
    }
}
