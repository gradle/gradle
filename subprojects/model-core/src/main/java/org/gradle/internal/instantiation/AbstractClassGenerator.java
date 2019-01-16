/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.instantiation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.internal.Cast;
import org.gradle.internal.extensibility.NoConventionMapping;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;
import org.gradle.internal.reflect.MethodSet;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
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
abstract class AbstractClassGenerator implements ClassGenerator {
    private static final Map<Object, Map<Class<?>, CachedClass>> GENERATED_CLASSES = new HashMap<Object, Map<Class<?>, CachedClass>>();
    private static final Lock CACHE_LOCK = new ReentrantLock();
    private final ImmutableSet<Class<? extends Annotation>> disabledAnnotations;
    private final ImmutableSet<Class<? extends Annotation>> enabledAnnotations;

    public AbstractClassGenerator(Collection<? extends InjectAnnotationHandler> allKnownAnnotations, Collection<Class<? extends Annotation>> enabledAnnotations) {
        this.enabledAnnotations = ImmutableSet.copyOf(enabledAnnotations);
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builder();
        for (InjectAnnotationHandler handler : allKnownAnnotations) {
            if (!enabledAnnotations.contains(handler.getAnnotation())) {
                builder.add(handler.getAnnotation());
            }
        }
        this.disabledAnnotations = builder.build();
    }

