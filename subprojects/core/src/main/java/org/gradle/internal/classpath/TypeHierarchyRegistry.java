/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;
import org.objectweb.asm.ClassReader;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public class TypeHierarchyRegistry {

    private final Map<String, Set<String>> superTypes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> directSuperTypes = new ConcurrentHashMap<>();

    public Set<String> getSuperTypes(String type) {
        return Collections.unmodifiableSet(superTypes.computeIfAbsent(type, this::computeSuperTypes));
    }

    public void visit(byte[] classBytes) {
        ClassReader classReader = new ClassReader(classBytes);
        registerSuperType(classReader.getClassName(), classReader.getSuperName());
        registerInterfaces(classReader.getClassName(), classReader.getInterfaces());
    }

    private void registerSuperType(String className, @Nullable String superType) {
        if (superType != null) {
            directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superType);
        }
    }

    private void registerInterfaces(String className, String[] interfaces) {
        for (String superType : interfaces) {
            directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superType);
        }
    }
    private Set<String> computeSuperTypes(String type) {
        Queue<String> superTypes = new ArrayDeque<>(getOrLoadDirectSuperTypes(type));
        Set<String> collected = new HashSet<>();
        collected.add(type);
        while (!superTypes.isEmpty()) {
            String superType = superTypes.poll();
            if (collected.add(superType)) {
                superTypes.addAll(getOrLoadDirectSuperTypes(superType));
            }
        }
        return collected;
    }

    private Set<String> getOrLoadDirectSuperTypes(String type) {
        Set<String> superTypesOrDefault = directSuperTypes.getOrDefault(type, Collections.emptySet());
        if (superTypesOrDefault.isEmpty() && type.startsWith("org/gradle")) {
            Set<String> loadedSuperTypes = loadDirectSuperTypes(type);
            if (!loadedSuperTypes.isEmpty()) {
                directSuperTypes.put(type, loadedSuperTypes);
            }
            return loadedSuperTypes;
        }
        return superTypesOrDefault;
    }

    private static Set<String> loadDirectSuperTypes(String type) {
        try {
            Class<?> clazz = Class.forName(toClassName(type));
            ImmutableSet.Builder<String> collected = ImmutableSet.builder();
            if (clazz.getSuperclass() != null) {
                collected.add(toResourcePath(clazz.getSuperclass()));
            }
            for (Class<?> superType : clazz.getInterfaces()) {
                collected.add(toResourcePath(superType));
            }
            return collected.build();
        } catch (ClassNotFoundException e) {
            return Collections.emptySet();
        }
    }

    private static String toClassName(String type) {
        return type.replace('/', '.');
    }

    private static String toResourcePath(Class<?> clazz) {
        return clazz.getName().replace('.', '/');
    }

    public static TypeHierarchyRegistry of(List<TypeHierarchyRegistry> others) {
        TypeHierarchyRegistry typeRegistry = new TypeHierarchyRegistry();
        others.forEach(other -> {
            other.superTypes.forEach((k, v) -> typeRegistry.superTypes.computeIfAbsent(k, __ -> ConcurrentHashMap.newKeySet()).addAll(v));
            other.directSuperTypes.forEach((k, v) -> typeRegistry.directSuperTypes.computeIfAbsent(k, __ -> ConcurrentHashMap.newKeySet()).addAll(v));
        });
        return typeRegistry;
    }

    @NonNullApi
    public static class TypeHierarchyReaderWriter {

        public static TypeHierarchyRegistry readFromFile(File file) {
            try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                TypeHierarchyRegistry typeRegistry = new TypeHierarchyRegistry();
                Properties properties = new Properties();
                properties.load(inputStream);
                properties.forEach((k, v) -> {
                    String[] values = ((String) v).split(",");
                    for (String value : values) {
                        typeRegistry.registerSuperType((String) k, value);
                    }
                });
                return typeRegistry;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public static void writeToFile(TypeHierarchyRegistry registry, File file) {
            try (OutputStream outputStream = Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE)) {
                Properties properties = new Properties();
                registry.directSuperTypes.forEach((k, v) -> properties.setProperty(k, String.join(",", v)));
                properties.store(outputStream, null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
