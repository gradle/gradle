/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.daemon.client.serialization;

import com.google.common.collect.ImmutableList;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderDetails;
import org.gradle.tooling.internal.provider.serialization.ClientOwnedClassLoaderSpec;
import org.gradle.tooling.internal.provider.serialization.DeserializeMap;
import org.gradle.tooling.internal.provider.serialization.PayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.SerializeMap;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.ref.WeakReference;
import java.net.URI;
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
 * A {@link PayloadClassLoaderRegistry} used in the client JVM that maps classes loaded by application ClassLoaders. Inspects each class to calculate a minimal classpath to send across to the daemon process to recreate the ClassLoaders.
 *
 * <p>Delegates to another {@link PayloadClassLoaderRegistry} to take care of managing the classes serialized from the daemon.
 */
@ThreadSafe
public class ClientSidePayloadClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private static final short CLIENT_CLASS_LOADER_ID = 1;
    private final PayloadClassLoaderRegistry delegate;
    private final ClasspathInferer classpathInferer;
    private final ClassLoaderCache classLoaderCache;
    private final Lock lock = new ReentrantLock(); // protects the following state
    // Contains only application owned ClassLoaders
    private final Map<UUID, LocalClassLoaderMapping> classLoaders = new LinkedHashMap<>();

    public ClientSidePayloadClassLoaderRegistry(PayloadClassLoaderRegistry delegate, ClasspathInferer classpathInferer, ClassLoaderCache classLoaderCache) {
        this.delegate = delegate;
        this.classpathInferer = classpathInferer;
        this.classLoaderCache = classLoaderCache;
    }

    @Override
    public SerializeMap newSerializeSession() {
        final Set<ClassLoader> candidates = new LinkedHashSet<>();
        final Set<URI> classPath = new LinkedHashSet<>();
        final Map<ClassLoader, Short> classLoaderIds = new HashMap<>();
        final Map<Short, ClassLoaderDetails> classLoaderDetails = new HashMap<>();
        return new SerializeMap() {
            @Override
            public short visitClass(Class<?> target) {
                ClassLoader classLoader = target.getClassLoader();
                Short id = classLoaderIds.get(classLoader);
                if (id != null) {
                    // Already seen this ClassLoader
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

            @Override
            public void collectClassLoaderDefinitions(Map<Short, ClassLoaderDetails> details) {
                ClassLoaderDetails clientClassLoaders = getDetailsForClassLoaders(candidates, classPath);
                details.putAll(classLoaderDetails);
                details.put(CLIENT_CLASS_LOADER_ID, clientClassLoaders);
            }
        };
    }

    @Override
    public DeserializeMap newDeserializeSession() {
        final DeserializeMap deserializeMap = delegate.newDeserializeSession();
        return new DeserializeMap() {
            @Override
            public Class<?> resolveClass(ClassLoaderDetails classLoaderDetails, String className) throws ClassNotFoundException {
                Set<ClassLoader> candidates = getClassLoaders(classLoaderDetails);
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

    private Set<ClassLoader> getClassLoaders(ClassLoaderDetails details) {
        lock.lock();
        try {
            // Locate the set of classloaders for the given details. First, try to locate by UUID.
            // A match by UUID means the entry was created by this client and can be reused
            LocalClassLoaderMapping localClassLoaderMapping = classLoaders.get(details.uuid);
            if (localClassLoaderMapping != null) {
                return localClassLoaderMapping.getClassLoaders();
            }

            // Try to locate by classloader spec
            // A match by classloader spec means the entry in the daemon was created by another client with exactly the same classloader structure as this client
            for (LocalClassLoaderMapping classLoaderMapping : new ArrayList<>(classLoaders.values())) {
                if (classLoaderMapping.details.spec.equals(details.spec)) {
                    // Found an entry with the same spec, so reuse it
                    classLoaders.put(details.uuid, classLoaderMapping);
                    return classLoaderMapping.getClassLoaders();
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    private ClassLoaderDetails getDetailsForClassLoaders(Set<ClassLoader> candidates, Set<URI> classPath) {
        lock.lock();
        try {
            // Determine whether the given set of classloaders have already been used for some previous request
            // A single daemon side classloader is used for a given set of client side classloaders
            // So, if we find an entry for the given set of classloaders, then reuse it
            for (LocalClassLoaderMapping localClassLoaderMapping : new ArrayList<>(classLoaders.values())) {
                Set<ClassLoader> localCandidates = new LinkedHashSet<>();
                for (WeakReference<ClassLoader> reference : localClassLoaderMapping.classLoaders) {
                    ClassLoader cl = reference.get();
                    if (cl != null) {
                        localCandidates.add(cl);
                    }
                }
                if (localCandidates.isEmpty()) {
                    // Classloaders in this entry have all been garbage collected, so remove the entry
                    classLoaders.remove(localClassLoaderMapping.details.uuid);
                    continue;
                }
                if (localCandidates.equals(candidates)) {
                    // A match. Because the entry is reused, add any additional classpath entries
                    return new ClassLoaderDetails(localClassLoaderMapping.details.uuid, new ClientOwnedClassLoaderSpec(ImmutableList.copyOf(classPath)));
                }
            }

            // Haven't seen the classloaders before - add a new entry
            UUID uuid = UUID.randomUUID();
            ClassLoaderDetails clientClassLoaders = new ClassLoaderDetails(uuid, new ClientOwnedClassLoaderSpec(ImmutableList.copyOf(classPath)));
            LocalClassLoaderMapping mapping = new LocalClassLoaderMapping(clientClassLoaders);
            for (ClassLoader candidate : candidates) {
                mapping.classLoaders.add(new WeakReference<>(candidate));
            }
            classLoaders.put(uuid, mapping);
            return mapping.details;
        } finally {
            lock.unlock();
        }
    }

    private static class LocalClassLoaderMapping {
        private final Set<WeakReference<ClassLoader>> classLoaders = new LinkedHashSet<>();
        private final ClassLoaderDetails details;

        private LocalClassLoaderMapping(ClassLoaderDetails details) {
            this.details = details;
        }

        @Override
        public String toString() {
            return "{details=" + details + " classloaders=" + classLoaders + "}";
        }

        Set<ClassLoader> getClassLoaders() {
            Set<ClassLoader> candidates = new LinkedHashSet<>();
            for (WeakReference<ClassLoader> reference : classLoaders) {
                ClassLoader classLoader = reference.get();
                if (classLoader != null) {
                    candidates.add(classLoader);
                }
            }
            return candidates;
        }
    }
}
