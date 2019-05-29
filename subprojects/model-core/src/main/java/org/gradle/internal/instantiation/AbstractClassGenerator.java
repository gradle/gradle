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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.gradle.api.Action;
import org.gradle.api.NonExtensible;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.InjectionPointQualifier;
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
import java.util.stream.Collectors;

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
    private final ImmutableMultimap<Class<? extends Annotation>, TypeToken<?>> allowedTypesForAnnotation;

    public AbstractClassGenerator(Collection<? extends InjectAnnotationHandler> allKnownAnnotations, Collection<Class<? extends Annotation>> enabledAnnotations) {
        this.enabledAnnotations = ImmutableSet.copyOf(enabledAnnotations);
        ImmutableSet.Builder<Class<? extends Annotation>> builder = ImmutableSet.builder();
        ImmutableListMultimap.Builder<Class<? extends Annotation>, TypeToken<?>> allowedTypesBuilder = ImmutableListMultimap.builder();
        for (InjectAnnotationHandler handler : allKnownAnnotations) {
            Class<? extends Annotation> annotationType = handler.getAnnotationType();
            if (!enabledAnnotations.contains(annotationType)) {
                builder.add(annotationType);
            } else {
                InjectionPointQualifier injectionPointQualifier = annotationType.getAnnotation(InjectionPointQualifier.class);
                if (injectionPointQualifier != null) {
                    for (Class<?> supportedType : injectionPointQualifier.supportedTypes()) {
                        allowedTypesBuilder.put(annotationType, TypeToken.of(supportedType));
                    }
                    for (Class<?> supportedProviderType : injectionPointQualifier.supportedProviderTypes()) {
                        allowedTypesBuilder.put(annotationType, providerOf(supportedProviderType));
                    }
                }
            }
        }
        this.disabledAnnotations = builder.build();
        this.allowedTypesForAnnotation = allowedTypesBuilder.build();
    }

    private <T> TypeToken<Provider<T>> providerOf(Class<T> providerType) {
        return new TypeToken<Provider<T>>() {}.where(new TypeParameter<T>() {}, providerType);
    }

    @Override
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

        List<CustomInjectAnnotationPropertyHandler> customAnnotationPropertyHandlers = new ArrayList<CustomInjectAnnotationPropertyHandler>(enabledAnnotations.size());

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
            customAnnotationPropertyHandlers.add(new CustomInjectAnnotationPropertyHandler(annotation));
        }
        handlers.addAll(customAnnotationPropertyHandlers);
        handlers.add(injectionHandler);

        // Order is significant
        List<ClassValidator> validators = new ArrayList<ClassValidator>(2 + disabledAnnotations.size());
        for (Class<? extends Annotation> annotation : disabledAnnotations) {
            validators.add(new DisabledAnnotationValidator(annotation));
        }
        validators.add(new InjectionAnnotationValidator(enabledAnnotations, allowedTypesForAnnotation));

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

        ImmutableList.Builder<Class<? extends Annotation>> annotationsTriggeringServiceInjection = ImmutableList.builder();
        for (CustomInjectAnnotationPropertyHandler handler : customAnnotationPropertyHandlers) {
            if (handler.isUsed()) {
                annotationsTriggeringServiceInjection.add(handler.getAnnotation());
            }
        }
        CachedClass cachedClass = new CachedClass(type, subclass, injectionHandler.getInjectedServices(), annotationsTriggeringServiceInjection.build());
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
        ClassMetadata classMetaData = new ClassMetadata(type);
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
            PropertyMetadata propertyMetaData = classMetaData.property(property.getName());
            for (ClassGenerationHandler handler : generationHandlers) {
                handler.visitProperty(propertyMetaData);
            }

            ClassGenerationHandler claimedBy = null;
            for (ClassGenerationHandler handler : generationHandlers) {
                if (!handler.claimPropertyImplementation(propertyMetaData)) {
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

    private void assembleProperties(ClassDetails classDetails, ClassMetadata classMetaData) {
        for (PropertyDetails property : classDetails.getProperties()) {
            PropertyMetadata propertyMetaData = classMetaData.property(property.getName());
            for (Method method : property.getGetters()) {
                propertyMetaData.addGetter(classMetaData.resolveTypeVariables(method));
            }
            for (Method method : property.getSetters()) {
                propertyMetaData.addSetter(method);
            }
        }
        for (Method method : classDetails.getInstanceMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 1) {
                PropertyMetadata propertyMetaData = classMetaData.getProperty(method.getName());
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
        private final List<Class<? extends Annotation>> annotationsTriggeringServiceInjection;
        private final List<GeneratedConstructor<Object>> constructors;

        public GeneratedClassImpl(Class<?> generatedClass, @Nullable Class<?> outerType, List<Class<?>> injectedServices, List<Class<? extends Annotation>> annotationsTriggeringServiceInjection) {
            this.generatedClass = generatedClass;
            this.outerType = outerType;
            this.injectedServices = injectedServices;
            this.annotationsTriggeringServiceInjection = annotationsTriggeringServiceInjection;
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
            public boolean serviceInjectionTriggeredByAnnotation(Class<? extends Annotation> serviceAnnotation) {
                return annotationsTriggeringServiceInjection.contains(serviceAnnotation);
            }

            @Override
            public Class<?> getGeneratedClass() {
                return constructor.getDeclaringClass();
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
        // This should be a list of weak references. For now, assume that all services are Gradle core services and are never collected
        private final List<Class<?>> injectedServices;
        private final List<Class<? extends Annotation>> annotationsTriggeringServiceInjection;

        CachedClass(Class<?> type, Class<?> generatedClass, List<Class<?>> injectedServices, List<Class<? extends Annotation>> annotationsTriggeringServiceInjection) {
            this.generatedClass = new WeakReference<Class<?>>(generatedClass);
            this.injectedServices = injectedServices;
            this.annotationsTriggeringServiceInjection = annotationsTriggeringServiceInjection;

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
            return new GeneratedClassImpl(generatedClass, outerType != null ? outerType.get() : null, injectedServices, annotationsTriggeringServiceInjection);
        }
    }

    private static class ClassMetadata {
        private final Class<?> type;
        private final Map<String, PropertyMetadata> properties = new LinkedHashMap<String, PropertyMetadata>();

        public ClassMetadata(Class<?> type) {
            this.type = type;
        }

        /**
         * Determines the concrete return type of the given method, resolving any type parameters.
         */
        public MethodMetadata resolveTypeVariables(Method method) {
            Type resolvedReturnType = JavaPropertyReflectionUtil.resolveMethodReturnType(type, method);
            return new MethodMetadata(method, resolvedReturnType);
        }

        @Nullable
        public PropertyMetadata getProperty(String name) {
            return properties.get(name);
        }

        public PropertyMetadata property(String name) {
            PropertyMetadata property = properties.get(name);
            if (property == null) {
                property = new PropertyMetadata(name);
                properties.put(name, property);
            }
            return property;
        }
    }

    protected static class MethodMetadata {
        private final Method method;
        private final Type returnType;

        public MethodMetadata(Method method, Type returnType) {
            this.method = method;
            this.returnType = returnType;
        }

        public String getName() {
            return method.getName();
        }

        public boolean isAbstract() {
            return Modifier.isAbstract(method.getModifiers());
        }

        boolean shouldOverride() {
            return !Modifier.isFinal(method.getModifiers()) && !method.isBridge();
        }

        boolean shouldImplement() {
            return !method.isBridge();
        }

        public Class<?> getReturnType() {
            return method.getReturnType();
        }

        public Type getGenericReturnType() {
            return returnType;
        }
    }

    protected static class PropertyMetadata {
        private final String name;
        private final List<MethodMetadata> getters = new ArrayList<>();
        private final List<MethodMetadata> overridableGetters = new ArrayList<>();
        private final List<Method> overridableSetters = new ArrayList<Method>();
        private final List<Method> setters = new ArrayList<Method>();
        private final List<Method> setMethods = new ArrayList<Method>();
        private MethodMetadata mainGetter;

        private PropertyMetadata(String name) {
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

        public MethodMetadata getMainGetter() {
            return mainGetter;
        }

        public List<MethodMetadata> getOverridableGetters() {
            return overridableGetters;
        }

        public List<Method> getOverridableSetters() {
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

        public void addGetter(MethodMetadata metadata) {
            if (metadata.shouldOverride()) {
                overridableGetters.add(metadata);
            }
            getters.add(metadata);
            if (mainGetter == null) {
                mainGetter = metadata;
            } else if (!mainGetter.shouldImplement() && metadata.shouldImplement()) {
                mainGetter = metadata;
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
        void visitProperty(PropertyMetadata property) {
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
        boolean claimPropertyImplementation(PropertyMetadata property) {
            return false;
        }

        /**
         * Called when another a handler with higher precedence has also claimed the given property.
         */
        void ambiguous(PropertyMetadata property) {
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
        void unclaimed(PropertyMetadata property);
    }

    private static class DslMixInPropertyType extends ClassGenerationHandler {
        private final AbstractClassGenerator.ExtensibleTypePropertyHandler extensibleTypeHandler;
        private boolean providesOwnDynamicObject;
        private boolean needDynamicAware;
        private boolean needGroovyObject;
        private final List<PropertyMetadata> mutableProperties = new ArrayList<PropertyMetadata>();
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
        void visitProperty(PropertyMetadata property) {
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
        boolean claimPropertyImplementation(PropertyMetadata property) {
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
            for (PropertyMetadata property : mutableProperties) {
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
        private final List<PropertyMetadata> conventionProperties = new ArrayList<PropertyMetadata>();

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
        boolean claimPropertyImplementation(PropertyMetadata property) {
            if (extensible) {
                if (property.getName().equals("extensions")) {
                    for (MethodMetadata getter : property.getOverridableGetters()) {
                        if (getter.isAbstract()) {
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
        public void unclaimed(PropertyMetadata property) {
            for (MethodMetadata getter : property.getOverridableGetters()) {
                if (!getter.method.getDeclaringClass().isAssignableFrom(noMappingClass)) {
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
            for (PropertyMetadata property : conventionProperties) {
                visitor.applyConventionMappingToProperty(property);
                for (MethodMetadata getter : property.getOverridableGetters()) {
                    visitor.applyConventionMappingToGetter(property, getter.method);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyConventionMappingToSetter(property, setter);
                }
            }
        }
    }

    private static class ManagedTypeHandler extends ClassGenerationHandler {
        private final List<PropertyMetadata> mutableProperties = new ArrayList<>();
        private final List<PropertyMetadata> readOnlyProperties = new ArrayList<>();
        private boolean hasFields;

        @Override
        public void hasFields() {
            hasFields = true;
        }

        @Override
        boolean claimPropertyImplementation(PropertyMetadata property) {
            // Skip properties with non-abstract getter or setter implementations
            for (MethodMetadata getter : property.getters) {
                if (!getter.isAbstract()) {
                    return false;
                }
            }
            for (Method setter : property.setters) {
                if (!Modifier.isAbstract(setter.getModifiers())) {
                    return false;
                }
            }
            if (property.getters.isEmpty()) {
                return false;
            }

            // Property is readable and all getters and setters are abstract

            if (property.setters.isEmpty()) {
                if (property.getType().equals(ConfigurableFileCollection.class)
                    || property.getType().equals(ListProperty.class)
                    || property.getType().equals(SetProperty.class)
                    || property.getType().equals(MapProperty.class)
                    || property.getType().equals(RegularFileProperty.class)
                    || property.getType().equals(DirectoryProperty.class)
                    || property.getType().equals(Property.class)) {
                    // Read-only property with managed type
                    readOnlyProperties.add(property);
                    return true;
                }
                return false;
            } else {
                // Mutable property
                mutableProperties.add(property);
                return true;
            }
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (!hasFields) {
                visitor.mixInManaged();
            }
            if (!readOnlyProperties.isEmpty()) {
                visitor.mixInServiceInjection();
            }
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetadata property : mutableProperties) {
                visitor.applyManagedStateToProperty(property);
                for (MethodMetadata getter : property.getters) {
                    visitor.applyManagedStateToGetter(property, getter.method);
                }
                for (Method setter : property.setters) {
                    visitor.applyManagedStateToSetter(property, setter);
                }
            }
            for (PropertyMetadata property : readOnlyProperties) {
                visitor.applyManagedStateToProperty(property);
                for (MethodMetadata getter : property.getters) {
                    visitor.applyReadOnlyManagedStateToGetter(property, getter.method);
                }
            }
            if (!hasFields) {
                visitor.addManagedMethods(mutableProperties, readOnlyProperties);
            }
        }
    }

    private static class PropertyTypePropertyHandler extends ClassGenerationHandler {
        private final List<PropertyMetadata> propertyTyped = new ArrayList<PropertyMetadata>();

        @Override
        void visitProperty(PropertyMetadata property) {
            if (property.isReadable() && isModelProperty(property)) {
                propertyTyped.add(property);
            }
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetadata property : propertyTyped) {
                visitor.addPropertySetters(property, property.mainGetter.method);
            }
        }

        private boolean isModelProperty(PropertyMetadata property) {
            return Property.class.isAssignableFrom(property.getType()) ||
                HasMultipleValues.class.isAssignableFrom(property.getType()) ||
                MapProperty.class.isAssignableFrom(property.getType());
        }
    }

    private static class InjectionAnnotationValidator implements ClassValidator {
        private final Set<Class<? extends Annotation>> annotationTypes;
        private final ImmutableMultimap<Class<? extends Annotation>, TypeToken<?>> allowedTypesForAnnotation;

        InjectionAnnotationValidator(Set<Class<? extends Annotation>> annotationTypes, ImmutableMultimap<Class<? extends Annotation>, TypeToken<?>> allowedTypesForAnnotation) {
            this.annotationTypes = annotationTypes;
            this.allowedTypesForAnnotation = allowedTypesForAnnotation;
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
            ImmutableCollection<TypeToken<?>> allowedTypes = allowedTypesForAnnotation.get(annotationType);
            if (!allowedTypes.isEmpty()) {
                Type returnType = method.getGenericReturnType();
                for (TypeToken<?> allowedType : allowedTypes) {
                    if (allowedType.isSubtypeOf(returnType)) {
                        return;
                    }
                }
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Cannot use ");
                formatter.appendAnnotation(annotationType);
                formatter.append(" annotation on property ");
                formatter.appendMethod(method);
                formatter.append(" of type ");
                formatter.append(TypeToken.of(returnType).toString());
                formatter.append(". Allowed property types: ");
                formatter.append(allowedTypes.stream()
                    .map(TypeToken::toString)
                    .sorted()
                    .collect(Collectors.joining(", "))
                );
                formatter.append(".");
                throw new IllegalArgumentException(formatter.toString());
            }
        }
    }

    private static class ServicesPropertyHandler extends ClassGenerationHandler {
        private boolean hasServicesProperty;

        @Override
        public boolean claimPropertyImplementation(PropertyMetadata property) {
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
        final List<PropertyMetadata> serviceInjectionProperties = new ArrayList<PropertyMetadata>();

        public AbstractInjectedPropertyHandler(Class<? extends Annotation> annotation) {
            this.annotation = annotation;
        }

        @Override
        public boolean claimPropertyImplementation(PropertyMetadata property) {
            for (MethodMetadata getter : property.getters) {
                if (getter.method.getAnnotation(annotation) != null) {
                    serviceInjectionProperties.add(property);
                    return true;
                }
            }
            return false;
        }

        @Override
        void ambiguous(PropertyMetadata property) {
            for (MethodMetadata getter : property.getters) {
                if (getter.method.getAnnotation(annotation) != null) {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("Cannot use ");
                    formatter.appendAnnotation(annotation);
                    formatter.append(" annotation on method ");
                    formatter.appendMethod(getter.method);
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
            for (PropertyMetadata property : serviceInjectionProperties) {
                services.add(property.getType());
            }
            return services.build();
        }

        public boolean isUsed() {
            return !serviceInjectionProperties.isEmpty();
        }

        public Class<? extends Annotation> getAnnotation() {
            return annotation;
        }
    }

    private static class InjectAnnotationPropertyHandler extends AbstractInjectedPropertyHandler {
        public InjectAnnotationPropertyHandler() {
            super(Inject.class);
        }

        @Override
        public void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetadata property : serviceInjectionProperties) {
                visitor.applyServiceInjectionToProperty(property);
                for (MethodMetadata getter : property.getOverridableGetters()) {
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
            for (PropertyMetadata property : serviceInjectionProperties) {
                visitor.applyServiceInjectionToProperty(property);
                for (MethodMetadata getter : property.getOverridableGetters()) {
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

        void applyServiceInjectionToProperty(PropertyMetadata property);

        void applyServiceInjectionToGetter(PropertyMetadata property, MethodMetadata getter);

        void applyServiceInjectionToSetter(PropertyMetadata property, Method setter);

        void applyServiceInjectionToGetter(PropertyMetadata property, Class<? extends Annotation> annotation, MethodMetadata getter);

        void applyServiceInjectionToSetter(PropertyMetadata property, Class<? extends Annotation> annotation, Method setter);

        void applyManagedStateToProperty(PropertyMetadata property);

        void applyManagedStateToGetter(PropertyMetadata property, Method getter);

        void applyManagedStateToSetter(PropertyMetadata property, Method setter);

        void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter);

        void addManagedMethods(List<PropertyMetadata> properties, List<PropertyMetadata> readOnlyProperties);

        void applyConventionMappingToProperty(PropertyMetadata property);

        void applyConventionMappingToGetter(PropertyMetadata property, Method getter);

        void applyConventionMappingToSetter(PropertyMetadata property, Method setter);

        void applyConventionMappingToSetMethod(PropertyMetadata property, Method metaMethod);

        void addSetMethod(PropertyMetadata propertyMetaData, Method setter);

        void addActionMethod(Method method);

        void addPropertySetters(PropertyMetadata property, Method getter);

        Class<?> generate() throws Exception;
    }
}
