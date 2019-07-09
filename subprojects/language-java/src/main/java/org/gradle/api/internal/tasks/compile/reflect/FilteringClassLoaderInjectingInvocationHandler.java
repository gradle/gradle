/*
 * Copyright 2019 the original author or authors.
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

import javax.tools.StandardLocation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.URLClassLoader;

import static org.gradle.api.internal.tasks.compile.filter.AnnotationProcessorFilter.getFilteredClassLoader;

/**
 * This class injects a filtering classloader when the compiler uses the standard java annotation processor path.
 * This prevents Gradle classes or external libraries from being visible on the annotation processor path.
 */
public class FilteringClassLoaderInjectingInvocationHandler implements InvocationHandler {
    public static final String GET_CLASS_LOADER = "getClassLoader";
    private final Object proxied;

    public FilteringClassLoaderInjectingInvocationHandler(Object proxied) {
        this.proxied = proxied;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals(GET_CLASS_LOADER) && args[0] == StandardLocation.ANNOTATION_PROCESSOR_PATH) {
            ClassLoader classLoader = (ClassLoader) method.invoke(proxied, args);
            if (classLoader instanceof URLClassLoader) {
                return new URLClassLoader(((URLClassLoader) classLoader).getURLs(), getFilteredClassLoader(classLoader.getParent()));
            } else {
                return classLoader;
            }
        }
        return method.invoke(proxied, args);
    }
}

