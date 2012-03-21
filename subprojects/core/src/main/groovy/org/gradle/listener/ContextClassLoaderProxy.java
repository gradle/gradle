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

package org.gradle.listener;

import org.gradle.messaging.dispatch.ContextClassLoaderDispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.dispatch.ReflectionDispatch;

/**
 * Creates a proxy object which sets the context ClassLoader when invoking methods on the target object.
 *
 * @param <T>
 */
public class ContextClassLoaderProxy<T> {
    private final ProxyDispatchAdapter<T> adapter;

    /**
     * Creates a proxy which dispatches to the given target object.
     */
    public ContextClassLoaderProxy(Class<T> type, T target, ClassLoader contextClassLoader) {
        adapter = new ProxyDispatchAdapter<T>(new ContextClassLoaderDispatch<MethodInvocation>(new ReflectionDispatch(target), contextClassLoader), type);
    }

    public T getSource() {
        return adapter.getSource();
    }
}