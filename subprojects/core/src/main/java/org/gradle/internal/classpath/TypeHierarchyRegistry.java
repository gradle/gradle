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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;

public class TypeHierarchyRegistry {

    private final Map<String, Set<String>> superTypes = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> directSuperTypes = new ConcurrentHashMap<>();

    public Set<String> getSuperTypes(String type) {
        return Collections.unmodifiableSet(superTypes.computeIfAbsent(type, this::computeSuperTypes));
    }

    public void visit(byte[] classBytes) {
        ClassReader classReader = new ClassReader(classBytes);
        classReader.accept(new TypeHierarchyClassVisitor(this), 0);
    }

    private void registerSuperType(String className, String superType) {
        directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superType);
    }

    private void registerInterfaces(String className, String[] interfaces) {
        for (String superType : interfaces) {
            directSuperTypes.computeIfAbsent(className, k -> ConcurrentHashMap.newKeySet()).add(superType);
        }
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
            Class<?> clazz = Class.forName(type.replace('/', '.'));
            ImmutableSet.Builder<String> collected = ImmutableSet.builder();
            if (clazz.getSuperclass() != null) {
                collected.add(clazz.getSuperclass().getName().replace('.', '/'));
            }
            for (Class<?> superType : clazz.getInterfaces()) {
                collected.add(superType.getName().replace('.', '/'));
            }
            return collected.build();
        } catch (ClassNotFoundException e) {
            return Collections.emptySet();
        }
    }

    public static TypeHierarchyRegistry of(List<TypeHierarchyRegistry> others) {
        TypeHierarchyRegistry typeRegistry = new TypeHierarchyRegistry();
        others.forEach(other -> {
            other.superTypes.forEach((k, v) -> typeRegistry.superTypes.computeIfAbsent(k, __ -> ConcurrentHashMap.newKeySet()).addAll(v));
            other.directSuperTypes.forEach((k, v) -> typeRegistry.directSuperTypes.computeIfAbsent(k, __ -> ConcurrentHashMap.newKeySet()).addAll(v));
        });
        return typeRegistry;
    }

    private static class TypeHierarchyClassVisitor extends ClassVisitor {

        private final TypeHierarchyRegistry typeRegistry;

        public TypeHierarchyClassVisitor(TypeHierarchyRegistry typeRegistry) {
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
