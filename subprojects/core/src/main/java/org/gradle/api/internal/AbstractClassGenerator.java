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
import org.gradle.api.NonExtensible;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Generates a subclass of the target class to mix-in some DSL behaviour.
 *
 * <ul>
 *     <li>For each property, a convention mapping is applied. These properties may have a setter method.</li>
 *     <li>For each property whose getter is annotated with {@code Inject}, a service instance will be injected instead. These properties may have a setter method.</li>
 *     <li>For each mutable property as set method is generated.</li>
 *     <li>For each method whose last parameter is an {@link org.gradle.api.Action}, an override is generated that accepts a {@link groovy.lang.Closure} instead.</li>
 *     <li>Coercion from string to enum property is mixed in.</li>
 *     <li>{@link groovy.lang.GroovyObject} is mixed in to the class.</li>
 * </ul>
 */
public abstract class AbstractClassGenerator implements ClassGenerator {
    private static final Map<Class<?>, Map<Class<?>, Class<?>>> GENERATED_CLASSES = new HashMap<Class<?>, Map<Class<?>, Class<?>>>();
    private static final Lock CACHE_LOCK = new ReentrantLock();
    private static final Collection<String> SKIP_PROPERTIES = Arrays.asList("class", "metaClass", "conventionMapping", "convention", "asDynamicObject", "extensions");

