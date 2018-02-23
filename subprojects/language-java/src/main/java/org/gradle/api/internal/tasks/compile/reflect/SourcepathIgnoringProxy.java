/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.reflect;

import java.lang.reflect.Proxy;

/**
 * Provides a proxy object which ignores all files discovered on the sourcepath.
 */
public class SourcepathIgnoringProxy {
    @SuppressWarnings("rawtypes")
    public static Object proxy(Object proxied, Class<?> targetInterface) {
        return Proxy.newProxyInstance(
            SourcepathIgnoringInvocationHandler.class.getClassLoader(),
            new Class[] {targetInterface},
            new SourcepathIgnoringInvocationHandler(proxied));
    }
}
