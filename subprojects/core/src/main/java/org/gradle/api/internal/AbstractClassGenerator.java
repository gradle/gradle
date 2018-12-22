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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.MethodSet;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Generates a subclass of the target class to mix-in some DSL behaviour.
 *
 * <ul>
 * <li>For each property, a convention mapping is applied. These properties may have a setter method.</li>
 * <li>For each property whose getter is annotated with {@code Inject}, a service instance will be injected instead. These properties may have a setter method and may be abstract.</li>
 * <li>For each mutable property as set method is generated.</li>
 * <li>For each method whose last parameter is an {@link org.gradle.api.Action}, an override is generated that accepts a {@link groovy.lang.Closure} instead.</li>
 * <li>Coercion from string to enum property is mixed in.</li>
 * <li>{@link groovy.lang.GroovyObject} and {@link DynamicObjectAware} is mixed in to the class.</li>
 * <li>An {@link ExtensionAware} implementation is added, unless {@link NonExtensible} is attached to the class.</li>
 * <li>An {@link IConventionAware} implementation is added, unless {@link NoConventionMapping} is attached to the class.</li>
 * </ul>
 */
public abstract class AbstractClassGenerator implements ClassGenerator {
    private static final Map<Class<?>, Map<Class<?>, Class<?>>> GENERATED_CLASSES = new HashMap<Class<?>, Map<Class<?>, Class<?>>>();
    private static final Lock CACHE_LOCK = new ReentrantLock();

