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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class ClassInspector {
    /**
     * Extracts a view of the given class.
     */
    public static ClassDetails inspect(Class<?> type) {
        DefaultClassDetails classDetails = new DefaultClassDetails(type);
        visitGraph(type, classDetails);
        return classDetails;
    }

    private static void visitGraph(Class<?> type, DefaultClassDetails classDetails) {
        Set<Class<?>> seen = new HashSet<Class<?>>();
        List<Class<?>> queue = new ArrayList<Class<?>>();
        queue.add(type);
        while (!queue.isEmpty()) {
            Class<?> current = queue.remove(0);
            if (!seen.add(current)) {
                continue;
            }
            inspectClass(current, classDetails);
            for (Class<?> c : current.getInterfaces()) {
                queue.add(0, c);
            }
            if (current.getSuperclass() != null && current.getSuperclass() != Object.class) {
                queue.add(0, current.getSuperclass());
            }
        }
    }

    private static void inspectClass(Class<?> type, DefaultClassDetails classDetails) {
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

    private static class MethodSignature {
        final String name;
        final Class<?>[] params;

        private MethodSignature(String name, Class<?>[] params) {
            this.name = name;
            this.params = params;
        }

        @Override
        public boolean equals(Object obj) {
            MethodSignature other = (MethodSignature) obj;
            return other.name.equals(name) && Arrays.equals(params, other.params);
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ Arrays.hashCode(params);
        }
    }

    private static class MethodSet implements Iterable<Method> {
        private final Set<MethodSignature> signatures = new HashSet<MethodSignature>();
        private final List<Method> methods = new ArrayList<Method>();

        /**
         * @return true if the method was added, false if not
         */
        public boolean add(Method method) {
            MethodSignature methodSignature = new MethodSignature(method.getName(), method.getParameterTypes());
            if (signatures.add(methodSignature)) {
                methods.add(method);
                return true;
            }
            return false;
        }

        public Iterator<Method> iterator() {
            return methods.iterator();
        }

        public List<Method> getValues() {
            return methods;
        }

        public boolean isEmpty() {
            return methods.isEmpty();
        }
    }

    private static class DefaultClassDetails implements ClassDetails {
        private final Class<?> type;
        private final MethodSet instanceMethods = new MethodSet();
        private final Map<String, DefaultPropertyDetails> properties = new TreeMap<String, DefaultPropertyDetails>();
        private final List<Method> methods = new ArrayList<Method>();

        private DefaultClassDetails(Class<?> type) {
            this.type = type;
        }

        @Override
        public List<Method> getAllMethods() {
            return methods;
        }

        @Override
        public List<Method> getInstanceMethods() {
            return instanceMethods.getValues();
        }

        @Override
        public Set<String> getPropertyNames() {
            return properties.keySet();
        }

        @Override
        public Collection<? extends PropertyDetails> getProperties() {
            return properties.values();
        }

        @Override
        public PropertyDetails getProperty(String name) throws NoSuchPropertyException {
            DefaultPropertyDetails property = properties.get(name);
            if (property == null) {
                throw new NoSuchPropertyException(String.format("No property '%s' found on %s.", name, type));
            }
            return property;
        }

        public void method(Method method) {
            methods.add(method);
        }

        public void instanceMethod(Method method) {
            instanceMethods.add(method);
        }

        public DefaultPropertyDetails property(String propertyName) {
            DefaultPropertyDetails property = properties.get(propertyName);
            if (property == null) {
                property = new DefaultPropertyDetails(propertyName);
                properties.put(propertyName, property);
            }
            return property;
        }
    }

    private static class DefaultPropertyDetails implements PropertyDetails {
        private final String name;
        private final MethodSet getters = new MethodSet();
        private final MethodSet setters = new MethodSet();

        private DefaultPropertyDetails(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Method> getGetters() {
            return getters.getValues();
        }

        @Override
        public List<Method> getSetters() {
            return setters.getValues();
        }

        public void addGetter(Method method) {
            getters.add(method);
        }

        public void addSetter(Method method) {
            setters.add(method);
        }
    }
}
