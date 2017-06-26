/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.initialization.loadercache;

import com.google.common.hash.HashCode;
import org.gradle.api.Nullable;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classpath.ClassPath;

import java.net.URLClassLoader;

public class DummyClassLoaderCache implements ClassLoaderCache {

    @Override
    public ClassLoader get(ClassLoaderId id, ClassPath classPath, ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec, HashCode implementationHash) {
        return new URLClassLoader(classPath.getAsURLArray(), parent);
    }

    @Override
    public ClassLoader get(ClassLoaderId id, ClassPath classPath, @Nullable ClassLoader parent, @Nullable FilteringClassLoader.Spec filterSpec) {
        return get(id, classPath, parent, filterSpec, null);
    }

    @Override
    public <T extends ClassLoader> T put(ClassLoaderId id, T classLoader) {
        return classLoader;
    }

    @Override
    public void remove(ClassLoaderId id) {
    }

    @Override
    public int size() {
        return 0;
    }
}
