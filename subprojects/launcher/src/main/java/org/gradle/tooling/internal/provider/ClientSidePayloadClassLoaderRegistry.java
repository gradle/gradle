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

import com.google.common.collect.Sets;
import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.classloader.VisitableURLClassLoader;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Used in the tooling API provider to inspect classes.
 */
@ThreadSafe
public class ClientSidePayloadClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private static final short CLIENT_CLASS_LOADER_ID = 1;
    private final PayloadClassLoaderRegistry delegate;
    private final Lock lock = new ReentrantLock();
    private final ClasspathInferer classpathInferer;
    private final ClassLoaderCache classLoaderCache;
    // Contains only application owned ClassLoaders
    private final Map<UUID, LocalClassLoader> classLoaders = new LinkedHashMap<UUID, LocalClassLoader>();

    public ClientSidePayloadClassLoaderRegistry(PayloadClassLoaderRegistry delegate, ClasspathInferer classpathInferer, ClassLoaderCache classLoaderCache) {
        this.delegate = delegate;
        this.classpathInferer = classpathInferer;
        this.classLoaderCache = classLoaderCache;
    }

    public SerializeMap newSerializeSession() {
        final Set<ClassLoader> candidates = new LinkedHashSet<ClassLoader>();
        final Set<URL> classPath = new LinkedHashSet<URL>();
        final Map<ClassLoader, Short> classLoaderIds = new HashMap<ClassLoader, Short>();
        final Map<Short, ClassLoaderDetails> classLoaderDetails = new HashMap<Short, ClassLoaderDetails>();
        return new SerializeMap() {
            public short visitClass(Class<?> target) {
                ClassLoader classLoader = target.getClassLoader();
                Short id = classLoaderIds.get(classLoader);
                if (id != null) {
                    // A known ClassLoader
                    return id;
                }
                ClassLoaderDetails details = classLoaderCache.maybeGetDetails(classLoader);
                if (details != null) {
                    // A cached ClassLoader
                    id = (short) (classLoaderIds.size() + CLIENT_CLASS_LOADER_ID + 1);
                    classLoaderIds.put(classLoader, id);
                    classLoaderDetails.put(id, details);
                    return id;
                }

                // An application ClassLoader: Inspect class to collect up the classpath for it
                classpathInferer.getClassPathFor(target, classPath);
                candidates.add(target.getClassLoader());
                return CLIENT_CLASS_LOADER_ID;
            }

            public Map<Short, ClassLoaderDetails> getClassLoaders() {
                lock.lock();
                UUID uuid;
                try {
                    uuid = getUuidForLocalClassLoaders(candidates);
                } finally {
                    lock.unlock();
                }
                ClassLoaderDetails details = new ClassLoaderDetails(uuid, new VisitableURLClassLoader.Spec(new ArrayList<URL>(classPath)));
                classLoaderDetails.put(CLIENT_CLASS_LOADER_ID, details);
                return classLoaderDetails;
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
                    candidates = getClassLoaders(classLoaderDetails.uuid);
                } finally {
                    lock.unlock();
                }
                if (candidates != null) {
                    // TODO:ADAM - This isn't quite right
                    // MB: I think ^ refers to the first capable classloader loading the class. This could be different
                    // from the loader which originally loaded it, which could pose equality and lifecycle issues.
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

    private Set<ClassLoader> getClassLoaders(UUID uuid) {
        LocalClassLoader localClassLoader = classLoaders.get(uuid);
        if (localClassLoader == null) {
            return null;
        }
        Set<ClassLoader> candidates = Sets.newLinkedHashSet();
        for (WeakReference<ClassLoader> reference : localClassLoader.classLoaders) {
            ClassLoader classLoader = reference.get();
            if (classLoader != null) {
                candidates.add(classLoader);
            }
        }
        return candidates;
    }

    private UUID getUuidForLocalClassLoaders(Set<ClassLoader> candidates) {
        for (LocalClassLoader localClassLoader : new ArrayList<LocalClassLoader>(classLoaders.values())) {
            Set<ClassLoader> localCandidates = new LinkedHashSet<ClassLoader>();
            for (WeakReference<ClassLoader> reference : localClassLoader.classLoaders) {
                ClassLoader cl = reference.get();
                if (cl != null) {
                    localCandidates.add(cl);
                }
            }
            if (localCandidates.isEmpty()) {
                classLoaders.remove(localClassLoader.uuid);
                continue;
            }
            if (localCandidates.equals(candidates)) {
                return localClassLoader.uuid;
            }
        }

        LocalClassLoader details = new LocalClassLoader(UUID.randomUUID());
        for (ClassLoader candidate : candidates) {
            details.classLoaders.add(new WeakReference<ClassLoader>(candidate));
        }
        classLoaders.put(details.uuid, details);
        return details.uuid;
    }

    private static class LocalClassLoader {
        private final Set<WeakReference<ClassLoader>> classLoaders = Sets.newLinkedHashSet();
        private final UUID uuid;

        private LocalClassLoader(UUID uuid) {
            this.uuid = uuid;
        }
    }
}
