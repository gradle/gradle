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

package org.gradle.tooling.internal.consumer;

import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.util.TrueTimeProvider;

/**
 * Thread safe logging provider that needs to be initialized before use.
 * <p>
 * by Szczepan Faber, created at: 12/14/11
 */
public class SynchronizedLogging implements LoggingProvider {

    private final ThreadLocal<ListenerManager> listenerManager = new ThreadLocal<ListenerManager>();
    private final ThreadLocal<DefaultProgressLoggerFactory> progressLoggerFactory = new ThreadLocal<DefaultProgressLoggerFactory>();

    //TODO SF refactor / cover
    public synchronized ListenerManager getListenerManager() {
        assertInitialized();
        return listenerManager.get();
    }

    public synchronized DefaultProgressLoggerFactory getProgressLoggerFactory() {
        assertInitialized();
        return progressLoggerFactory.get();
    }

    public synchronized void init() {
        DefaultListenerManager manager = new DefaultListenerManager();
        listenerManager.set(manager);
        progressLoggerFactory.set(new DefaultProgressLoggerFactory(manager.getBroadcaster(ProgressListener.class), new TrueTimeProvider()));
    }

    private void assertInitialized() {
        if (listenerManager.get() == null) {
            throw new IllegalStateException("Internal problem. Logging has not yet been initialized for this thread.");
        }
    }
}