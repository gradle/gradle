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

import org.gradle.api.Action;
import org.gradle.internal.remote.ConnectionAcceptor;

public interface IncomingConnector {
    /**
     * Starts listening for incoming connections. Assigns an arbitrary address for the endpoint.
     *
     * @param action the action to execute on incoming connection. The supplied action is not required to be thread-safe.
     * @param allowRemote If true, only allow connections from remote machines. If false, allow only from the local machine.
     * @return the address of the endpoint which the connector is listening on.
     */
    ConnectionAcceptor accept(Action<ConnectCompletion> action, boolean allowRemote);
}
