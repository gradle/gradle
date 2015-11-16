/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.debug

import java.nio.ByteBuffer

class JDWPUtil {
    String host
    Integer port
    Socket client
    DataInputStream fromServer
    DataOutputStream toServer
    private boolean isConnected

    private static final String HANDSHAKE = "JDWP-Handshake"

    // See http://docs.oracle.com/javase/6/docs/platform/jpda/jdwp/jdwp-protocol.html for additional commands.
    // First element is the command set, the second is the command id
    private static final byte[] SUSPEND_COMMAND = [1, 8]
    private static final byte[] RESUME_COMMAND  = [1, 9]

    JDWPUtil(Integer port) {
        this.port = port
    }

    JDWPUtil(String host, Integer port) {
        this.host = host
        this.port = port
    }

    public JDWPUtil connect() {
        InetAddress hostAddress = InetAddress.getByName("127.0.0.1")
        client = new Socket(hostAddress, port)
        toServer = new DataOutputStream(client.getOutputStream())
        fromServer = new DataInputStream(client.getInputStream())
        performHandshake()
        isConnected = true
        return this
    }

    private void performHandshake() {
        toServer.writeBytes(HANDSHAKE)
        byte[] reply = new byte[HANDSHAKE.size()]
        fromServer.read(reply, 0, HANDSHAKE.size())
        if (new String(reply) != HANDSHAKE) {
            throw new IllegalStateException("Received response from handshake that was not handshake string: ${reply}")
        }
    }

    public JDWPUtil resume() {
        println "Sending resume command..."
        return sendCommand(RESUME_COMMAND)
    }

    public JDWPUtil suspend() {
        println "Sending suspend command..."
        return sendCommand(SUSPEND_COMMAND)
    }

    //TODO Does not currently handle commands that send data
    public JDWPUtil sendCommand(byte[] command) {
        verifyConnected()
        ByteBuffer message = ByteBuffer.wrap(new byte[11])
        message.putInt(0x0b)        //message length
        message.putInt(0x01)        //client id
        message.put(0x00 as byte)   //flags
        message.put(command[0])
        message.put(command[1])
        toServer.write(message.array())
        return this
    }

    private void verifyConnected() {
        if (! isConnected) {
            throw new IllegalStateException("Not connected to debug VM - call connect() first")
        }
    }
}
