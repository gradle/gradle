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
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderVisitor;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;

import java.io.*;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@ThreadSafe
public class PayloadSerializer {
    private static final short SYSTEM_CLASS_LOADER_ID = (short) 1;
    private final Lock lock = new ReentrantLock();
    private final ModelClassLoaderFactory classLoaderRegistry;
    // TODO:ADAM - don't use strong references
    private final Map<ClassLoader, ClassLoaderDetails> classLoaderDetails = Maps.newHashMap();
    private final Map<UUID, ClassLoader> classLoaderIds = Maps.newHashMap();
    private final Transformer<ObjectStreamClass, Class<?>> classLookup;

    public PayloadSerializer(ModelClassLoaderFactory classLoaderRegistry) {
        this.classLoaderRegistry = classLoaderRegistry;

        // On Java 6, there is a public method to lookup a class descriptor given a class. On Java 5, we have to use reflection
        // TODO:ADAM - move this into the service registry
        if (Jvm.current().getJavaVersion().isJava6Compatible()) {
            // Use the public method
            try {
                classLookup = (Transformer<ObjectStreamClass, Class<?>>) getClass().getClassLoader().loadClass("org.gradle.tooling.internal.provider.jdk6.Jdk6ClassLookup").newInstance();
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            // Use reflection
            classLookup = new ReflectionClassLookup();
        }
    }

    public SerializedPayload serialize(final Object payload) {
        return doSerialize(payload, new DefaultSerializeMap(null));
    }

    public SerializedPayload serialize(final Object payload, SerializeMap map) {
        return doSerialize(payload, new DefaultSerializeMap(map));
    }

    private SerializedPayload doSerialize(final Object payload, final DefaultSerializeMap map) {
        try {
            final Map<ClassLoader, Short> classLoaderIds = new HashMap<ClassLoader, Short>();
            final Map<Short, ClassLoaderDetails> classLoaderDetails = new HashMap<Short, ClassLoaderDetails>();
            final Set<ClassLoader> systemClassLoaders = new HashSet<ClassLoader>();
            for (ClassLoader cl = ClassLoader.getSystemClassLoader().getParent(); cl != null; cl = cl.getParent()) {
                systemClassLoaders.add(cl);
            }

            ByteArrayOutputStream content = new ByteArrayOutputStream();
            ObjectOutputStream objectStream = new ObjectOutputStream(content) {
                @Override
                protected void writeClassDescriptor(ObjectStreamClass desc) throws IOException {
                    Class<?> targetClass = desc.forClass();
                    writeClass(targetClass);
                }

                @Override
                protected void annotateProxyClass(Class<?> cl) throws IOException {
                    writeClassLoader(cl);
                    writeInt(cl.getInterfaces().length);
                    for (Class<?> type : cl.getInterfaces()) {
                        writeClass(type);
                    }
                }

                private void writeClass(Class<?> targetClass) throws IOException {
                    writeClassLoader(targetClass);
                    writeUTF(targetClass.getName());
                }

                private void writeClassLoader(Class<?> targetClass) throws IOException {
                    writeShort(getClassLoaderId(targetClass));
                }

                private short getClassLoaderId(Class<?> targetClass) {
                    ClassLoader classLoader = targetClass.getClassLoader();
                    if (classLoader == null || systemClassLoaders.contains(classLoader)) {
                        return SYSTEM_CLASS_LOADER_ID;
                    }

                    Short id = classLoaderIds.get(classLoader);
                    if (id != null) {
                        return id;
                    }

                    ClassLoaderDetails details = map.getDetails(classLoader);
                    id = (short) (classLoaderIds.size() + SYSTEM_CLASS_LOADER_ID + 1);

                    classLoaderIds.put(classLoader, id);
                    classLoaderDetails.put(id, details);

                    return id;
                }
            };

            objectStream.writeObject(payload);
            objectStream.close();

            return new SerializedPayload(classLoaderDetails, content.toByteArray());
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public Object deserialize(SerializedPayload payload) {
        return doDeserialize(payload, new DefaultDeserializeMap(null));
    }

    public Object deserialize(SerializedPayload payload, DeserializeMap map) {
        return doDeserialize(payload, new DefaultDeserializeMap(map));
    }

    private Object doDeserialize(SerializedPayload payload, DefaultDeserializeMap map) {
        try {
            final Map<Short, ClassLoader> classLoaders = new HashMap<Short, ClassLoader>();
            for (ClassLoader cl = ClassLoader.getSystemClassLoader().getParent(); cl != null; cl = cl.getParent()) {
                classLoaders.put(SYSTEM_CLASS_LOADER_ID, ClassLoader.getSystemClassLoader().getParent());
            }
            Map<Short, ClassLoaderDetails> detailsMap = (Map<Short, ClassLoaderDetails>) payload.getHeader();
            for (Map.Entry<Short, ClassLoaderDetails> entry : detailsMap.entrySet()) {
                classLoaders.put(entry.getKey(), map.getClassLoader(entry.getValue()));
            }

            final ObjectInputStream objectStream = new ObjectInputStream(new ByteArrayInputStream(payload.getSerializedModel())) {
                @Override
                protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                    Class<?> aClass = readClass();
                    ObjectStreamClass descriptor = classLookup.transform(aClass);
                    if (descriptor == null) {
                        throw new ClassNotFoundException(aClass.getName());
                    }
                    return descriptor;
                }

                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                    return desc.forClass();
                }

                private Class<?> readClass() throws IOException, ClassNotFoundException {
                    ClassLoader classLoader = readClassLoader();
                    String className = readUTF();
                    return Class.forName(className, false, classLoader);
                }

                @Override
                protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
                    ClassLoader classLoader = readClassLoader();
                    int count = readInt();
                    Class<?>[] actualInterfaces = new Class<?>[count];
                    for (int i = 0; i < count; i++) {
                        actualInterfaces[i] = readClass();
                    }
                    return Proxy.getProxyClass(classLoader, actualInterfaces);
                }

                private ClassLoader readClassLoader() throws IOException {
                    short id = readShort();
                    return classLoaders.get(id);
                }
            };
            return objectStream.readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private class DefaultSerializeMap {
        final SerializeMap overrides;

        private DefaultSerializeMap(SerializeMap overrides) {
            this.overrides = overrides;
        }

        public ClassLoaderDetails getDetails(final ClassLoader classLoader) {
            lock.lock();
            try {
                ClassLoaderDetails details = classLoaderDetails.get(classLoader);
                if (details != null) {
                    return details;
                }
                if (overrides != null) {
                    details = overrides.getDetails(classLoader);
                }
                if (details == null) {
                    final List<URL> classpath = new ArrayList<URL>();
                    final List<ClassLoader> parents = new ArrayList<ClassLoader>();
                    new ClassLoaderVisitor() {
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
                            classpath.addAll(Arrays.asList(classPath));
                        }
                    }.visit(classLoader);

                    UUID uuid = UUID.randomUUID();
                    details = new ClassLoaderDetails(uuid, classpath);
                    for (ClassLoader parent : parents) {
                        details.parents.add(getDetails(parent));
                    }
                }
                classLoaderDetails.put(classLoader, details);
                classLoaderIds.put(details.uuid, classLoader);
                return details;
            } finally {
                lock.unlock();
            }
        }
    }

    private class DefaultDeserializeMap {
        final DeserializeMap overrides;

        private DefaultDeserializeMap(DeserializeMap overrides) {
            this.overrides = overrides;
        }

        public ClassLoader getClassLoader(ClassLoaderDetails details) {
            lock.lock();
            try {
                ClassLoader classLoader = classLoaderIds.get(details.uuid);
                if (classLoader != null) {
                    return classLoader;
                }
                if (overrides != null) {
                    classLoader = overrides.getClassLoader(details);
                }
                if (classLoader == null) {
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
                    classLoader = classLoaderRegistry.getClassLoaderFor(details.classPath, parent);
                }
                classLoaderIds.put(details.uuid, classLoader);
                classLoaderDetails.put(classLoader, details);
                return classLoader;
            } finally {
                lock.unlock();
            }
        }
    }

    public static class ClassLoaderDetails implements Serializable {
        final UUID uuid;
        final List<URL> classPath;
        final List<ClassLoaderDetails> parents = new ArrayList<ClassLoaderDetails>();

        public ClassLoaderDetails(UUID uuid, List<URL> classPath) {
            this.uuid = uuid;
            this.classPath = classPath;
        }
    }

    /**
     * Implementations don't need to be thread-safe
     */
    public interface SerializeMap {
        /**
         * Returns the details to use to reconstruct the given ClassLoader in the recipient. Return null to
         * use the default details for the given ClassLoader.
         */
        @Nullable
        ClassLoaderDetails getDetails(ClassLoader target);
    }

    /**
     * Implementations don't need to be thread-safe
     */
    public interface DeserializeMap {
        /**
         * Reconstructs the ClassLoader received from the originator. Return null to use the default for the given
         * details.
         */
        @Nullable
        ClassLoader getClassLoader(ClassLoaderDetails classLoaderDetails);
    }
}
