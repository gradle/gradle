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

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

class RemoteMessage implements Serializable
{
    private String methodName;
    private Class[] parameters;
    private Object[] arguments;

    public RemoteMessage(Method method, Object[] args) {
        methodName = method.getName();
        parameters = method.getParameterTypes();
        arguments = args;
    }

    public void send(OutputStream outputSteam) throws IOException {
        ObjectOutputStream oos = null;
        try {
            oos = new ObjectOutputStream(outputSteam);
            oos.writeObject(this);
        } finally {
            if (oos != null) oos.close();
        }
    }

    public static RemoteMessage receive(InputStream inputSteam) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = null;
        try {
            ois = new ObjectInputStream(inputSteam);
            return (RemoteMessage) ois.readObject();
        } finally {
            if (ois != null) ois.close();
        }
    }

    public Object dispatch(ListenerBroadcast<?> broadcaster) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> listenerClass = broadcaster.getType();
        Method method = listenerClass.getMethod(methodName, parameters);
        return method.invoke(broadcaster.getSource(), arguments);
    }
}

