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

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.classloader.MutableURLClassLoader;

import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class ClientSidePayloadClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private static final short CLIENT_CLASS_LOADER_ID = 1;
    private final PayloadClassLoaderRegistry delegate;
    private final Lock lock = new ReentrantLock();
    private final ClasspathInferer classpathInferer;
    // TODO - don't use strong references
    private final Map<Set<ClassLoader>, UUID> classLoaderIds = new HashMap<Set<ClassLoader>, UUID>();
    private final Map<UUID, Set<ClassLoader>> classLoaders = new HashMap<UUID, Set<ClassLoader>>();

    public ClientSidePayloadClassLoaderRegistry(PayloadClassLoaderRegistry delegate, ClasspathInferer classpathInferer) {
        this.delegate = delegate;
        this.classpathInferer = classpathInferer;
    }

    public SerializeMap newSerializeSession() {
        final Set<ClassLoader> candidates = new LinkedHashSet<ClassLoader>();
        final Set<URL> classPath = new LinkedHashSet<URL>();

        return new SerializeMap() {
            public short visitClass(Class<?> target) {
                classpathInferer.getClassPathFor(target, classPath);
                candidates.add(target.getClassLoader());
                return CLIENT_CLASS_LOADER_ID;
            }

            public Map<Short, ClassLoaderDetails> getClassLoaders() {
                lock.lock();
                UUID uuid;
                try {
                    uuid = classLoaderIds.get(candidates);
                    if (uuid == null) {
                        uuid = UUID.randomUUID();
                        classLoaderIds.put(candidates, uuid);
                        classLoaders.put(uuid, candidates);
                    }
                } finally {
                    lock.unlock();
                }
                return Collections.singletonMap(CLIENT_CLASS_LOADER_ID, new ClassLoaderDetails(uuid, new MutableURLClassLoader.Spec(new ArrayList<URL>(classPath))));
            }
        };
    }

    public DeserializeMap newDeserializeSession() {
        final DeserializeMap deserializeMap = delegate.newDeserializeSession();
        return new DeserializeMap() {
            public Class<?> resolveClass(ClassLoaderDetails classLoaderDetails, String className) throws ClassNotFoundException {
                Set<ClassLoader> candidates;
                lock.lock();
                try {
                    candidates = classLoaders.get(classLoaderDetails.uuid);
                } finally {
                    lock.unlock();
                }
                if (candidates != null) {
                    // TODO:ADAM - This isn't quite right
                    for (ClassLoader candidate : candidates) {
                        try {
                            return candidate.loadClass(className);
                        } catch (ClassNotFoundException e) {
                            // Ignore
                        }
                    }
                    throw new UnsupportedOperationException("Unexpected class received in response.");
                }
                return deserializeMap.resolveClass(classLoaderDetails, className);
            }
        };
    }
}
