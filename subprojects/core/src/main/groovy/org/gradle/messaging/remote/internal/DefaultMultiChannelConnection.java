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

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.Address;

class DefaultMultiChannelConnection implements MultiChannelConnection<Object> {
    private final Address sourceAddress;
    private final Address destinationAddress;
    private final MessageHub hub;

    DefaultMultiChannelConnection(MessageHub hub, Connection<Message> connection, Address sourceAddress, Address destinationAddress) {
        this.hub = hub;
        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;

        hub.addConnection(connection);
    }

    public Address getLocalAddress() {
        if (sourceAddress == null) {
            throw new UnsupportedOperationException();
        }
        return sourceAddress;
    }

    public Address getRemoteAddress() {
        if (destinationAddress == null) {
            throw new UnsupportedOperationException();
        }
        return destinationAddress;
    }

    public void addIncomingChannel(String channelKey, final Dispatch<Object> dispatch) {
        hub.addIncoming(channelKey, dispatch);
    }

    public Dispatch<Object> addOutgoingChannel(String channelKey) {
        return hub.addUnicastOutgoing(channelKey);
    }

    public void requestStop() {
        hub.requestStop();
    }

    public void stop() {
        requestStop();
        hub.stop();
    }
}
