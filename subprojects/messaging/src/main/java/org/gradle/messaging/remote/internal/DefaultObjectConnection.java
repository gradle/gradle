/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.remote.ObjectConnection;

public class DefaultObjectConnection implements ObjectConnection {
    private final AsyncStoppable stopControl;
    private final OutgoingMethodInvocationHandler outgoing;
    private final IncomingMethodInvocationHandler incoming;

    public DefaultObjectConnection(AsyncStoppable stopControl, OutgoingMethodInvocationHandler outgoing, IncomingMethodInvocationHandler incoming) {
        this.stopControl = stopControl;
        this.outgoing = outgoing;
        this.incoming = incoming;
    }

    public <T> void addIncoming(Class<T> type, T instance) {
        incoming.addIncoming(type, instance);
    }

    public void addIncoming(Class<?> type, Dispatch<? super MethodInvocation> dispatch) {
        incoming.addIncoming(type, dispatch);
    }

    public <T> T addOutgoing(Class<T> type) {
        return outgoing.addOutgoing(type);
    }

    public void requestStop() {
        stopControl.requestStop();
    }

    public void stop() {
        stopControl.stop();
    }
}
