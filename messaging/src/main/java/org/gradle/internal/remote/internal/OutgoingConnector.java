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
package org.gradle.internal.remote.internal;

import org.gradle.internal.remote.Address;

public interface OutgoingConnector {
    /**
     * Creates a connection to the given address. Blocks until the connection with the peer has been established.
     *
     * @throws ConnectException when there is nothing listening on the remote address.
     */
    ConnectCompletion connect(Address destinationAddress) throws ConnectException;
}
