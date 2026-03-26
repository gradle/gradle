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
 */
public class ClassHierarchyHasher {

    /**
     * Compute a hash incorporating the bytecode of every class/interface in the
     * hierarchy of {@code type}, plus arbitrary configuration strings.
     *
     * @param type the root class to hash
     * @param configStrings additional strings to include in the hash (e.g. suffix, annotation names)
     * @return a hex string hash suitable for use as a cache key
     */
    public static String computeHash(Class<?> type, List<String> configStrings) {
        Hasher hasher = Hashing.newHasher();

        // Hash configuration strings first
        for (String config : configStrings) {
            hasher.putString(config);
        }

        // Walk the class hierarchy in BFS order and hash each class's bytecode
        Set<String> seen = new HashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();

        // Add the type itself and its superclass chain
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
            hashClassBytecode(current, hasher);
            Collections.addAll(queue, current.getInterfaces());
        }

        return hasher.hash().toString();
    }

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
        List<String> configStrings = new java.util.ArrayList<>();
        configStrings.add(suffix);
        configStrings.add(Boolean.toString(decorate));
        configStrings.addAll(enabledAnnotations.stream().map(Class::getName).sorted().collect(Collectors.toList()));
        configStrings.add("---"); // separator
        configStrings.addAll(disabledAnnotations.stream().map(Class::getName).sorted().collect(Collectors.toList()));
        return computeHash(type, configStrings);
    }

    private static void hashClassBytecode(Class<?> clazz, Hasher hasher) {
        ClassLoader classLoader = clazz.getClassLoader();
        if (classLoader == null) {
            // Bootstrap class — hash the name (stable for same JVM version)
            hasher.putString(clazz.getName());
            return;
        }
        String resourceName = clazz.getName().replace('.', '/') + ".class";
        try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
            if (is != null) {
                byte[] bytes = ByteStreams.toByteArray(is);
                hasher.putBytes(bytes);
            } else {
                // Fallback: hash the name if bytecode is not accessible
                hasher.putString(clazz.getName());
            }
        } catch (IOException e) {
            hasher.putString(clazz.getName());
        }
    }
}
