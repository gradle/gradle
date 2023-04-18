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
import org.gradle.internal.classpath.InstrumentingClasspathFileTransformer.Policy;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;

public class TypeCollectingClasspathFileTransformer implements ClasspathFileTransformer {

    private final ClasspathWalker classpathWalker;
    private final Policy policy;

    public TypeCollectingClasspathFileTransformer(ClasspathWalker classpathWalker, Policy policy) {
        this.classpathWalker = classpathWalker;
        this.policy = policy;
    }

    @Override
    public File transform(File source, FileSystemLocationSnapshot sourceSnapshot, File cacheDir, TypeRegistry typeRegistry) {
        try {
            classpathWalker.visit(source, entry -> {
                if (!policy.includeEntry(entry)) {
                    return;
                }
                if (entry.getName().endsWith(".class")) {
                    ClassReader classReader = new ClassReader(entry.getContent());
                    classReader.accept(new TypeCollectingClassVisitor(typeRegistry), 0);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return source;
    }

    public static class TypeRegistry {

        private final Map<String, Set<String>> subTypes = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> superTypes = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> directSubTypes = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> directSuperTypes = new ConcurrentHashMap<>();

        public void registerSuperType(String className, String superType) {
            directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superType);
            directSubTypes.computeIfAbsent(superType, k -> ConcurrentHashMap.newKeySet()).add(className);
        }

        public void registerSuperType(String className, Class<?> superType) {
            if (superType != null) {
                String superTypeName = superType.getName().replace(".", "/");
                directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superTypeName);
                directSubTypes.computeIfAbsent(superTypeName, k -> ConcurrentHashMap.newKeySet()).add(className);
            }
        }

        public void registerInterfaces(String className, String[] interfaces) {
            for (String superType : interfaces) {
                directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superType);
                directSubTypes.computeIfAbsent(superType, k -> ConcurrentHashMap.newKeySet()).add(className);
            }
        }

        public void registerInterfaces(String className, Class<?>[] interfaces) {
            for (Class<?> superType : interfaces) {
                String superTypeName = superType.getName().replace(".", "/");
                directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superTypeName);
                directSubTypes.computeIfAbsent(superTypeName, k -> ConcurrentHashMap.newKeySet()).add(className);
            }
        }

        public Map<String, Set<String>> getDirectSuperTypes() {
            return Collections.unmodifiableMap(directSuperTypes);
        }

        /**
         * Returned collection contains all subtypes + type passed as a parameter
         */
        public Set<String> getSubTypes(String type) {
            return Collections.unmodifiableSet(subTypes.computeIfAbsent(type, this::computeSubTypes));
        }

        public Set<String> getSuperTypes(String type) {
            return Collections.unmodifiableSet(superTypes.computeIfAbsent(type, this::computeSuperTypes));
        }

        private Set<String> computeSubTypes(String type) {
            Queue<String> subTypes = new LinkedList<>(directSubTypes.getOrDefault(type, Collections.emptySet()));
            Set<String> collected = new HashSet<>();
            collected.add(type);
            while (!subTypes.isEmpty()) {
                String subType = subTypes.poll();
                if (collected.add(subType)) {
                    subTypes.addAll(directSubTypes.getOrDefault(subType, Collections.emptySet()));
                }
            }
            return collected;
        }

        private Set<String> computeSuperTypes(String type) {
            Queue<String> superTypes = new LinkedList<>(getOrLoadDirectSuperTypes(type));
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
                try {
                    Class<?> clazz = Class.forName(type.replace('/', '.'));
                    ImmutableSet.Builder<String> collected = ImmutableSet.builder();
                    if (clazz.getSuperclass() != null) {
                        collected.add(clazz.getSuperclass().getName().replace('.', '/'));
                    }
                    for (Class<?> superType : clazz.getInterfaces()) {
                        collected.add(superType.getName().replace('.', '/'));
                    }
                    Set<String> collectedSet = collected.build();
                    directSuperTypes.put(type, collectedSet);
                    return collectedSet;
                } catch (ClassNotFoundException e) {
                    return Collections.emptySet();
                }
            }
            return superTypesOrDefault;
        }
    }

    private static class TypeCollectingClassVisitor extends ClassVisitor {

        private final TypeRegistry typeRegistry;

        public TypeCollectingClassVisitor(TypeRegistry typeRegistry) {
            super(ASM_LEVEL);
            this.typeRegistry = typeRegistry;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            typeRegistry.registerSuperType(name, superName);
            typeRegistry.registerInterfaces(name, interfaces);
        }
    }
}
