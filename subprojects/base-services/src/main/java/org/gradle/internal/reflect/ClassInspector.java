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
    public static ClassDetails inspect(Class<?> type) {
        DefaultClassDetails classDetails = new DefaultClassDetails(type);
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            inspectClass(current, classDetails);
        }
        return classDetails;
    }

    private static void inspectClass(Class<?> type, DefaultClassDetails classDetails) {
        for (Method method : type.getDeclaredMethods()) {
            classDetails.method(method);

            if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.isBridge()) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().startsWith("get")
                    && method.getName().length() > 3
                    && !method.getReturnType().equals(Void.TYPE)
                    && parameterTypes.length == 0) {
                String propertyName = method.getName().substring(3);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                classDetails.property(propertyName).addGetter(method);
            } else if (method.getName().startsWith("is")
                    && method.getName().length() > 2
                    && (method.getReturnType().equals(Boolean.class) || method.getReturnType().equals(Boolean.TYPE))
                    && parameterTypes.length == 0) {
                String propertyName = method.getName().substring(2);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                classDetails.property(propertyName).addGetter(method);
            } else if (method.getName().startsWith("set")
                    && method.getName().length() > 3
                    && parameterTypes.length == 1) {
                String propertyName = method.getName().substring(3);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                classDetails.property(propertyName).addSetter(method);
            } else {
                classDetails.instanceMethod(method);
            }
        }
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
