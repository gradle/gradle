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

import com.google.common.collect.Lists;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class ClassInspector {
    /**
     * Extracts a view of the given class.
     */
    public static ClassDetails inspect(Class<?> type) {
        MutableClassDetails classDetails = new MutableClassDetails(type);
        visitGraph(type, classDetails);
        return classDetails;
    }

    private static void visitGraph(Class<?> type, MutableClassDetails classDetails) {
        Set<Class<?>> seen = new HashSet<Class<?>>();
        List<Class<?>> queue = new ArrayList<Class<?>>();

        // fully visit the class hierarchy before any interfaces in order to meet the contract
        // of PropertyDetails.getGetters() etc.
        queue.add(type);
        Collections.addAll(queue, superClasses(type));
        while (!queue.isEmpty()) {
            Class<?> current = queue.remove(0);
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

    private static Class<?>[] superClasses(Class<?> current) {
        List<Class<?>> supers = Lists.newArrayList();
        Class<?> superclass = current.getSuperclass();
        while (superclass != null && superclass != Object.class) {
            supers.add(superclass);
            superclass = superclass.getSuperclass();
        }
        return supers.toArray(new Class<?>[0]);
    }

    private static void inspectClass(Class<?> type, MutableClassDetails classDetails) {
        for (Method method : type.getDeclaredMethods()) {
            classDetails.method(method);

            if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.isBridge()) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            String methodName = method.getName();
            if (methodName.startsWith("get")
                && methodName.length() > 3
                && !method.getReturnType().equals(Void.TYPE)
                && parameterTypes.length == 0) {
                String propertyName = propertyName(methodName, 3);
                classDetails.property(propertyName).addGetter(method);
            } else if (methodName.startsWith("is")
                && methodName.length() > 2
                && (method.getReturnType().equals(Boolean.class) || method.getReturnType().equals(Boolean.TYPE))
                && parameterTypes.length == 0) {
                String propertyName = propertyName(methodName, 2);
                classDetails.property(propertyName).addGetter(method);
            } else if (methodName.startsWith("set")
                && methodName.length() > 3
                && parameterTypes.length == 1) {
                String propertyName = propertyName(methodName, 3);
                classDetails.property(propertyName).addSetter(method);
            } else {
                classDetails.instanceMethod(method);
            }
        }
    }

    private static String propertyName(String methodName, int beginIndex) {
        String propertyName = methodName.substring(beginIndex);
        if (Character.isUpperCase(propertyName.charAt(0)) && propertyName.length() > 1 && Character.isUpperCase(propertyName.charAt(1))) {
            // First 2 chars are upper-case -> leave name unmodified
            return propertyName;
        }
        return Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

}
