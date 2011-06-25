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

import org.gradle.messaging.remote.Address;
import org.gradle.messaging.remote.internal.protocol.ConnectRequest;
import org.gradle.util.UncheckedException;

public class HandshakeOutgoingConnector implements OutgoingConnector<Message> {
    private final OutgoingConnector<Message> connector;

    public HandshakeOutgoingConnector(OutgoingConnector<Message> connector) {
        this.connector = connector;
    }

    public Connection<Message> connect(Address destinationAddress) {
        if (!(destinationAddress instanceof CompositeAddress)) {
            throw new IllegalArgumentException(String.format("Cannot create a connection to address of unknown type: %s.", destinationAddress));
        }
        CompositeAddress compositeAddress = (CompositeAddress) destinationAddress;
        Address connectionAddress = compositeAddress.getAddress();
        Connection<Message> connection = connector.connect(connectionAddress);
        try {
            connection.dispatch(new ConnectRequest(destinationAddress));
        } catch (Throwable e) {
            connection.stop();
            throw UncheckedException.asUncheckedException(e);
        }

        return connection;
    }
}
