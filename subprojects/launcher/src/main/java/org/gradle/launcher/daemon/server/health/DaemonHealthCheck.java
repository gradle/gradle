/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server.health;

import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationListener;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.DO_NOT_EXPIRE;

public class DaemonHealthCheck {

    private final DaemonExpirationStrategy strategy;
    private final ListenerBroadcast<DaemonExpirationListener> listenerBroadcast;

    public DaemonHealthCheck(DaemonExpirationStrategy strategy, ListenerManager listenerManager) {
        this.strategy = strategy;
        this.listenerBroadcast = listenerManager.createAnonymousBroadcaster(DaemonExpirationListener.class);
    }

    public void executeHealthCheck() {
        DaemonExpirationResult result = strategy.checkExpiration();
        if (result.getStatus() != DO_NOT_EXPIRE) {
            listenerBroadcast.getSource().onExpirationEvent(result);
        }
    }
}
