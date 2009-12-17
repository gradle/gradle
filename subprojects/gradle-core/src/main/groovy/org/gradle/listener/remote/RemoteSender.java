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

import org.gradle.listener.Event;

import java.io.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.Socket;

public class RemoteSender<T> implements Closeable {
    private final T source;
    private final Socket socket;
    private final OutputStream outstr;

    public RemoteSender(Class<T> type, int port) throws IOException {
        socket = new Socket((String) null, port);
        outstr = new BufferedOutputStream(socket.getOutputStream());
        source = type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                new SenderInvocationHandler()));
    }

    public T getSource() {
        return source;
    }

    public void close() throws IOException {
        socket.close();
    }

    private class SenderInvocationHandler implements InvocationHandler {
        public Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
            Event event = new Event(method, arguments);
            event.send(outstr);
            outstr.flush();
            return null;
        }
    }

}

