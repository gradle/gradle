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

package org.gradle.util.exec;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author Tom Eyckmans
 */
public abstract class ExecHandleNotifier implements Runnable {
    private final ExecHandle execHandle;
    private final List<ExecHandleListener> listeners;

    public ExecHandleNotifier(final ExecHandle execHandle) {
        this.execHandle = execHandle;
        this.listeners = new ArrayList<ExecHandleListener>(execHandle.getListeners());
    }

    public final void run() {
        final Iterator<ExecHandleListener> listenersIt = listeners.iterator();
        boolean keepNotifing = true;
        while ( keepNotifing && listenersIt.hasNext() ) {
            final ExecHandleListener listener = listenersIt.next();
            try {
                keepNotifing = notifyListener(execHandle, listener);
            }
            catch ( Throwable t ) {
                // ignore
                // TODO listenerNotificationFailureHandle.listenerFailed(execHandle, listener)
                // TODO may be interesting for e.g. when an log writer fails remove it and add an email sender
                // TODO but for now ignore it
            }
        }
    }

    protected abstract boolean notifyListener(ExecHandle execHandle, ExecHandleListener listener);
}
