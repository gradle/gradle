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

package org.gradle.internal.classloader;

import com.google.common.collect.MapMaker;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;

public class CachingClassLoader extends ClassLoader implements ClassLoaderHierarchy, Closeable {
    private static final Object MISSING_CLASS = new Object();
    private final ConcurrentMap<String, Object> loadedClasses = new MapMaker().weakValues().makeMap();
    private final ClassLoader parent;

    static {
        try {
            //noinspection Since15
            ClassLoader.registerAsParallelCapable();
        } catch (NoSuchMethodError ignore) {
            // Not supported on Java 6
        }
    }

    public CachingClassLoader(ClassLoader parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Object cachedValue = loadedClasses.get(name);
        if (cachedValue instanceof Class) {
            return (Class<?>) cachedValue;
        } else if (cachedValue == MISSING_CLASS) {
            throw new ClassNotFoundException(name);
        }
        Class<?> result;
        try {
            result = super.loadClass(name, resolve);
        } catch (ClassNotFoundException e) {
            loadedClasses.putIfAbsent(name, MISSING_CLASS);
            throw e;
        }
        loadedClasses.putIfAbsent(name, result);
        return result;
    }

    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec());
        visitor.visitParent(getParent());
    }

    @Override
    public void close() throws IOException {
        loadedClasses.clear();
    }

    public static class Spec extends ClassLoaderSpec {
        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass().equals(Spec.class);
        }

        @Override
        public int hashCode() {
            return getClass().getName().hashCode();
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CachingClassLoader)) {
            return false;
        }

        CachingClassLoader that = (CachingClassLoader) o;
        return parent.equals(that.parent);
    }

    public int hashCode() {
        return parent.hashCode();
    }
}
