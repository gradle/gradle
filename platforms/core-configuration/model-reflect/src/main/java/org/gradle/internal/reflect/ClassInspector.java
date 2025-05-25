/*
 * Copyright 2014 the original author or authors.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class ClassInspector {

    /**
     * Extracts a view of the given class. Ignores private methods.
     */
    public static ClassDetails inspect(Class<?> type) {
        MutableClassDetails classDetails = new MutableClassDetails(type);
        visitGraph(type, classDetails);
        return classDetails;
    }

    private static void visitGraph(Class<?> type, MutableClassDetails classDetails) {
        Set<Class<?>> seen = new HashSet<Class<?>>();
        Deque<Class<?>> queue = new ArrayDeque<Class<?>>();

        // fully visit the class hierarchy before any interfaces in order to meet the contract
        // of PropertyDetails.getGetters() etc.
        queue.add(type);
        superClasses(type, queue);
        while (!queue.isEmpty()) {
            Class<?> current = queue.removeFirst();
            if (!seen.add(current)) {
                continue;
            }
            if (!current.equals(type)) {
                classDetails.superType(current);
            }
            inspectClass(current, classDetails);
            Collections.addAll(queue, current.getInterfaces());
        }
    }

    private static void superClasses(Class<?> current, Collection<Class<?>> supers) {
        Class<?> superclass = current.getSuperclass();
        while (superclass != null) {
            supers.add(superclass);
            superclass = superclass.getSuperclass();
        }
    }

    private static void inspectClass(Class<?> type, MutableClassDetails classDetails) {
        for (Method method : type.getDeclaredMethods()) {
            classDetails.method(method);

            if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            if (accessorType == PropertyAccessorType.GET_GETTER || accessorType == PropertyAccessorType.IS_GETTER) {
                String propertyName = accessorType.propertyNameFor(method);
                classDetails.property(propertyName).addGetter(method);
            } else if (accessorType == PropertyAccessorType.SETTER) {
                String propertyName = accessorType.propertyNameFor(method);
                classDetails.property(propertyName).addSetter(method);
            } else {
                classDetails.instanceMethod(method);
            }
        }
        for (Field field : type.getDeclaredFields()) {
            classDetails.field(field);
        }
    }
}
