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

import com.google.common.collect.Maps;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classloader.MultiParentClassLoader;

import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class ClassLoaderRegistry {
    private final Lock lock = new ReentrantLock();
    private final ModelClassLoaderFactory classLoaderFactory;
    // TODO:ADAM - don't use strong references
    private final Map<ClassLoader, ClassLoaderDetails> classLoaderDetails = Maps.newHashMap();
    private final Map<UUID, ClassLoader> classLoaderIds = Maps.newHashMap();

    public ClassLoaderRegistry(ModelClassLoaderFactory classLoaderFactory) {
        this.classLoaderFactory = classLoaderFactory;
    }

    public ClassLoader getClassLoader(ClassLoaderDetails details) {
        lock.lock();
        try {
            ClassLoader classLoader = classLoaderIds.get(details.uuid);
            if (classLoader != null) {
                return classLoader;
            }
            ClassLoader parent = null;
            if (details.parents.size() == 1) {
                parent = getClassLoader(details.parents.get(0));
            } else if (details.parents.size() > 1) {
                MultiParentClassLoader multiParentClassLoader = new MultiParentClassLoader();
                for (ClassLoaderDetails parentDetails : details.parents) {
                    multiParentClassLoader.addParent(getClassLoader(parentDetails));
                }
                parent = new CachingClassLoader(multiParentClassLoader);
            }
            classLoader = classLoaderFactory.getClassLoaderFor(details.classPath, parent);
            classLoaderIds.put(details.uuid, classLoader);
            classLoaderDetails.put(classLoader, details);
            return classLoader;
        } finally {
            lock.unlock();
        }
    }

    public ClassLoaderDetails getDetails(ClassLoader classLoader) {
        lock.lock();
        try {
            ClassLoaderDetails details = classLoaderDetails.get(classLoader);
            if (details != null) {
                return details;
            }

            ClassLoaderSpecVisitor visitor = new ClassLoaderSpecVisitor(classLoader);
            visitor.visit(classLoader);

            UUID uuid = UUID.randomUUID();
            details = new ClassLoaderDetails(uuid, visitor.classPath);
            for (ClassLoader parent : visitor.parents) {
                details.parents.add(getDetails(parent));
            }

            classLoaderDetails.put(classLoader, details);
            classLoaderIds.put(details.uuid, classLoader);
            return details;
        } finally {
            lock.unlock();
        }
    }

    private static class ClassLoaderSpecVisitor extends ClassLoaderVisitor {
        final ClassLoader classLoader;
        final List<ClassLoader> parents = new ArrayList<ClassLoader>();
        final List<URL> classPath = new ArrayList<URL>();

        public ClassLoaderSpecVisitor(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void visit(ClassLoader candidate) {
            if (candidate == classLoader) {
                super.visit(candidate);
            } else {
                parents.add(candidate);
            }
        }

        @Override
        public void visitClassPath(URL[] classPath) {
            this.classPath.addAll(Arrays.asList(classPath));
        }
    }
}
