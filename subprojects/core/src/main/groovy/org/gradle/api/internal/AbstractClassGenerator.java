/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractClassGenerator implements ClassGenerator {
    private static final Map<Class<?>, Map<Class<?>, Class<?>>> GENERATED_CLASSES = new HashMap<Class<?>, Map<Class<?>, Class<?>>>();
    private static final Lock CACHE_LOCK = new ReentrantLock();
    private static final Collection<String> SKIP_PROPERTIES = Arrays.asList("class", "metaClass", "conventionMapping", "convention", "asDynamicObject", "extensions");

    public <T> T newInstance(Class<T> type, Object... parameters) {
        Instantiator instantiator = new DirectInstantiator();
        return instantiator.newInstance(generate(type), parameters);
    }

    public <T> Class<? extends T> generate(Class<T> type) {
        try {
            CACHE_LOCK.lock();
            return generateUnderLock(type);
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    private <T> Class<? extends T> generateUnderLock(Class<T> type) {
        Map<Class<?>, Class<?>> cache = GENERATED_CLASSES.get(getClass());
        if (cache == null) {
            // WeakHashMap won't work here. It keeps a strong reference to the mapping value, which is the generated class in this case
            // However, the generated class has a strong reference to the source class (by extending it), so the keys will always be
            // strongly reachable while this Class is strongly reachable. Use weak references for both key and value of the mapping instead.
            cache = new ReferenceMap(AbstractReferenceMap.WEAK, AbstractReferenceMap.WEAK);
            GENERATED_CLASSES.put(getClass(), cache);
        }
        Class<?> generatedClass = cache.get(type);
        if (generatedClass != null) {
            return generatedClass.asSubclass(type);
        }

        if (Modifier.isPrivate(type.getModifiers())) {
            throw new GradleException(String.format("Cannot create a proxy class for private class '%s'.",
                    type.getSimpleName()));
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new GradleException(String.format("Cannot create a proxy class for abstract class '%s'.",
                    type.getSimpleName()));
        }

        Class<? extends T> subclass;
        try {
            ClassBuilder<T> builder = start(type);

            boolean isConventionAware = type.getAnnotation(NoConventionMapping.class) == null;

            builder.startClass(isConventionAware);

            if (!DynamicObjectAware.class.isAssignableFrom(type)) {
                if (ExtensionAware.class.isAssignableFrom(type)) {
                    throw new UnsupportedOperationException("A type that implements ExtensionAware must currently also implement DynamicObjectAware.");
                }
                builder.mixInDynamicAware();
            }
            if (!GroovyObject.class.isAssignableFrom(type)) {
                builder.mixInGroovyObject();
            }
            builder.addDynamicMethods();
            if (isConventionAware && !IConventionAware.class.isAssignableFrom(type)) {
                builder.mixInConventionAware();
            }

            Class noMappingClass = Object.class;
            for (Class<?> c = type; c != null && noMappingClass == Object.class; c = c.getSuperclass()) {
                if (c.getAnnotation(NoConventionMapping.class) != null) {
                    noMappingClass = c;
                }
            }

            Set<PropertyMetaData> settableProperties = new HashSet<PropertyMetaData>();
            Set<PropertyMetaData> conventionProperties = new HashSet<PropertyMetaData>();

            ClassMetaData classMetaData = inspectType(type);
            for (PropertyMetaData property : classMetaData.properties.values()) {
                if (SKIP_PROPERTIES.contains(property.name)) {
                    continue;
                }

                boolean needsConventionMapping = false;
                for (Method getter : property.getters) {
                    if (!Modifier.isFinal(getter.getModifiers()) && !getter.getDeclaringClass().isAssignableFrom(noMappingClass)) {
                        needsConventionMapping = true;
                        break;
                    }
                }

                if (needsConventionMapping) {
                    conventionProperties.add(property);
                    builder.addConventionProperty(property);
                    for (Method getter : property.getters) {
                        builder.overrideGetter(property, getter);
                    }
                }

                if (property.setters.isEmpty()) {
                    continue;
                }

                if (needsConventionMapping) {
                    for (Method setter : property.setters) {
                        if (!Modifier.isFinal(setter.getModifiers())) {
                            builder.overrideSetter(property, setter);
                        }
                    }
                }

                if (Iterable.class.isAssignableFrom(property.getType())) {
                    continue;
                }

                settableProperties.add(property);
            }

            Set<Method> actionMethods = classMetaData.missingOverloads;
            for (Method method : actionMethods) {
                builder.addActionMethod(method);
            }

            // Adds a set method for each mutable property
            for (PropertyMetaData property : settableProperties) {
                if (property.setMethods.isEmpty()) {
                    for (Method setter : property.setters) {
                        builder.addSetMethod(property, setter);
                    }
                } else if (conventionProperties.contains(property)) {
                    for (Method setMethod : property.setMethods) {
                        builder.overrideSetMethod(property, setMethod);
                    }
                }
            }

            for (Constructor<?> constructor : type.getConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers())) {
                    builder.addConstructor(constructor);
                }
            }

            subclass = builder.generate();
        } catch (Throwable e) {
            throw new GradleException(String.format("Could not generate a proxy class for class %s.", type.getName()), e);
        }

        cache.put(type, subclass);
        cache.put(subclass, subclass);
        return subclass;
    }

    protected abstract <T> ClassBuilder<T> start(Class<T> type);

    private ClassMetaData inspectType(Class<?> type) {
        ClassMetaData classMetaData = new ClassMetaData();
        for (Class<?> current = type; current != Object.class; current = current.getSuperclass()) {
            inspectType(current, classMetaData);
        }
        attachSetMethods(classMetaData);
        findMissingClosureOverloads(classMetaData);
        classMetaData.complete();
        return classMetaData;
    }

    private void findMissingClosureOverloads(ClassMetaData classMetaData) {
        for (Method method : classMetaData.actionMethods) {
            Method overload = findClosureOverload(method, classMetaData.closureMethods.get(method.getName()));
            if (overload == null) {
                classMetaData.actionMethodRequiresOverload(method);
            }
        }
    }

    private Method findClosureOverload(Method method, Collection<Method> candidates) {
        for (Method candidate : candidates) {
            if (candidate.getParameterTypes().length != method.getParameterTypes().length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; matches && i < candidate.getParameterTypes().length - 1; i++) {
                if (!candidate.getParameterTypes()[i].equals(method.getParameterTypes()[i])) {
                    matches = false;
                }
            }
            if (matches) {
                return candidate;
            }
        }
        return null;
    }

    private void attachSetMethods(ClassMetaData classMetaData) {
        for (Method method : classMetaData.setMethods) {
            PropertyMetaData property = classMetaData.getProperty(method.getName());
            if (property != null) {
                property.addSetMethod(method);
            }
        }
    }

    private void inspectType(Class<?> type, ClassMetaData classMetaData) {
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().startsWith("get")
                    && method.getName().length() > 3
                    && !method.getReturnType().equals(Void.TYPE)
                    && parameterTypes.length == 0) {
                String propertyName = method.getName().substring(3);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                classMetaData.property(propertyName).addGetter(method);
            } else if (method.getName().startsWith("is")
                    && method.getName().length() > 2
                    && (method.getReturnType().equals(Boolean.class) || method.getReturnType().equals(Boolean.TYPE))
                    && parameterTypes.length == 0) {
                String propertyName = method.getName().substring(2);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                classMetaData.property(propertyName).addGetter(method);
            } else if (method.getName().startsWith("set")
                    && method.getName().length() > 3
                    && parameterTypes.length == 1) {
                String propertyName = method.getName().substring(3);
                propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
                classMetaData.property(propertyName).addSetter(method);
            } else {
                if (parameterTypes.length == 1) {
                    classMetaData.addSetMethod(method);
                }
                if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length-1].equals(Action.class)) {
                    classMetaData.addActionMethod(method);
                } else if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length-1].equals(Closure.class)) {
                    classMetaData.addClosureMethod(method);
                }
            }
        }
    }

    private static class ClassMetaData {
        final Map<String, PropertyMetaData> properties = new LinkedHashMap<String, PropertyMetaData>();
        final Set<Method> missingOverloads = new LinkedHashSet<Method>();
        MethodSet actionMethods = new MethodSet();
        SetMultimap<String, Method> closureMethods = LinkedHashMultimap.create();
        MethodSet setMethods = new MethodSet();

        @Nullable
        public PropertyMetaData getProperty(String name) {
            return properties.get(name);
        }

        public PropertyMetaData property(String name) {
            PropertyMetaData property = properties.get(name);
            if (property == null) {
                property = new PropertyMetaData(name);
                properties.put(name, property);
            }
            return property;
        }

        public void addActionMethod(Method method) {
            actionMethods.add(method);
        }

        public void addClosureMethod(Method method) {
            closureMethods.put(method.getName(), method);
        }

        public void addSetMethod(Method method) {
            setMethods.add(method);
        }

        public void complete() {
            setMethods = null;
            actionMethods = null;
            closureMethods = null;
        }

        public void actionMethodRequiresOverload(Method method) {
            missingOverloads.add(method);
        }
    }

    protected static class PropertyMetaData {
        final String name;
        final MethodSet getters = new MethodSet();
        final MethodSet setters = new MethodSet();
        final MethodSet setMethods = new MethodSet();

        private PropertyMetaData(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format("[property %s]", name);
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            if (!getters.isEmpty()) {
                return getters.getValues().get(0).getReturnType();
            }
            return setters.getValues().get(0).getParameterTypes()[0];
        }

        public void addGetter(Method method) {
            getters.add(method);
        }

        public void addSetter(Method method) {
            setters.add(method);
        }

        public void addSetMethod(Method method) {
            setMethods.add(method);
        }
    }

    private static class MethodSet implements Iterable<Method> {
        private final Set<MethodSignature> signatures = new HashSet<MethodSignature>();
        private final List<Method> methods = new ArrayList<Method>();

        public void add(Method method) {
            MethodSignature methodSignature = new MethodSignature(method.getName(), method.getParameterTypes());
            if (signatures.add(methodSignature)) {
                methods.add(method);
            }
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

    protected interface ClassBuilder<T> {
        void startClass(boolean isConventionAware);

        void addConstructor(Constructor<?> constructor) throws Exception;

        void mixInDynamicAware() throws Exception;

        void mixInConventionAware() throws Exception;

        void mixInGroovyObject() throws Exception;

        void addDynamicMethods() throws Exception;

        void addConventionProperty(PropertyMetaData property) throws Exception;

        void overrideGetter(PropertyMetaData property, Method getter) throws Exception;

        void overrideSetter(PropertyMetaData property, Method setter) throws Exception;

        void overrideSetMethod(PropertyMetaData property, Method metaMethod) throws Exception;

        void addSetMethod(PropertyMetaData propertyMetaData, Method setter) throws Exception;

        void addActionMethod(Method method) throws Exception;

        Class<? extends T> generate() throws Exception;
    }
}
