/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.messaging.remote.internal.hub;

import org.gradle.internal.CompositeStoppable;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.dispatch.ReflectionDispatch;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

public class MessageHubBackedObjectConnection implements ObjectConnection {
    private final MessageHub hub;
    private final Connection<InterHubMessage> connection;

    public MessageHubBackedObjectConnection(MessageHub hub, Connection<InterHubMessage> connection) {
        this.hub = hub;
        this.connection = connection;
        hub.addConnection(connection);
    }

    public void addIncoming(Class<?> type, Dispatch<? super MethodInvocation> dispatch) {
        hub.addHandler(type.getName(), dispatch);
    }

    public <T> void addIncoming(Class<T> type, T instance) {
        hub.addHandler(type.getName(), new ReflectionDispatch(instance));
    }

    public <T> T addOutgoing(Class<T> type) {
        ProxyDispatchAdapter<T> adapter = new ProxyDispatchAdapter<T>(hub.getOutgoing(type.getName(), MethodInvocation.class), type, ThreadSafe.class);
        return adapter.getSource();
    }

    public void requestStop() {
        hub.requestStop();
    }

    public void stop() {
        CompositeStoppable.stoppable(hub, connection).stop();
    }
}
