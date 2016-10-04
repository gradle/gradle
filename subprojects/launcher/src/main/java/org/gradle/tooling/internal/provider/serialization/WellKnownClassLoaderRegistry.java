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

package org.gradle.tooling.internal.provider.serialization;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.ClassLoaderUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A {@link PayloadClassLoaderRegistry} that maps classes loaded by several well known ClassLoaders: the JVM platform, Gradle core and Gradle plugins.
 *
 * <p>Delegates to another {@link PayloadClassLoaderRegistry} for all other classes.
 */
public class WellKnownClassLoaderRegistry implements PayloadClassLoaderRegistry {
    private static final Set<ClassLoader> PLATFORM_CLASS_LOADERS;
    private static final ClassLoader PLATFORM_CLASS_LOADER = ClassLoaderUtils.getPlatformClassLoader();
    private static final short PLATFORM_CLASS_LOADER_ID = -1;
    private static final ClassLoaderDetails PLATFORM_CLASS_LOADER_DETAILS = new ClassLoaderDetails(UUID.randomUUID(), new KnownClassLoaderSpec(PLATFORM_CLASS_LOADER_ID));
    private final PayloadClassLoaderRegistry delegate;

    static {
        ImmutableSet.Builder<ClassLoader> builder = ImmutableSet.builder();
        for (ClassLoader cl = PLATFORM_CLASS_LOADER; cl != null; cl = cl.getParent()) {
            builder.add(cl);
        }
        PLATFORM_CLASS_LOADERS = builder.build();
    }

    public WellKnownClassLoaderRegistry(PayloadClassLoaderRegistry delegate) {
        this.delegate = delegate;
    }

    @Override
    public SerializeMap newSerializeSession() {
        final SerializeMap delegateSession = delegate.newSerializeSession();
        return new SerializeMap() {
            Map<Short, ClassLoaderDetails> knownLoaders = new HashMap<Short, ClassLoaderDetails>();

            @Override
            public short visitClass(Class<?> target) {
                ClassLoader classLoader = target.getClassLoader();
                if (classLoader == null || PLATFORM_CLASS_LOADERS.contains(classLoader)) {
                    knownLoaders.put(PLATFORM_CLASS_LOADER_ID, PLATFORM_CLASS_LOADER_DETAILS);
                    return PLATFORM_CLASS_LOADER_ID;
                }
                return delegateSession.visitClass(target);
            }

            @Override
            public void collectClassLoaderDefinitions(Map<Short, ClassLoaderDetails> details) {
                delegateSession.collectClassLoaderDefinitions(details);
                details.putAll(knownLoaders);
            }
        };
    }

    @Override
    public DeserializeMap newDeserializeSession() {
        final DeserializeMap delegateSession = delegate.newDeserializeSession();
        return new DeserializeMap() {
            @Override
            public Class<?> resolveClass(ClassLoaderDetails classLoaderDetails, String className) throws ClassNotFoundException {
                if (classLoaderDetails.spec instanceof KnownClassLoaderSpec) {
                    KnownClassLoaderSpec knownClassLoaderSpec = (KnownClassLoaderSpec) classLoaderDetails.spec;
                    switch (knownClassLoaderSpec.id) {
                        case PLATFORM_CLASS_LOADER_ID:
                            return Class.forName(className, false, PLATFORM_CLASS_LOADER);
                        default:
                            throw new IllegalArgumentException("Unknown ClassLoader id specified.");
                    }
                }
                return delegateSession.resolveClass(classLoaderDetails, className);
            }
        };
    }

    private static class KnownClassLoaderSpec extends ClassLoaderSpec {
        private final short id;

        KnownClassLoaderSpec(short id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }

            KnownClassLoaderSpec other = (KnownClassLoaderSpec) obj;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public String toString() {
            return "{known-class-loader id: " + id + "}";
        }
    }
}