    public <T> Class<? extends T> generate(Class<T> type) {
        CACHE_LOCK.lock();
        try {
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

        Class<?> subclass;
        try {
            ClassInspectionVisitor inspectionVisitor = start(type);

            ServiceInjectionPropertyHandler injectionHandler = new ServiceInjectionPropertyHandler();
            PropertyTypePropertyHandler propertyTypedHandler = new PropertyTypePropertyHandler();
            ExtensibleTypePropertyHandler extensibleTypeHandler = new ExtensibleTypePropertyHandler();
            DslMixInPropertyType dslMixInHandler = new DslMixInPropertyType(extensibleTypeHandler);
            // Order is significant. Injection handler should be at the end
            List<ClassGenerationHandler> handlers = ImmutableList.of(extensibleTypeHandler, dslMixInHandler, propertyTypedHandler, injectionHandler);
            inspectType(type, handlers, extensibleTypeHandler);
            for (ClassGenerationHandler handler : handlers) {
                handler.applyTo(inspectionVisitor);
            }

            ClassGenerationVisitor generationVisitor = inspectionVisitor.builder();

            for (ClassGenerationHandler handler : handlers) {
                handler.applyTo(generationVisitor);
            }

            for (Constructor<?> constructor : type.getConstructors()) {
                if (Modifier.isPublic(constructor.getModifiers())) {
                    generationVisitor.addConstructor(constructor);
                }
            }

            subclass = generationVisitor.generate();
        } catch (ClassGenerationException e) {
            throw e;
        } catch (Throwable e) {
            throw new ClassGenerationException(String.format("Could not generate a decorated class for class %s.", type.getName()), e);
        }

        cache.put(type, subclass);
        cache.put(subclass, subclass);
        return subclass.asSubclass(type);
    }

    protected abstract ClassInspectionVisitor start(Class<?> type);

    private void inspectType(Class<?> type, List<ClassGenerationHandler> propertyHandlers, UnclaimedPropertyHandler unclaimedHandler) {
        ClassDetails classDetails = ClassInspector.inspect(type);
        ClassMetaData classMetaData = new ClassMetaData();
        assembleProperties(classDetails, classMetaData);

        for (ClassGenerationHandler propertyHandler : propertyHandlers) {
            propertyHandler.startType(type);
        }

        for (Method method : classDetails.getAllMethods()) {
            for (ClassGenerationHandler propertyHandler : propertyHandlers) {
                propertyHandler.validateMethod(method);
            }
        }

        for (PropertyDetails property : classDetails.getProperties()) {
            PropertyMetaData propertyMetaData = classMetaData.property(property.getName());
            for (ClassGenerationHandler propertyHandler : propertyHandlers) {
                propertyHandler.visitProperty(propertyMetaData);
            }

            ClassGenerationHandler ownedBy = null;
            for (ClassGenerationHandler propertyHandler : propertyHandlers) {
                if (!propertyHandler.claimProperty(propertyMetaData)) {
                    continue;
                }
                if (ownedBy == null) {
                    ownedBy = propertyHandler;
                } else {
                    propertyHandler.ambiguous(propertyMetaData);
                    break;
                }
            }
            if (ownedBy != null) {
                continue;
            }

            unclaimedHandler.unclaimed(propertyMetaData);
            for (Method method : property.getGetters()) {
                assertNotAbstract(type, method);
            }
            for (Method method : property.getSetters()) {
                assertNotAbstract(type, method);
            }
            for (Method method : propertyMetaData.setMethods) {
                assertNotAbstract(type, method);
            }
        }

        for (Method method : classDetails.getInstanceMethods()) {
            assertNotAbstract(type, method);
            for (ClassGenerationHandler propertyHandler : propertyHandlers) {
                propertyHandler.visitInstanceMethod(method);
            }
        }
    }

    private void assembleProperties(ClassDetails classDetails, ClassMetaData classMetaData) {
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
                PropertyMetaData propertyMetaData = classMetaData.getProperty(method.getName());
                if (propertyMetaData != null) {
                    propertyMetaData.addSetMethod(method);
                }
            }
        }
    }

    private void assertNotAbstract(Class<?> type, Method method) {
        if (Modifier.isAbstract(type.getModifiers()) && Modifier.isAbstract(method.getModifiers())) {
            throw new IllegalArgumentException(String.format("Cannot have abstract method %s.%s().", method.getDeclaringClass().getSimpleName(), method.getName()));
        }
        // Else, ignore abstract methods on non-abstract classes as some other tooling (e.g. the Groovy compiler) has decided this is ok
    }

    private static class ClassMetaData {
        private final Map<String, PropertyMetaData> properties = new LinkedHashMap<String, PropertyMetaData>();

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
    }

    protected static class PropertyMetaData {
        private final String name;
        private final List<Method> getters = new ArrayList<Method>();
        private final List<Method> overridableGetters = new ArrayList<Method>();
        private final List<Method> overridableSetters = new ArrayList<Method>();
        private final List<Method> setters = new ArrayList<Method>();
        private final List<Method> setMethods = new ArrayList<Method>();
        private Method mainGetter;

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

        public boolean isReadable() {
            return mainGetter != null;
        }

        public Iterable<Method> getOverridableGetters() {
            return overridableGetters;
        }

        public Iterable<Method> getOverridableSetters() {
            return overridableSetters;
        }

        public Class<?> getType() {
            if (mainGetter != null) {
                return mainGetter.getReturnType();
            }
            return setters.get(0).getParameterTypes()[0];
        }

        public Type getGenericType() {
            if (mainGetter != null) {
                return mainGetter.getGenericReturnType();
            }
            return setters.get(0).getGenericParameterTypes()[0];
        }

        public void addGetter(Method method) {
            if (!Modifier.isFinal(method.getModifiers()) && !method.isBridge()) {
                overridableGetters.add(method);
            }
            getters.add(method);
            if (mainGetter == null) {
                mainGetter = method;
            } else if (mainGetter.isBridge() && !method.isBridge()) {
                mainGetter = method;
            }
        }

        public void addSetter(Method method) {
            for (Method setter : setters) {
                if (setter.getParameterTypes()[0].equals(method.getParameterTypes()[0])) {
                    return;
                }
            }
            setters.add(method);
            if (!Modifier.isFinal(method.getModifiers()) && !method.isBridge()) {
                overridableSetters.add(method);
            }
        }

        public void addSetMethod(Method method) {
            setMethods.add(method);
        }
    }

    private static class ClassGenerationHandler {
        void startType(Class<?> type) {
        }

        void validateMethod(Method method) {
        }

        /**
         * Collect information about an instance method. This is called for all instance methods that are not property getter or setter methods.
         */
        void visitInstanceMethod(Method method) {
        }

        /**
         * Collect information about a property. This is called for all properties of a type.
         */
        void visitProperty(PropertyMetaData property) {
        }

        /**
         * Handler can claim the property, taking responsibility for generating whatever is required to make the property work.
         * Handler is also expected to take care of validation.
         */
        boolean claimProperty(PropertyMetaData property) {
            return false;
        }

        /**
         * Called when another a handler with higher precedence has also claimed the given property.
         */
        void ambiguous(PropertyMetaData property) {
            throw new UnsupportedOperationException("Multiple matches for " + property.getName());
        }

        void applyTo(ClassInspectionVisitor visitor) {
        }

        void applyTo(ClassGenerationVisitor visitor) {
        }
    }

    private interface UnclaimedPropertyHandler {
        /**
         * Called when no handler has claimed the property.
         */
        void unclaimed(PropertyMetaData property);
    }

    private static class DslMixInPropertyType extends ClassGenerationHandler {
        private final AbstractClassGenerator.ExtensibleTypePropertyHandler extensibleTypeHandler;
        private boolean providesOwnDynamicObject;
        private boolean needDynamicAware;
        private boolean needGroovyObject;
        private final List<PropertyMetaData> mutableProperties = new ArrayList<PropertyMetaData>();
        private final MethodSet actionMethods = new MethodSet();
        private final SetMultimap<String, Method> closureMethods = LinkedHashMultimap.create();

        public DslMixInPropertyType(ExtensibleTypePropertyHandler extensibleTypeHandler) {
            this.extensibleTypeHandler = extensibleTypeHandler;
        }

        @Override
        void startType(Class<?> type) {
            needDynamicAware = !DynamicObjectAware.class.isAssignableFrom(type);
            needGroovyObject = !GroovyObject.class.isAssignableFrom(type);
        }

        @Override
        void visitProperty(PropertyMetaData property) {
            if (property.setters.isEmpty()) {
                return;
            }
            if (Iterable.class.isAssignableFrom(property.getType())) {
                // Currently not supported
                return;
            }
            mutableProperties.add(property);
        }

        @Override
        boolean claimProperty(PropertyMetaData property) {
            if (property.getName().equals("asDynamicObject")) {
                providesOwnDynamicObject = true;
                return true;
            }
            return false;
        }

        @Override
        public void visitInstanceMethod(Method method) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].equals(Action.class)) {
                actionMethods.add(method);
            } else if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1].equals(Closure.class)) {
                closureMethods.put(method.getName(), method);
            }
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (providesOwnDynamicObject) {
                visitor.providesOwnDynamicObjectImplementation();
            }
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            if (needDynamicAware) {
                visitor.mixInDynamicAware();
            }
            if (needGroovyObject) {
                visitor.mixInGroovyObject();
            }
            visitor.addDynamicMethods();
            addMissingClosureOverloads(visitor);
            addSetMethods(visitor);
        }

        private void addSetMethods(AbstractClassGenerator.ClassGenerationVisitor visitor) {
            for (PropertyMetaData property : mutableProperties) {
                if (property.setMethods.isEmpty()) {
                    for (Method setter : property.setters) {
                        visitor.addSetMethod(property, setter);
                    }
                } else if (extensibleTypeHandler.conventionProperties.contains(property)) {
                    for (Method setMethod : property.setMethods) {
                        visitor.applyConventionMappingToSetMethod(property, setMethod);
                    }
                }
            }
        }

        private void addMissingClosureOverloads(ClassGenerationVisitor visitor) {
            for (Method method : actionMethods) {
                Method overload = findClosureOverload(method, closureMethods.get(method.getName()));
                if (overload == null) {
                    visitor.addActionMethod(method);
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
    }

    private static class ExtensibleTypePropertyHandler extends ClassGenerationHandler implements UnclaimedPropertyHandler {
        private Class<?> type;
        private Class<?> noMappingClass;
        private boolean conventionAware;
        private boolean extensible;
        private boolean hasExtensionAwareImplementation;
        private final List<PropertyMetaData> conventionProperties = new ArrayList<PropertyMetaData>();

        @Override
        void startType(Class<?> type) {
            this.type = type;
            extensible = JavaReflectionUtil.getAnnotation(type, NonExtensible.class) == null;

            noMappingClass = Object.class;
            for (Class<?> c = type; c != null && noMappingClass == Object.class; c = c.getSuperclass()) {
                if (c.getAnnotation(NoConventionMapping.class) != null) {
                    noMappingClass = c;
                }
            }

            conventionAware = extensible && noMappingClass != type;
        }

        @Override
        boolean claimProperty(PropertyMetaData property) {
            if (extensible) {
                if (property.getName().equals("extensions")) {
                    for (Method getter : property.getOverridableGetters()) {
                        if (Modifier.isAbstract(getter.getModifiers())) {
                            return true;
                        }
                    }
                    hasExtensionAwareImplementation = true;
                    return true;
                }
                if (property.getName().equals("conventionMapping") || property.getName().equals("convention")) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void unclaimed(PropertyMetaData property) {
            for (Method getter : property.getOverridableGetters()) {
                if (!getter.getDeclaringClass().isAssignableFrom(noMappingClass)) {
                    conventionProperties.add(property);
                    break;
                }
            }
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (extensible) {
                visitor.mixInExtensible();
            }
            if (conventionAware) {
                visitor.mixInConventionAware();
            }
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            if (extensible && !hasExtensionAwareImplementation) {
                visitor.addExtensionsProperty();
            }
            if (conventionAware && !IConventionAware.class.isAssignableFrom(type)) {
                visitor.mixInConventionAware();
            }
            for (PropertyMetaData property : conventionProperties) {
                visitor.addConventionProperty(property);
                for (Method getter : property.getOverridableGetters()) {
                    visitor.applyConventionMappingToGetter(property, getter);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyConventionMappingToSetter(property, setter);
                }
            }
        }
    }

    private static class PropertyTypePropertyHandler extends ClassGenerationHandler {
        private final List<PropertyMetaData> propertyTyped = new ArrayList<PropertyMetaData>();

        @Override
        boolean claimProperty(PropertyMetaData property) {
            if (property.isReadable() && isModelProperty(property)) {
                propertyTyped.add(property);
                return true;
            }
            return false;
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetaData property : propertyTyped) {
                visitor.addPropertySetters(property, property.mainGetter);
            }
        }

        private boolean isModelProperty(PropertyMetaData property) {
            return Property.class.isAssignableFrom(property.getType()) ||
                    HasMultipleValues.class.isAssignableFrom(property.getType()) ||
                    MapProperty.class.isAssignableFrom(property.getType());
        }
    }

    private static class ServiceInjectionPropertyHandler extends ClassGenerationHandler {
        private boolean hasServicesProperty;
        private final List<PropertyMetaData> serviceInjectionProperties = new ArrayList<PropertyMetaData>();

        @Override
        public void validateMethod(Method method) {
            if (method.getAnnotation(Inject.class) != null) {
                if (Modifier.isStatic(method.getModifiers())) {
                    throw new UnsupportedOperationException(String.format("Cannot attach @Inject to method %s.%s() as it is static.", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
                if (PropertyAccessorType.of(method) != PropertyAccessorType.GET_GETTER) {
                    throw new UnsupportedOperationException(String.format("Cannot attach @Inject to method %s.%s() as it is not a property getter.", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
                if (Modifier.isFinal(method.getModifiers())) {
                    throw new UnsupportedOperationException(String.format("Cannot attach @Inject to method %s.%s() as it is final.", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
                if (!Modifier.isPublic(method.getModifiers()) && !Modifier.isProtected(method.getModifiers())) {
                    throw new UnsupportedOperationException(String.format("Cannot attach @Inject to method %s.%s() as it is not public or protected.", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
            }
        }

        @Override
        public boolean claimProperty(PropertyMetaData property) {
            if (property.getName().equals("services") && property.isReadable() && ServiceRegistry.class.isAssignableFrom(property.getType())) {
                hasServicesProperty = true;
                return true;
            }
            for (Method method : property.getters) {
                if (method.getAnnotation(Inject.class) != null) {
                    serviceInjectionProperties.add(property);
                    return true;
                }
            }
            return false;
        }

        @Override
        void ambiguous(PropertyMetaData property) {
            for (Method method : property.getters) {
                if (method.getAnnotation(Inject.class) != null) {
                    throw new IllegalArgumentException(String.format("Cannot use @Inject annotation on method %s.%s().", method.getDeclaringClass().getSimpleName(), method.getName()));
                }
            }
            super.ambiguous(property);
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (isShouldImplementWithServicesRegistry()) {
                visitor.mixInServiceInjection();
            }
        }

        @Override
        public void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetaData property : serviceInjectionProperties) {
                visitor.addInjectorProperty(property);
                for (Method getter : property.getOverridableGetters()) {
                    visitor.applyServiceInjectionToGetter(property, getter);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyServiceInjectionToSetter(property, setter);
                }
            }
        }

        public boolean isShouldImplementWithServicesRegistry() {
            return !serviceInjectionProperties.isEmpty() && !hasServicesProperty;
        }
    }

    protected interface ClassInspectionVisitor {
        void mixInExtensible();

        void mixInConventionAware();

        void providesOwnDynamicObjectImplementation();

        void mixInServiceInjection();

        ClassGenerationVisitor builder();
    }

    protected interface ClassGenerationVisitor {
        void addConstructor(Constructor<?> constructor) throws Exception;

        void mixInDynamicAware();

        void mixInConventionAware();

        void mixInGroovyObject();

        void addDynamicMethods();

        void addExtensionsProperty();

        void addInjectorProperty(PropertyMetaData property);

        void applyServiceInjectionToGetter(PropertyMetaData property, Method getter);

        void applyServiceInjectionToSetter(PropertyMetaData property, Method setter);

        void addConventionProperty(PropertyMetaData property);

        void applyConventionMappingToGetter(PropertyMetaData property, Method getter);

        void applyConventionMappingToSetter(PropertyMetaData property, Method setter);

        void applyConventionMappingToSetMethod(PropertyMetaData property, Method metaMethod);

        void addSetMethod(PropertyMetaData propertyMetaData, Method setter);

        void addActionMethod(Method method);

        void addPropertySetters(PropertyMetaData property, Method getter);

        Class<?> generate() throws Exception;
    }
}
