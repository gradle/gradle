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

package org.gradle.messaging.dispatch;

public class ContextClassLoaderDispatch<T> implements Dispatch<T> {
    private final Dispatch<? super T> dispatch;
    private final ClassLoader contextClassLoader;

    public ContextClassLoaderDispatch(Dispatch<? super T> dispatch, ClassLoader contextClassLoader) {
        this.dispatch = dispatch;
        this.contextClassLoader = contextClassLoader;
    }

    public void dispatch(T message) {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        try {
            dispatch.dispatch(message);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }
}
