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

package org.gradle.internal.reflect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.internal.Cast;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.Set;

public class Types {
    private static final Collection<Class<?>> OBJECT_TYPE = ImmutableList.<Class<?>>of(Object.class);

    /**
     * Visits all types in a type hierarchy in breadth-first order, super-classes first and then implemented interfaces.
     *
     * @param clazz the type of whose type hierarchy to visit.
     * @param visitor the visitor to call for each type in the hierarchy.
     */
    public static <T> void walkTypeHierarchy(Class<T> clazz, TypeVisitor<? extends T> visitor) {
        walkTypeHierarchy(clazz, OBJECT_TYPE, visitor);
    }

    /**
     * Visits all types in a type hierarchy in breadth-first order, super-classes first and then implemented interfaces.
     *
     * @param clazz the type of whose type hierarchy to visit.
     * @param excludedTypes the types not to walk when encountered in the hierarchy.
     * @param visitor the visitor to call for each type in the hierarchy.
     */
    public static <T> void walkTypeHierarchy(Class<T> clazz, Collection<Class<?>> excludedTypes, TypeVisitor<? extends T> visitor) {
        Set<Class<?>> seenInterfaces = Sets.newHashSet();
        Queue<Class<? super T>> queue = new ArrayDeque<Class<? super T>>();
        queue.add(clazz);
        Class<? super T> type;
        while ((type = queue.poll()) != null) {
            if (excludedTypes.contains(type)) {
                continue;
            }

            visitor.visitType(type);

            Class<? super T> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (seenInterfaces.add(iface)) {
                    queue.add(Cast.<Class<? super T>>uncheckedCast(iface));
                }
            }
        }
    }

    @FunctionalInterface
    public interface TypeVisitor<T> {
        void visitType(Class<? super T> type);
    }
}
