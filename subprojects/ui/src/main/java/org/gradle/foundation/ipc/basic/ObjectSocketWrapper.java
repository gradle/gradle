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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

/**
 * Wrapper around a java.net.Socket just to simplify usage.
 */
public class ObjectSocketWrapper {
    private Socket socket;
    private final Logger logger = Logging.getLogger(ObjectSocketWrapper.class);

    public ObjectSocketWrapper(Socket socket) {
        this.socket = socket;
    }

    public void setTimeout(int timeoutMilliseconds) {
        try {
            socket.setSoTimeout(timeoutMilliseconds);
        } catch (SocketException e) {
            logger.error("Failed to set timeout", e);
        }
    }

    public Object readObject() {

        ObjectInputStream reader = null;

        try {
            reader = new ObjectInputStream(socket.getInputStream());
        } catch (SocketException e) {
            if (!isIgnorableException(e)) {
                logger.error("Reading Object", e);
            }
            return null;
        } catch (Exception e) {
            logger.error("Reading Object", e);
            return null;
        }

        try {
            return reader.readObject();
        } catch (SocketException e) {
            //a connection reset is normal if the client quits, so don't dump out this exception and just return null.
            if (!isIgnorableException(e)) {
                logger.error("Reading Object", e);
            }
            return null;
        } catch (Exception e) {
            logger.error("Reading Object", e);
        }

        return null;
    }

    private boolean isIgnorableException(SocketException e) {
        //a connection reset is normal if the client quits.
        return "Connection reset".equalsIgnoreCase(e.getMessage());
    }

    /**
     * Synchronizing this prevents multiple threads from sending messages at the same time which corrupts the socket.
     */
    public synchronized boolean sendObject(Object object) {
        ObjectOutputStream writer = null;
        try {
            writer = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            logger.error("Exception when creating writer sending object: " + object, e);
            return false;
        }

        try {
            writer.reset();
            writer.flush();
            writer.writeObject(object);
            writer.flush();

            return true;
        } catch (Exception e) {
            logger.error("Exception when sending object: " + object, e);
            return false;
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Closing", e);
        }
    }
}