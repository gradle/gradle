/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import com.google.common.collect.MapMaker;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.classloader.MutableURLClassLoader;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

@ThreadSafe
public class ModelClassLoaderRegistry {
    private final ConcurrentMap<List<URL>, ClassLoader> loaders;

    public ModelClassLoaderRegistry() {
        loaders = new MapMaker().softValues().makeMap();
    }

    public ClassLoader getClassLoaderFor(List<URL> classpath) {
        ArrayList<URL> key = new ArrayList<URL>(classpath);
        ClassLoader classLoader = loaders.get(key);
        while (classLoader == null) {
            loaders.putIfAbsent(key, new MutableURLClassLoader(getClass().getClassLoader(), key));
            classLoader = loaders.get(key);
        }
        return classLoader;
    }
}
