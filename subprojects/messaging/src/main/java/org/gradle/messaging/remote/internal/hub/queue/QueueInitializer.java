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

package org.gradle.messaging.remote.internal.hub.queue;

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.hub.protocol.ConnectionClosed;
import org.gradle.messaging.remote.internal.hub.protocol.ConnectionEstablished;
import org.gradle.messaging.remote.internal.hub.protocol.EndOfStream;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

import java.util.LinkedHashMap;
import java.util.Map;

public class QueueInitializer {
    private EndOfStream endOfStream;
    private Map<Connection<?>, ConnectionEstablished> queued = new LinkedHashMap<Connection<?>, ConnectionEstablished>();

    void onStatefulMessage(InterHubMessage message) {
        if (message instanceof ConnectionEstablished) {
            ConnectionEstablished connectionEstablished = (ConnectionEstablished) message;
            queued.put(connectionEstablished.getConnection(), connectionEstablished);
        } else if (message instanceof ConnectionClosed) {
            ConnectionClosed connectionClosed = (ConnectionClosed) message;
            queued.remove(connectionClosed.getConnection());
        } else if (message instanceof EndOfStream) {
            queued.clear();
            endOfStream = (EndOfStream) message;
        } else {
            throw new UnsupportedOperationException(String.format("Received unexpected stateful message: %s", message));
        }
    }

    void onQueueAdded(Dispatch<InterHubMessage> queue) {
        for (ConnectionEstablished connectionEstablished : queued.values()) {
            queue.dispatch(connectionEstablished);
        }
        if (endOfStream != null) {
            queue.dispatch(endOfStream);
        }
    }
}