    public <T> GeneratedClass<? extends T> generate(Class<T> type) {
        CACHE_LOCK.lock();
        try {
            return Cast.uncheckedCast(generateUnderLock(type));
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    private GeneratedClass<?> generateUnderLock(Class<?> type) {
        Map<Class<?>, CachedClass> cache = GENERATED_CLASSES.get(key());
        if (cache == null) {
            // Use weak keys to allow the type to be garbage collected. The entries maintain only weak and soft references to the type and the generated class
            cache = new WeakHashMap<Class<?>, CachedClass>();
            GENERATED_CLASSES.put(key(), cache);
        }
        CachedClass generatedClass = cache.get(type);
        if (generatedClass != null) {
            GeneratedClass<?> wrapper = generatedClass.asWrapper();
            if (wrapper != null) {
                return wrapper;
            }
            // Else, the generated class has been collected, so generate a new one
        }

        ServicesPropertyHandler servicesHandler = new ServicesPropertyHandler();
        InjectAnnotationPropertyHandler injectionHandler = new InjectAnnotationPropertyHandler();
        PropertyTypePropertyHandler propertyTypedHandler = new PropertyTypePropertyHandler();
        ManagedTypeHandler managedTypeHandler = new ManagedTypeHandler();
        ExtensibleTypePropertyHandler extensibleTypeHandler = new ExtensibleTypePropertyHandler();
        DslMixInPropertyType dslMixInHandler = new DslMixInPropertyType(extensibleTypeHandler);

        // Order is significant. Injection handler should be at the end
        List<ClassGenerationHandler> handlers = new ArrayList<ClassGenerationHandler>(5 + enabledAnnotations.size() + disabledAnnotations.size());
        handlers.add(extensibleTypeHandler);
        handlers.add(dslMixInHandler);
        handlers.add(propertyTypedHandler);
        handlers.add(servicesHandler);
        handlers.add(managedTypeHandler);
        for (Class<? extends Annotation> annotation : enabledAnnotations) {
            handlers.add(new CustomInjectAnnotationPropertyHandler(annotation));
        }
        handlers.add(injectionHandler);

        // Order is significant
        List<ClassValidator> validators = new ArrayList<ClassValidator>(1 + disabledAnnotations.size());
        for (Class<? extends Annotation> annotation : disabledAnnotations) {
            validators.add(new DisabledAnnotationValidator(annotation));
        }
        validators.add(new InjectionAnnotationValidator(enabledAnnotations));

        final Class<?> subclass;
        try {
            ClassInspectionVisitor inspectionVisitor = start(type);

            inspectType(type, validators, handlers, extensibleTypeHandler);
            for (ClassGenerationHandler handler : handlers) {
                handler.applyTo(inspectionVisitor);
            }

            ClassGenerationVisitor generationVisitor = inspectionVisitor.builder();

            for (ClassGenerationHandler handler : handlers) {
                handler.applyTo(generationVisitor);
            }

            if (type.isInterface()) {
                generationVisitor.addDefaultConstructor();
            } else {
                for (Constructor<?> constructor : type.getConstructors()) {
                    generationVisitor.addConstructor(constructor);
                }
            }

            subclass = generationVisitor.generate();
        } catch (ClassGenerationException e) {
            throw e;
        } catch (Throwable e) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not generate a decorated class for ");
            formatter.appendType(type);
            formatter.append(".");
            throw new ClassGenerationException(formatter.toString(), e);
        }

        List<Class<?>> injectedServices = injectionHandler.getInjectedServices();
        CachedClass cachedClass = new CachedClass(type, subclass, injectedServices);
        cache.put(type, cachedClass);
        cache.put(subclass, cachedClass);
        return cachedClass.asWrapper();
    }

    /**
     * Returns the key to use to cache the classes generated by this generator.
     */
    protected abstract Object key();

    protected abstract ClassInspectionVisitor start(Class<?> type);

    protected abstract <T> T newInstance(Constructor<T> constructor, ServiceLookup services, Instantiator nested, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException;

    private void inspectType(Class<?> type, List<ClassValidator> validators, List<ClassGenerationHandler> generationHandlers, UnclaimedPropertyHandler unclaimedHandler) {
        ClassDetails classDetails = ClassInspector.inspect(type);
        ClassMetaData classMetaData = new ClassMetaData();
        assembleProperties(classDetails, classMetaData);

        for (ClassGenerationHandler handler : generationHandlers) {
            handler.startType(type);
        }

        for (Method method : classDetails.getAllMethods()) {
            for (ClassValidator validator : validators) {
                validator.validateMethod(method, PropertyAccessorType.of(method));
            }
        }

        for (PropertyDetails property : classDetails.getProperties()) {
            PropertyMetaData propertyMetaData = classMetaData.property(property.getName());
            for (ClassGenerationHandler handler : generationHandlers) {
                handler.visitProperty(propertyMetaData);
            }

            ClassGenerationHandler claimedBy = null;
            for (ClassGenerationHandler handler : generationHandlers) {
                if (!handler.claimProperty(propertyMetaData)) {
                    continue;
                }
                if (claimedBy == null) {
                    claimedBy = handler;
                } else {
                    handler.ambiguous(propertyMetaData);
                    break;
                }
            }
            if (claimedBy != null) {
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
            for (ClassGenerationHandler handler : generationHandlers) {
                handler.visitInstanceMethod(method);
            }
        }

        visitFields(type, generationHandlers);
    }

    private void visitFields(Class<?> type, List<ClassGenerationHandler> generationHandlers) {
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                for (ClassGenerationHandler handler : generationHandlers) {
                    handler.hasFields();
                }
                return;
            }
        }
        if (type.getSuperclass() != null && type.getSuperclass() != Object.class) {
            visitFields(type.getSuperclass(), generationHandlers);
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
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot have abstract method ");
            formatter.appendMethod(method);
            formatter.append(".");
            throw new IllegalArgumentException(formatter.toString());
        }
        // Else, ignore abstract methods on non-abstract classes as some other tooling (e.g. the Groovy compiler) has decided this is ok
    }

    private class GeneratedClassImpl implements GeneratedClass<Object> {
        private final Class<?> generatedClass;
        private final Class<?> outerType;
        private final List<Class<?>> injectedServices;
        private final List<GeneratedConstructor<Object>> constructors;

        public GeneratedClassImpl(Class<?> generatedClass, @Nullable Class<?> outerType, List<Class<?>> injectedServices) {
            this.generatedClass = generatedClass;
            this.outerType = outerType;
            this.injectedServices = injectedServices;
            ImmutableList.Builder<GeneratedConstructor<Object>> builder = ImmutableList.builderWithExpectedSize(generatedClass.getDeclaredConstructors().length);
            for (final Constructor<?> constructor : generatedClass.getDeclaredConstructors()) {
                if (!constructor.isSynthetic()) {
                    builder.add(new GeneratedConstructorImpl(constructor));
                }
            }
            this.constructors = builder.build();
        }

        @Override
        public Class<Object> getGeneratedClass() {
            return Cast.uncheckedCast(generatedClass);
        }

        @Nullable
        @Override
        public Class<?> getOuterType() {
            return outerType;
        }

        @Override
        public List<GeneratedConstructor<Object>> getConstructors() {
            return constructors;
        }

        private class GeneratedConstructorImpl implements GeneratedConstructor<Object> {
            private final Constructor<?> constructor;

            public GeneratedConstructorImpl(Constructor<?> constructor) {
                this.constructor = constructor;
            }

            @Override
            public Object newInstance(ServiceLookup services, Instantiator nested, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
                return AbstractClassGenerator.this.newInstance(constructor, services, nested, params);
            }

            @Override
            public boolean requiresService(Class<?> serviceType) {
                for (Class<?> parameterType : constructor.getParameterTypes()) {
                    if (parameterType.isAssignableFrom(serviceType)) {
                        return true;
                    }
                }
                for (Class<?> injectedService : injectedServices) {
                    if (injectedService.isAssignableFrom(serviceType)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Class<?>[] getParameterTypes() {
                return constructor.getParameterTypes();
            }

            @Override
            public Type[] getGenericParameterTypes() {
                return constructor.getGenericParameterTypes();
            }

            @Nullable
            @Override
            public <S extends Annotation> S getAnnotation(Class<S> annotation) {
                return constructor.getAnnotation(annotation);
            }

            @Override
            public int getModifiers() {
                return constructor.getModifiers();
            }
        }
    }

    private class CachedClass {
        // Keep a weak reference to the generated class, to allow it to be collected
        private final WeakReference<Class<?>> generatedClass;
        private final WeakReference<Class<?>> outerType;
        // This should be a list of weak references. For now, assume that all service types are Gradle core services and are never collected
        private final List<Class<?>> injectedServices;

        CachedClass(Class<?> type, Class<?> generatedClass, List<Class<?>> injectedServices) {
            this.generatedClass = new WeakReference<Class<?>>(generatedClass);
            this.injectedServices = injectedServices;

            // This is expensive to calculate, so cache the result
            Class<?> enclosingClass = type.getEnclosingClass();
            if (enclosingClass != null && !Modifier.isStatic(type.getModifiers())) {
                outerType = new WeakReference<Class<?>>(enclosingClass);
            } else {
                outerType = null;
            }
        }

        @Nullable
        public GeneratedClassImpl asWrapper() {
            // Hold a strong reference to the class, to avoid it being collected while doing this work
            Class<?> generatedClass = this.generatedClass.get();
            if (generatedClass == null) {
                return null;
            }
            return new GeneratedClassImpl(generatedClass, outerType != null ? outerType.get() : null, injectedServices);
        }
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

    private interface ClassValidator {
        void validateMethod(Method method, PropertyAccessorType accessorType);
    }

    private static class ClassGenerationHandler {
        void startType(Class<?> type) {
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
         * Called when the type has any non-static fields.
         */
        public void hasFields() {
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
            // No supposed to happen
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
            extensible = JavaPropertyReflectionUtil.getAnnotation(type, NonExtensible.class) == null;

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
                visitor.applyConventionMappingToProperty(property);
                for (Method getter : property.getOverridableGetters()) {
                    visitor.applyConventionMappingToGetter(property, getter);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyConventionMappingToSetter(property, setter);
                }
            }
        }
    }

    private static class ManagedTypeHandler extends ClassGenerationHandler {
        private final List<PropertyMetaData> properties = new ArrayList<>();
        private boolean hasFields;

        @Override
        public void hasFields() {
            hasFields = true;
        }

        @Override
        boolean claimProperty(PropertyMetaData property) {
            for (Method getter : property.getters) {
                if (!Modifier.isAbstract(getter.getModifiers())) {
                    return false;
                }
            }
            for (Method setter : property.setters) {
                if (!Modifier.isAbstract(setter.getModifiers())) {
                    return false;
                }
            }
            if (property.setters.isEmpty()) {
                return false;
            }
            properties.add(property);
            return true;
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (!hasFields) {
                visitor.mixInManaged();
            }
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetaData property : properties) {
                visitor.applyManagedStateToProperty(property);
                for (Method getter : property.getters) {
                    visitor.applyManagedStateToGetter(property, getter);
                }
                for (Method setter : property.setters) {
                    visitor.applyManagedStateToSetter(property, setter);
                }
            }
            if (!hasFields) {
                visitor.addManagedMethods(properties);
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

    private static class InjectionAnnotationValidator implements ClassValidator {
        private final Set<Class<? extends Annotation>> annotationTypes;

        InjectionAnnotationValidator(Set<Class<? extends Annotation>> annotationTypes) {
            this.annotationTypes = annotationTypes;
        }

        @Override
        public void validateMethod(Method method, PropertyAccessorType accessorType) {
            List<Class<? extends Annotation>> matches = new ArrayList<Class<? extends Annotation>>();
            validateMethod(method, accessorType, Inject.class, matches);
            for (Class<? extends Annotation> annotationType : annotationTypes) {
                validateMethod(method, accessorType, annotationType, matches);
            }
            if (matches.size() > 1) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(matches.get(0));
                formatter.append(" and ");
                formatter.appendAnnotation(matches.get(1));
                formatter.append(" annotations together on method ");
                formatter.appendMethod(method);
                formatter.append(".");
                throw new IllegalArgumentException(formatter.toString());
            }
        }

        private void validateMethod(Method method, PropertyAccessorType accessorType, Class<? extends Annotation> annotationType, List<Class<? extends Annotation>> matches) {
            if (method.getAnnotation(annotationType) == null) {
                return;
            }
            matches.add(annotationType);
            if (Modifier.isStatic(method.getModifiers())) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(annotationType);
                formatter.append(" annotation on method ");
                formatter.appendMethod(method);
                formatter.append(" as it is static.");
                throw new IllegalArgumentException(formatter.toString());
            }
            if (accessorType != PropertyAccessorType.GET_GETTER) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(annotationType);
                formatter.append(" annotation on method ");
                formatter.appendMethod(method);
                formatter.append(" as it is not a property getter.");
                throw new IllegalArgumentException(formatter.toString());
            }
            if (Modifier.isFinal(method.getModifiers())) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(annotationType);
                formatter.append(" annotation on method ");
                formatter.appendMethod(method);
                formatter.append(" as it is final.");
                throw new IllegalArgumentException(formatter.toString());
            }
            if (!Modifier.isPublic(method.getModifiers()) && !Modifier.isProtected(method.getModifiers())) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(annotationType);
                formatter.append(" annotation on method ");
                formatter.appendMethod(method);
                formatter.append(" as it is not public or protected.");
                throw new IllegalArgumentException(formatter.toString());
            }
        }
    }

    private static class ServicesPropertyHandler extends ClassGenerationHandler {
        private boolean hasServicesProperty;

        @Override
        public boolean claimProperty(PropertyMetaData property) {
            if (property.getName().equals("services") && property.isReadable() && ServiceRegistry.class.isAssignableFrom(property.getType())) {
                hasServicesProperty = true;
                return true;
            }
            return false;
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (hasServicesProperty) {
                visitor.providesOwnServicesImplementation();
            }
        }
    }

    private static abstract class AbstractInjectedPropertyHandler extends ClassGenerationHandler {
        final Class<? extends Annotation> annotation;
        final List<PropertyMetaData> serviceInjectionProperties = new ArrayList<PropertyMetaData>();

        public AbstractInjectedPropertyHandler(Class<? extends Annotation> annotation) {
            this.annotation = annotation;
        }

        @Override
        public boolean claimProperty(PropertyMetaData property) {
            for (Method method : property.getters) {
                if (method.getAnnotation(annotation) != null) {
                    serviceInjectionProperties.add(property);
                    return true;
                }
            }
            return false;
        }

        @Override
        void ambiguous(PropertyMetaData property) {
            for (Method method : property.getters) {
                if (method.getAnnotation(annotation) != null) {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("Cannot use ");
                    formatter.appendAnnotation(annotation);
                    formatter.append(" annotation on method ");
                    formatter.appendMethod(method);
                    formatter.append(".");
                    throw new IllegalArgumentException(formatter.toString());
                }
            }
            super.ambiguous(property);
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (!serviceInjectionProperties.isEmpty()) {
                visitor.mixInServiceInjection();
            }
        }

        public List<Class<?>> getInjectedServices() {
            ImmutableList.Builder<Class<?>> services = ImmutableList.builderWithExpectedSize(serviceInjectionProperties.size());
            for (PropertyMetaData property : serviceInjectionProperties) {
                services.add(property.getType());
            }
            return services.build();
        }
    }

    private static class InjectAnnotationPropertyHandler extends AbstractInjectedPropertyHandler {
        public InjectAnnotationPropertyHandler() {
            super(Inject.class);
        }

        @Override
        public void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetaData property : serviceInjectionProperties) {
                visitor.applyServiceInjectionToProperty(property);
                for (Method getter : property.getOverridableGetters()) {
                    visitor.applyServiceInjectionToGetter(property, getter);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyServiceInjectionToSetter(property, setter);
                }
            }
        }
    }

