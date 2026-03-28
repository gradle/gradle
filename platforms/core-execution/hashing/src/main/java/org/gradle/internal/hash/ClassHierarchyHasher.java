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

package org.gradle.internal.hash;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes a hash of a class's entire type hierarchy bytecode combined with
 * additional configuration strings. This is used to produce cache keys for
 * generated decorated classes, where the output depends on the full hierarchy.
 *
 * <p>Per-class bytecode hashes are cached using {@link ClassValue}, which
 * associates data with Class objects using weak references — entries are
 * automatically removed when the Class (and its classloader) is GC'd,
 * preventing memory leaks in long-lived daemons.</p>
 */
public class ClassHierarchyHasher {

    /**
     * Per-class bytecode hash, cached for the lifetime of the Class object.
     * Uses ClassValue which is GC-friendly: when a classloader is collected,
     * the associated Class objects and their ClassValue entries are too.
     */
    private static final ClassValue<HashCode> PER_CLASS_HASH = new ClassValue<HashCode>() {
        @Override
        protected HashCode computeValue(Class<?> clazz) {
            return computeClassHash(clazz);
        }
    };

    /**
     * Convenience overload that builds config strings from typical generator parameters.
     */
    public static String computeHash(
        Class<?> type,
        String suffix,
        boolean decorate,
        Set<? extends Class<?>> enabledAnnotations,
        Set<? extends Class<?>> disabledAnnotations
    ) {
        List<String> configStrings = new ArrayList<>();
        configStrings.add(suffix);
        configStrings.add(Boolean.toString(decorate));
        configStrings.addAll(enabledAnnotations.stream().map(Class::getName).sorted().collect(Collectors.toList()));
        configStrings.add("---");
        configStrings.addAll(disabledAnnotations.stream().map(Class::getName).sorted().collect(Collectors.toList()));
        return computeHash(type, configStrings);
    }

    /**
     * Compute a hash incorporating the bytecode of every class/interface in the
     * hierarchy of {@code type}, plus arbitrary configuration strings.
     */
    static String computeHash(Class<?> type, List<String> configStrings) {
        Hasher hasher = Hashing.newHasher();

        // Hash configuration strings first
        for (String config : configStrings) {
            hasher.putString(config);
        }

        // Walk the class hierarchy in BFS order and fold in each class's cached hash
        Set<String> seen = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();

        queue.add(type);
        Class<?> superclass = type.getSuperclass();
        while (superclass != null) {
            queue.add(superclass);
            superclass = superclass.getSuperclass();
        }

        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!seen.add(current.getName())) {
                continue;
            }
            hasher.putHash(PER_CLASS_HASH.get(current));
            Collections.addAll(queue, current.getInterfaces());
        }

        return hasher.hash().toString();
    }

    private static HashCode computeClassHash(Class<?> clazz) {
        ClassLoader classLoader = clazz.getClassLoader();
        if (classLoader == null) {
            // Bootstrap class — hash the name (stable for same JVM version)
            return Hashing.hashString(clazz.getName());
        }
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
            if (is != null) {
                byte[] bytes = ByteStreams.toByteArray(is);
                return Hashing.hashBytes(bytes);
            }
        } catch (IOException e) {
            // Fallback to name-based hash if bytecode is not readable
        }
        return Hashing.hashString(clazz.getName());
    }
}
