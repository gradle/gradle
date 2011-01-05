/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ReflectionDispatch;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class IncomingMethodInvocationHandler {
    private final ClassLoader classLoader;
    private final MultiChannelConnection<Object> connection;
    private final Set<Class<?>> classes = new CopyOnWriteArraySet<Class<?>>();

    public IncomingMethodInvocationHandler(ClassLoader classLoader, MultiChannelConnection<Object> connection) {
        this.classLoader = classLoader;
        this.connection = connection;
    }

    public <T> void addIncoming(Class<T> type, T instance) {
        addIncoming(type, new ReflectionDispatch(instance));
    }

    public void addIncoming(Class<?> type, Dispatch<? super MethodInvocation> dispatch) {
        Set<Class<?>> incomingTypes = new HashSet<Class<?>>();
        addInterfaces(type, incomingTypes);
        for (Class<?> incomingType : incomingTypes) {
            if (!classes.add(incomingType)) {
                throw new IllegalArgumentException(String.format("A handler has already been added for type '%s'.", incomingType.getName()));
            }
            connection.addIncomingChannel(incomingType.getName(), new MethodInvocationUnmarshallingDispatch(dispatch, classLoader));
        }
    }

    private void addInterfaces(Class<?> type, Set<Class<?>> superInterfaces) {
        superInterfaces.add(type);
        for (Class<?> superType : type.getInterfaces()) {
            addInterfaces(superType, superInterfaces);
        }
    }
}