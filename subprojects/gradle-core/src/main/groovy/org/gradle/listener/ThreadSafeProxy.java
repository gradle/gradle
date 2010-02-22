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

import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.dispatch.ReflectionDispatch;
import org.gradle.messaging.dispatch.ThreadSafeDispatch;

/**
 * Creates a proxy object which invoking method on the target object from a single thread at a time.
 *
 * @param <T>
 */
public class ThreadSafeProxy<T> {
    private final ProxyDispatchAdapter<T> adapter;

    /**
     * Creates a proxy which dispatches to the given target object.
     */
    public ThreadSafeProxy(Class<T> type, T target) {
        adapter = new ProxyDispatchAdapter<T>(type, new ThreadSafeDispatch<MethodInvocation>(new ReflectionDispatch(target)));
    }

    public T getSource() {
        return adapter.getSource();
    }
}