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
package org.gradle.foundation.ipc.basic;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;

/**
 * A holder for a message that is sent over a socket.
 */
public class MessageObject implements Serializable {
    private String messageType;
    private String message;
    private Serializable data;

    public MessageObject(String messageType, String message, Serializable data) {
        this.messageType = messageType;
        this.message = message;
        this.data = data;
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        out.writeObject(messageType);
        out.writeObject(message);
        out.writeObject(data);
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        messageType = (String) in.readObject();
        message = (String) in.readObject();
        data = (Serializable) in.readObject();
    }

    private void readObjectNoData() throws ObjectStreamException {

    }

    @Override
    public String toString() {
        return "Type='" + messageType + '\'' + " Message='" + message + '\'' + " data=" + data;
    }

    public String getMessageType() {
        return messageType;
    }

    public String getMessage() {
        return message;
    }

    public Serializable getData() {
        return data;
    }
}