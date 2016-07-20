/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.gradle.internal.Factory;
import org.gradle.internal.Pair;

import java.io.Closeable;
import java.util.List;

/**
 * A ClassLoader which lazily re-instantiates its parent on the next use after it's closed.
 */
public class ReusableClassLoader extends ClassLoader implements ClassLoaderHierarchy, Closeable {
    private final Factory<ClassLoader> parentFactory;
    private ClassLoader parent;
    private List<Pair<String, Boolean>> loadedClasses;

    public ReusableClassLoader(Factory<ClassLoader> parentFactory) {
        this.parentFactory = Preconditions.checkNotNull(parentFactory);
        this.parent = null;
        this.loadedClasses = Lists.newArrayList();
    }

    @Override
    public void close() {
        ClassLoaderUtils.tryClose(parent);
        parent = null;
    }

    @Override
    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new CachingClassLoader.Spec());
        visitor.visitParent(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (parent == null) {
            parent = parentFactory.create();
            replay();
        }

        Class<?> loadedClass = parent.loadClass(name);
        loadedClasses.add(Pair.of(name, resolve));
        return loadedClass;
    }

    private void replay() throws ClassNotFoundException {
        // Reload prior classes one by one.
        List<Pair<String, Boolean>> classesToLoad = loadedClasses;
        loadedClasses = Lists.newArrayList();

        // This builds loadedClasses back up; the lists should be equal by the end of it.
        for (Pair<String, Boolean> replayClass : classesToLoad) {
            try {
                loadClass(replayClass.getLeft(), replayClass.getRight());
            } catch (ClassNotFoundException e) {
                // If loading fails, back out to the state before replay was called.
                close();
                loadedClasses = classesToLoad;
                throw e;
            }
        }

        if (!loadedClasses.equals(classesToLoad)) {
            close();
            throw new IllegalStateException("Replayed classes (" + loadedClasses + ") don't match old list of classes (" + classesToLoad + ")");
        }
    }
}
