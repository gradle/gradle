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
package org.gradle.messaging;

import org.gradle.messaging.dispatch.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class IncomingMethodInvocationHandler {
    private final Map<Class<?>, Dispatch<? super MethodInvocation>> incoming
            = new ConcurrentHashMap<Class<?>, Dispatch<? super MethodInvocation>>();
    private final ClassLoader classLoader;

    public IncomingMethodInvocationHandler(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Dispatch<Message> getIncomingDispatch() {
        Dispatch<MethodInvocation> invocationDispatch = new Dispatch<MethodInvocation>() {
            public void dispatch(MethodInvocation methodInvocation) {
                Dispatch<? super MethodInvocation> dispatchForType = incoming.get(
                        methodInvocation.getTargetClass());
                if (dispatchForType == null) {
                    throw new IllegalStateException(String.format("Received method invocation for unknown type '%s'.",
                            methodInvocation.getTargetClass().getName()));
                }
                dispatchForType.dispatch(methodInvocation);
            }
        };

        return new MethodInvocationUnmarshallingDispatch(invocationDispatch, classLoader);
    }

    public <T> void addIncoming(Class<T> type, T instance) {
        addIncoming(type, new ReflectionDispatch(instance));
    }

    public void addIncoming(Class<?> type, Dispatch<? super MethodInvocation> dispatch) {
        Set<Class<?>> incomingTypes = new HashSet<Class<?>>();
        addInterfaces(type, incomingTypes);
        for (Class<?> incomingType : incomingTypes) {
            if (incoming.containsKey(incomingType)) {
                throw new IllegalArgumentException(String.format("A handler has already been added for type '%s'.", incomingType.getName()));
            }
            incoming.put(incomingType, dispatch);
        }
    }

    private void addInterfaces(Class<?> type, Set<Class<?>> superInterfaces) {
        superInterfaces.add(type);
        for (Class<?> superType : type.getInterfaces()) {
            addInterfaces(superType, superInterfaces);
        }
    }
}