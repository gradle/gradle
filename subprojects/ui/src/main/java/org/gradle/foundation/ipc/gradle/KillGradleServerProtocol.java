/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.foundation.ipc.gradle;

import org.gradle.foundation.ipc.basic.MessageObject;
import org.gradle.foundation.ipc.basic.Server;

/**
 * This protocol is used by a client that launches its own server. See KillGradleClientProtocol.
 */
public class KillGradleServerProtocol implements Server.Protocol<Server> {
    private Server server;

    public void initialize(Server server) {
        this.server = server;
    }

    public void connectionAccepted() {

    }

    public boolean continueConnection() {
        return true;
    }

    public void messageReceived(MessageObject message) {
        if (ProtocolConstants.KILL.equals(message.getMessageType())) {
            killProcess();
        }
    }

    private void killProcess() {
        System.exit(-1);
    }

    public void clientCommunicationStopped() {
        killProcess();
    }

    public void readFailureOccurred() {

    }
}
