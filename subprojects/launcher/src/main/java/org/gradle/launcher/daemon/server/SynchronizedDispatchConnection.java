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

package org.gradle.launcher.daemon.server;

import org.gradle.internal.concurrent.Synchronizer;
import org.gradle.messaging.remote.internal.Connection;

/**
 * Connection decorator that synchronizes dispatching.
 * <p>
 * by Szczepan Faber, created at: 2/27/12
 */
public class SynchronizedDispatchConnection<T> implements Connection<T> {
    
    private final Synchronizer sync = new Synchronizer();
    private final Connection<T> delegate;

    public SynchronizedDispatchConnection(Connection<T> delegate) {
        this.delegate = delegate;
    }
    
    public void requestStop() {
        delegate.requestStop();
    }

    public void dispatch(final T message) {
        sync.synchronize(new Runnable() {
            public void run() {
                delegate.dispatch(message);
            }
        });
    }

    public T receive() {
        //in case one wants to synchronize this method,
        //bear in mind that it is blocking so it cannot share the same lock as others
        return delegate.receive();
    }

    public void stop() {
        delegate.stop();
    }

    public String toString() {
        return delegate.toString();
    }
}