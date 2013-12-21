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
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class DefaultPayloadClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPayloadClassLoaderRegistry.class);
    private final Lock lock = new ReentrantLock();
    private final ModelClassLoaderFactory classLoaderFactory;
    private Map<ClassLoader, ClassLoaderDetails> classLoaderDetails;
    private Map<UUID, ClassLoader> classLoaderIds;

    public DefaultPayloadClassLoaderRegistry(ModelClassLoaderFactory modelClassLoaderFactory) {
        classLoaderDetails = new MapMaker().weakKeys().makeMap();
        classLoaderIds = new MapMaker().weakValues().makeMap();
        this.classLoaderFactory = modelClassLoaderFactory;
    }

    public SerializeMap newSerializeSession() {
        return new SerializeMap() {
            final Map<ClassLoader, Short> classLoaderIds = new HashMap<ClassLoader, Short>();
            final Map<Short, ClassLoaderDetails> classLoaderDetails = new HashMap<Short, ClassLoaderDetails>();

            public short visitClass(Class<?> target) {
                ClassLoader classLoader = target.getClassLoader();
                Short id = classLoaderIds.get(classLoader);
                if (id != null) {
                    return id;
                }
                if (classLoaderIds.size() == Short.MAX_VALUE) {
                    throw new UnsupportedOperationException();
                }
                ClassLoaderDetails details = getDetails(classLoader);
                id = (short) (classLoaderIds.size() + 1);

                classLoaderIds.put(classLoader, id);
                classLoaderDetails.put(id, details);

                return id;
            }

            public Map<Short, ClassLoaderDetails> getClassLoaders() {
                return classLoaderDetails;
            }
        };
    }

    public DeserializeMap newDeserializeSession() {
        return new DeserializeMap() {
            public Class<?> resolveClass(ClassLoaderDetails classLoaderDetails, String className) throws ClassNotFoundException {
                ClassLoader classLoader = getClassLoader(classLoaderDetails);
                return Class.forName(className, false, classLoader);
            }
        };
    }

    private ClassLoader getClassLoader(ClassLoaderDetails details) {
        lock.lock();
        try {
            ClassLoader classLoader = classLoaderIds.get(details.uuid);
            if (classLoader != null) {
                return classLoader;
            }

            List<ClassLoader> parents = new ArrayList<ClassLoader>();
            for (ClassLoaderDetails parentDetails : details.parents) {
                parents.add(getClassLoader(parentDetails));
            }
            if (parents.isEmpty()) {
                parents.add(classLoaderFactory.getClassLoaderFor(ClassLoaderSpec.SYSTEM_CLASS_LOADER, null));
            }

            LOGGER.info("Creating ClassLoader {} from {} and {}.", details.uuid, details.spec, parents);

            classLoader = classLoaderFactory.getClassLoaderFor(details.spec, parents);
            classLoaderIds.put(details.uuid, classLoader);
            classLoaderDetails.put(classLoader, details);
            return classLoader;
        } finally {
            lock.unlock();
        }
    }

    private ClassLoaderDetails getDetails(ClassLoader classLoader) {
        lock.lock();
        try {
            ClassLoaderDetails details = classLoaderDetails.get(classLoader);
            if (details != null) {
                return details;
            }

            ClassLoaderSpecVisitor visitor = new ClassLoaderSpecVisitor(classLoader);
            visitor.visit(classLoader);

            if (visitor.spec == null) {
                if (visitor.classPath == null) {
                    visitor.spec = ClassLoaderSpec.SYSTEM_CLASS_LOADER;
                } else {
                    visitor.spec = new MutableURLClassLoader.Spec(CollectionUtils.toList(visitor.classPath));
                }
            }

            UUID uuid = UUID.randomUUID();
            details = new ClassLoaderDetails(uuid, visitor.spec);
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
        ClassLoaderSpec spec;
        URL[] classPath;

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
            this.classPath = classPath;
        }

        @Override
        public void visitSpec(ClassLoaderSpec spec) {
            this.spec = spec;
        }
    }
}