    public <T> T newInstance(Class<T> type, Object... parameters) {
        return DirectInstantiator.instantiate(generate(type), parameters);
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
            ClassMetaData classMetaData = inspectType(type);

            ClassBuilder<T> builder = start(type, classMetaData);

            builder.startClass(classMetaData.isShouldImplementWithServiceRegistry());

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
            if (classMetaData.conventionAware && !IConventionAware.class.isAssignableFrom(type)) {
                builder.mixInConventionAware();
            }

            Class noMappingClass = Object.class;
            for (Class<?> c = type; c != null && noMappingClass == Object.class; c = c.getSuperclass()) {
                if (c.getAnnotation(NoConventionMapping.class) != null) {
                    noMappingClass = c;
                }
            }

            if (classMetaData.isShouldImplementWithServiceRegistry()) {
                builder.generateServiceRegistrySupportMethods();
            }

            Set<PropertyMetaData> conventionProperties = new HashSet<PropertyMetaData>();

            for (PropertyMetaData property : classMetaData.properties.values()) {
                if (SKIP_PROPERTIES.contains(property.name)) {
                    continue;
                }

                if (!property.getters.isEmpty() && Property.class.isAssignableFrom(property.getType())) {
                    builder.addPropertySetters(property, property.getters.get(0));
                    continue;
                }

                if (property.injector) {
                    builder.addInjectorProperty(property);
                    for (Method getter : property.getters) {
                        builder.applyServiceInjectionToGetter(property, getter);
                    }
                    for (Method setter : property.setters) {
                        builder.applyServiceInjectionToSetter(property, setter);
                    }
                    continue;
                }

                boolean needsConventionMapping = false;
                if (classMetaData.isExtensible()) {
                    for (Method getter : property.getters) {
                        if (!Modifier.isFinal(getter.getModifiers()) && !getter.getDeclaringClass().isAssignableFrom(noMappingClass)) {
                            needsConventionMapping = true;
                            break;
                        }
                    }
                }

                if (needsConventionMapping) {
                    conventionProperties.add(property);
                    builder.addConventionProperty(property);
                    for (Method getter : property.getters) {
                        builder.applyConventionMappingToGetter(property, getter);
                    }

                    for (Method setter : property.setters) {
                        if (!Modifier.isFinal(setter.getModifiers())) {
                            builder.applyConventionMappingToSetter(property, setter);
                        }
                    }
                }
            }

            Set<Method> actionMethods = classMetaData.missingOverloads;
            for (Method method : actionMethods) {
                builder.addActionMethod(method);
            }

            // Adds a set method for each mutable property
            for (PropertyMetaData property : classMetaData.properties.values()) {
                if (property.setters.isEmpty()) {
                    continue;
                }
                if (Iterable.class.isAssignableFrom(property.getType())) {
                    // Currently not supported
                    continue;
                }

                if (property.setMethods.isEmpty()) {
                    for (Method setter : property.setters) {
                        builder.addSetMethod(property, setter);
                    }
                } else if (conventionProperties.contains(property)) {
                    for (Method setMethod : property.setMethods) {
                        builder.applyConventionMappingToSetMethod(property, setMethod);
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

    protected abstract <T> ClassBuilder<T> start(Class<T> type, ClassMetaData classMetaData);

    private ClassMetaData inspectType(Class<?> type) {
        boolean isConventionAware = type.getAnnotation(NoConventionMapping.class) == null;
        boolean extensible = JavaReflectionUtil.getAnnotation(type, NonExtensible.class) == null;

        ClassMetaData classMetaData = new ClassMetaData(extensible, isConventionAware);
        inspectType(type, classMetaData);
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

    @Nullable
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
        ClassDetails classDetails = ClassInspector.inspect(type);
        boolean hasGetServicesMethod = false;
        for (Method method : classDetails.getAllMethods()) {
            if (method.getAnnotation(Inject.class) != null) {
                if (!Modifier.isPublic(method.getModifiers()) && !Modifier.isProtected(method.getModifiers())) {
                    throw new UnsupportedOperationException(String.format("Cannot attach @Inject to method %s.%s() as it is not public or protected.", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
                if (Modifier.isStatic(method.getModifiers())) {
                    throw new UnsupportedOperationException(String.format("Cannot attach @Inject to method %s.%s() as it is static.", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
            }
            if ("getServices".equals(method.getName()) && (method.getParameterTypes().length == 0) && ServiceRegistry.class.equals(method.getReturnType())) {
                hasGetServicesMethod = true;
            }
        }
        for (PropertyDetails property : classDetails.getProperties()) {
            PropertyMetaData propertyMetaData = classMetaData.property(property.getName());
            for (Method method : property.getGetters()) {
                propertyMetaData.addGetter(method);
            }
            for (Method method : property.getSetters()) {
                propertyMetaData.addSetter(method);
            }
        }
        for (Method method : classDetails.getInstanceMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1) {
                classMetaData.addCandidateSetMethod(method);
            }
            if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].equals(Action.class)) {
                classMetaData.addActionMethod(method);
            } else if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].equals(Closure.class)) {
                classMetaData.addClosureMethod(method);
            }
        }
        if (!hasGetServicesMethod) {
            for (PropertyMetaData metaData : classMetaData.properties.values()) {
                if (metaData.injector) {
                    classMetaData.shouldImplementWithServiceRegistry = true;
                }
            }
        }
    }

    protected static class ClassMetaData {
        private final Map<String, PropertyMetaData> properties = new LinkedHashMap<String, PropertyMetaData>();
        private final Set<Method> missingOverloads = new LinkedHashSet<Method>();
        private final boolean extensible;
        private final boolean conventionAware;

        private List<Method> actionMethods = new ArrayList<Method>();
        private SetMultimap<String, Method> closureMethods = LinkedHashMultimap.create();
        private List<Method> setMethods = new ArrayList<Method>();
        private boolean shouldImplementWithServiceRegistry;

        public ClassMetaData(boolean extensible, boolean conventionAware) {
            this.extensible = extensible;
            this.conventionAware = conventionAware;
        }

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

        public void addCandidateSetMethod(Method method) {
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

        public boolean providesDynamicObjectImplementation() {
            PropertyMetaData property = properties.get("asDynamicObject");
            return property != null && !property.getters.isEmpty();
        }

        public boolean isExtensible() {
            return extensible;
        }

        public boolean isConventionAware() {
            return conventionAware;
        }

        public boolean isShouldImplementWithServiceRegistry() {
            return shouldImplementWithServiceRegistry;
        }
    }

    protected static class PropertyMetaData {
        final String name;
        final List<Method> getters = new ArrayList<Method>();
        final List<Method> setters = new ArrayList<Method>();
        final List<Method> setMethods = new ArrayList<Method>();
        boolean injector;

        private PropertyMetaData(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "[property " + name + "]";
        }

        public String getName() {
            return name;
        }

        public Class<?> getType() {
            if (!getters.isEmpty()) {
                return getters.get(0).getReturnType();
            }
            return setters.get(0).getParameterTypes()[0];
        }

        public void addGetter(Method method) {
            if (getters.add(method) && method.getAnnotation(Inject.class) != null) {
                injector = true;
            }
        }

        public void addSetter(Method method) {
            setters.add(method);
        }

        public void addSetMethod(Method method) {
            setMethods.add(method);
        }
    }

    protected interface ClassBuilder<T> {
        void startClass(boolean shouldImplementWithServices);

        void addConstructor(Constructor<?> constructor) throws Exception;

        void mixInDynamicAware() throws Exception;

        void mixInConventionAware() throws Exception;

        void mixInGroovyObject() throws Exception;

        void addDynamicMethods() throws Exception;

        void addInjectorProperty(PropertyMetaData property);

        void applyServiceInjectionToGetter(PropertyMetaData property, Method getter) throws Exception;

        void applyServiceInjectionToSetter(PropertyMetaData property, Method setter) throws Exception;

        void addConventionProperty(PropertyMetaData property) throws Exception;

        void applyConventionMappingToGetter(PropertyMetaData property, Method getter) throws Exception;

        void applyConventionMappingToSetter(PropertyMetaData property, Method setter) throws Exception;

        void applyConventionMappingToSetMethod(PropertyMetaData property, Method metaMethod) throws Exception;

        void addSetMethod(PropertyMetaData propertyMetaData, Method setter) throws Exception;

        void addActionMethod(Method method) throws Exception;

        void addPropertySetters(PropertyMetaData property, Method getter) throws Exception;

        void generateServiceRegistrySupportMethods() throws Exception;

        Class<? extends T> generate() throws Exception;
    }
}
