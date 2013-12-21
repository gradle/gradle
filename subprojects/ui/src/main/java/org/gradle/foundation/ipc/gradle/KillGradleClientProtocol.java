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

import org.gradle.foundation.ipc.basic.ClientProcess;

import java.net.Socket;

/**
 * This protocol is used by the process that launches gradle (the launching server - but in this case its the client) so that it can tell gradle to kill itself. This is used to cancel gradle
 * execution. There is no other clean way to do it but kill it. All this does is send a 'kill' message.
 */
public class KillGradleClientProtocol implements ClientProcess.Protocol {
    private ClientProcess client;

    public void initialize(ClientProcess client) {
        this.client = client;
    }

    public boolean serverConnected(Socket clientSocket) {
        return true;
    }

    public boolean continueConnection() {
        return true;
    }

    public void sendKillMessage() {
        client.sendMessage(ProtocolConstants.KILL, ProtocolConstants.KILL);
    }
}