    private static class CustomInjectAnnotationPropertyHandler extends AbstractInjectedPropertyHandler {
        public CustomInjectAnnotationPropertyHandler(Class<? extends Annotation> injectAnnotation) {
            super(injectAnnotation);
        }

        @Override
        public void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetaData property : serviceInjectionProperties) {
                visitor.applyServiceInjectionToProperty(property);
                for (Method getter : property.getOverridableGetters()) {
                    visitor.applyServiceInjectionToGetter(property, annotation, getter);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyServiceInjectionToSetter(property, annotation, setter);
                }
            }
        }
    }

    private static class DisabledAnnotationValidator implements ClassValidator {
        private final Class<? extends Annotation> annotation;

        public DisabledAnnotationValidator(Class<? extends Annotation> annotation) {
            this.annotation = annotation;
        }

        @Override
        public void validateMethod(Method method, PropertyAccessorType accessorType) {
            if (method.getAnnotation(annotation) != null) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(annotation);
                formatter.append(" annotation on method ");
                formatter.appendMethod(method);
                formatter.append(".");

                throw new IllegalArgumentException(formatter.toString());
            }
        }
    }

    protected interface ClassInspectionVisitor {
        void mixInExtensible();

        void mixInConventionAware();

        void providesOwnDynamicObjectImplementation();

        void providesOwnServicesImplementation();

        void mixInManaged();

        void mixInServiceInjection();

        ClassGenerationVisitor builder();
    }

    protected interface ClassGenerationVisitor {
        void addConstructor(Constructor<?> constructor);

        void addDefaultConstructor();

        void mixInDynamicAware();

        void mixInConventionAware();

        void mixInGroovyObject();

        void addDynamicMethods();

        void addExtensionsProperty();

        void applyServiceInjectionToProperty(PropertyMetaData property);

        void applyServiceInjectionToGetter(PropertyMetaData property, Method getter);

        void applyServiceInjectionToSetter(PropertyMetaData property, Method setter);

        void applyServiceInjectionToGetter(PropertyMetaData property, Class<? extends Annotation> annotation, Method getter);

        void applyServiceInjectionToSetter(PropertyMetaData property, Class<? extends Annotation> annotation, Method setter);

        void applyManagedStateToProperty(PropertyMetaData property);

        void applyManagedStateToGetter(PropertyMetaData property, Method getter);

        void applyManagedStateToSetter(PropertyMetaData property, Method setter);

        void addManagedMethods(List<PropertyMetaData> properties);

        void applyConventionMappingToProperty(PropertyMetaData property);

        void applyConventionMappingToGetter(PropertyMetaData property, Method getter);

        void applyConventionMappingToSetter(PropertyMetaData property, Method setter);

        void applyConventionMappingToSetMethod(PropertyMetaData property, Method metaMethod);

        void addSetMethod(PropertyMetaData propertyMetaData, Method setter);

        void addActionMethod(Method method);

        void addPropertySetters(PropertyMetaData property, Method getter);

        Class<?> generate() throws Exception;
    }
}
