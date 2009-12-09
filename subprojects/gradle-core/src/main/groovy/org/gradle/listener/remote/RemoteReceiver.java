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
package org.gradle.listener.remote;

import org.gradle.listener.ListenerBroadcast;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class RemoteReceiver  {
    private final ListenerBroadcast<?> broadcaster;
    private final ServerSocket serverSocket;
    private final Thread receiverThread;
    private final ExceptionListener exceptionListener;

    public RemoteReceiver(ListenerBroadcast<?> broadcaster) throws IOException {
        this(broadcaster, null);
    }

    public RemoteReceiver(ListenerBroadcast<?> broadcaster, ExceptionListener exceptionListener) throws IOException {
        if (broadcaster == null) {
            throw new NullPointerException();
        }
        
        this.broadcaster = broadcaster;
        this.exceptionListener = exceptionListener;
        serverSocket = new ServerSocket(0);
        receiverThread = new Thread(new Receiver(), "Remote Receiver Thread");
        receiverThread.start();
    }

    public int getBoundPort() {
        return serverSocket.getLocalPort();
    }

    public void close() throws IOException {
        receiverThread.interrupt();
        serverSocket.close();
    }

    private void processMessage(Socket socket)
    {
        try {
            RemoteMessage message = RemoteMessage.receive(socket.getInputStream());
            message.dispatch(broadcaster);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            if (exceptionListener != null) {
                exceptionListener.receiverThrewException(e.getTargetException());
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private class Receiver implements Runnable
    {
        public void run() {
            while (true) {
                try {
                    processMessage(serverSocket.accept());
                } catch (IOException e) {
                    break; // let the thread die
                }
            }
        }
    }

    public static interface ExceptionListener
    {
        public void receiverThrewException(Throwable throwable);
    }
}

