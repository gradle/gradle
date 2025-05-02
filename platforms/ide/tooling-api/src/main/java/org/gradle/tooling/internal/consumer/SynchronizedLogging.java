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

import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressListener;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.internal.time.Clock;

/**
 * Provides logging services per thread.
 */
public class SynchronizedLogging implements LoggingProvider {
    private final ThreadLocal<ThreadLoggingServices> services = new ThreadLocal<ThreadLoggingServices>();
    private final Clock clock;
    private final BuildOperationIdFactory buildOperationIdFactory;

    public SynchronizedLogging(Clock clock, BuildOperationIdFactory buildOperationIdFactory) {
        this.clock = clock;
        this.buildOperationIdFactory = buildOperationIdFactory;
    }

    @Override
    public ListenerManager getListenerManager() {
        return services().listenerManager;
    }

    @Override
    public ProgressLoggerFactory getProgressLoggerFactory() {
        return services().progressLoggerFactory;
    }

    private ThreadLoggingServices services() {
        ThreadLoggingServices threadServices = services.get();
        if (threadServices == null) {
            DefaultListenerManager manager = new DefaultListenerManager(Global.class);
            DefaultProgressLoggerFactory progressLoggerFactory = new DefaultProgressLoggerFactory(manager.getBroadcaster(ProgressListener.class), clock, buildOperationIdFactory);
            threadServices = new ThreadLoggingServices(manager, progressLoggerFactory);
            services.set(threadServices);
        }
        return threadServices;
    }

    private static class ThreadLoggingServices {
        final ListenerManager listenerManager;
        final ProgressLoggerFactory progressLoggerFactory;

        private ThreadLoggingServices(ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory) {
            this.listenerManager = listenerManager;
            this.progressLoggerFactory = progressLoggerFactory;
        }
    }
}
