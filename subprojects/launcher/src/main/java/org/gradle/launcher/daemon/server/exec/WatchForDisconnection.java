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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.launcher.daemon.server.api.DaemonCommandAction;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener;

public class WatchForDisconnection implements DaemonCommandAction {
    private static final Logger LOGGER = Logging.getLogger(WatchForDisconnection.class);

    private final ListenerBroadcast<DaemonExpirationListener> listenerBroadcast;

    public static final String EXPIRATION_REASON = "client disconnected";

    public WatchForDisconnection(ListenerManager listenerManager) {
        this.listenerBroadcast = listenerManager.createAnonymousBroadcaster(DaemonExpirationListener.class);
    }

    public void execute(final DaemonCommandExecution execution) {
        // Watch for the client disconnecting before we call stop()
        execution.getConnection().onDisconnect(new Runnable() {
            public void run() {
                LOGGER.warn("thread {}: client disconnection detected, canceling the build", Thread.currentThread().getId());
                execution.getDaemonStateControl().requestCancel();
            }
        });

        try {
            execution.proceed();
        } finally {
            // Remove the handler
            execution.getConnection().onDisconnect(null);
        }
    }

}
