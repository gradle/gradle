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

import org.apache.commons.lang.StringUtils;
import org.gradle.util.UncheckedException;

import java.net.URI;
import java.net.URISyntaxException;

public class HandshakeOutgoingConnector implements OutgoingConnector {
    private final OutgoingConnector connector;

    public HandshakeOutgoingConnector(OutgoingConnector connector) {
        this.connector = connector;
    }

    public Connection<Message> connect(URI destinationAddress) {
        if (!destinationAddress.getScheme().equals("channel")) {
            throw new IllegalArgumentException(String.format("Cannot create a connection to destination URI with unknown scheme: %s.",
                    destinationAddress));
        }
        URI connectionAddress = toConnectionAddress(destinationAddress);
        Connection<Message> connection = connector.connect(connectionAddress);
        try {
            connection.dispatch(new ConnectRequest(destinationAddress));
        } catch (Throwable e) {
            connection.stop();
            throw UncheckedException.asUncheckedException(e);
        }

        return connection;
    }

    private URI toConnectionAddress(URI destinationAddress) {
        String content = destinationAddress.getSchemeSpecificPart();
        URI connectionAddress;
        try {
            connectionAddress = new URI(StringUtils.substringBeforeLast(content, "!"));
        } catch (URISyntaxException e) {
            throw new UncheckedException(e);
        }
        return connectionAddress;
    }
}
