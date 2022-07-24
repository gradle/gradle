/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NonExtensible;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
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
import org.gradle.api.tasks.Nested;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.Cast;
import org.gradle.internal.extensibility.NoConventionMapping;
import org.gradle.internal.instantiation.ClassGenerationException;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.JavaPropertyReflectionUtil;
import org.gradle.internal.reflect.MethodSet;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates a subclass of the target class to mix-in some DSL behaviour.
 *
 * <ul>
 * <li>For each property, a convention mapping is applied. These properties may have a setter method.</li>
 * <li>For each property whose getter is annotated with {@link Inject}, service instance will be injected instead. These properties may have a setter method and may be abstract.</li>
 * <li>For each mutable property as set method is generated.</li>
 * <li>For each method whose last parameter is an {@link org.gradle.api.Action}, an override is generated that accepts a {@link groovy.lang.Closure} instead.</li>
 * <li>Coercion from string to enum property is mixed in.</li>
 * <li>{@link groovy.lang.GroovyObject} and {@link DynamicObjectAware} is mixed in to the class.</li>
 * <li>An {@link ExtensionAware} implementation is added, unless {@link NonExtensible} is attached to the class.</li>
 * <li>An {@link IConventionAware} implementation is added, unless {@link NoConventionMapping} is attached to the class.</li>
 * </ul>
 */
abstract class AbstractClassGenerator implements ClassGenerator {
    private static final ImmutableSet<Class<?>> MANAGED_PROPERTY_TYPES = ImmutableSet.of(
        ConfigurableFileCollection.class,
        ConfigurableFileTree.class,
        ListProperty.class,
        SetProperty.class,
        MapProperty.class,
        RegularFileProperty.class,
        DirectoryProperty.class,
        Property.class,
        NamedDomainObjectContainer.class,
        DomainObjectSet.class
    );
    private static final Object[] NO_PARAMS = new Object[0];

    private final CrossBuildInMemoryCache<Class<?>, GeneratedClassImpl> generatedClasses;
    private final ImmutableSet<Class<? extends Annotation>> disabledAnnotations;
    private final ImmutableSet<Class<? extends Annotation>> enabledAnnotations;
    private final ImmutableMultimap<Class<? extends Annotation>, TypeToken<?>> allowedTypesForAnnotation;
    private final Function<Class<?>, GeneratedClassImpl> generator = this::generateUnderLock;
    private final PropertyRoleAnnotationHandler roleHandler;

    protected AbstractClassGenerator(
        Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
        Collection<Class<? extends Annotation>> enabledAnnotations,
        PropertyRoleAnnotationHandler roleHandler,
        CrossBuildInMemoryCache<Class<?>, GeneratedClassImpl> generatedClassesCache
    ) {
        this.generatedClasses = generatedClassesCache;
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
        this.roleHandler = roleHandler;
    }

    PropertyRoleAnnotationHandler getRoleHandler() {
        return roleHandler;
    }

    private <T> TypeToken<Provider<T>> providerOf(Class<T> providerType) {
        return new TypeToken<Provider<T>>() {
        }.where(new TypeParameter<T>() {
        }, providerType);
    }

    @Override
    public <T> GeneratedClass<? extends T> generate(Class<T> type) {
        GeneratedClassImpl generatedClass = generatedClasses.getIfPresent(type);
        if (generatedClass == null) {
            // It is possible that multiple threads will execute this branch concurrently, when the type is missing. However, the contract for `get()` below will ensure that
            // only one thread will actually generate the implementation class
            generatedClass = generatedClasses.get(type, generator);
            // Also use the generated class for itself
            generatedClasses.put(generatedClass.generatedClass, generatedClass);
        }
        return Cast.uncheckedNonnullCast(generatedClass);
    }

    private GeneratedClassImpl generateUnderLock(Class<?> type) {
        List<CustomInjectAnnotationPropertyHandler> customAnnotationPropertyHandlers = new ArrayList<>(enabledAnnotations.size());

        ServicesPropertyHandler servicesHandler = new ServicesPropertyHandler();
        InjectAnnotationPropertyHandler injectionHandler = new InjectAnnotationPropertyHandler();
        PropertyTypePropertyHandler propertyTypedHandler = new PropertyTypePropertyHandler();
        ManagedPropertiesHandler managedPropertiesHandler = new ManagedPropertiesHandler();
        NamePropertyHandler namePropertyHandler = new NamePropertyHandler();
        ExtensibleTypePropertyHandler extensibleTypeHandler = new ExtensibleTypePropertyHandler();
        DslMixInPropertyType dslMixInHandler = new DslMixInPropertyType(extensibleTypeHandler);

        // Order is significant. Injection handler should be at the end
        List<ClassGenerationHandler> handlers = new ArrayList<>(5 + enabledAnnotations.size() + disabledAnnotations.size());
        handlers.add(extensibleTypeHandler);
        handlers.add(dslMixInHandler);
        handlers.add(propertyTypedHandler);
        handlers.add(servicesHandler);
        handlers.add(namePropertyHandler);
        handlers.add(managedPropertiesHandler);
        for (Class<? extends Annotation> annotation : enabledAnnotations) {
            customAnnotationPropertyHandlers.add(new CustomInjectAnnotationPropertyHandler(annotation));
        }
        handlers.addAll(customAnnotationPropertyHandlers);
        handlers.add(injectionHandler);

        // Order is significant
        List<ClassValidator> validators = new ArrayList<>(2 + disabledAnnotations.size());
        for (Class<? extends Annotation> annotation : disabledAnnotations) {
            validators.add(new DisabledAnnotationValidator(annotation));
        }
        validators.add(new InjectionAnnotationValidator(enabledAnnotations, allowedTypesForAnnotation));

        Class<?> generatedClass;
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

            boolean shouldImplementNameProperty = namePropertyHandler.hasNameProperty();
            if (type.isInterface()) {
                if (shouldImplementNameProperty) {
                    generationVisitor.addNameConstructor();
                } else {
                    generationVisitor.addDefaultConstructor();
                }
            } else {
                for (Constructor<?> constructor : type.getConstructors()) {
                    generationVisitor.addConstructor(constructor, shouldImplementNameProperty);
                }
            }

            generatedClass = generationVisitor.generate();
        } catch (ClassGenerationException e) {
            throw e;
        } catch (Throwable e) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Could not generate a decorated class for type ");
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

        // This is expensive to calculate, so cache the result
        Class<?> enclosingClass = type.getEnclosingClass();
        Class<?> outerType;
        if (enclosingClass != null && !Modifier.isStatic(type.getModifiers())) {
            outerType = enclosingClass;
        } else {
            outerType = null;
        }

        return new GeneratedClassImpl(generatedClass, outerType, injectionHandler.getInjectedServices(), annotationsTriggeringServiceInjection.build());
    }

    protected abstract ClassInspectionVisitor start(Class<?> type);

    protected abstract InstantiationStrategy createUsingConstructor(Constructor<?> constructor);

    protected abstract InstantiationStrategy createForSerialization(Class<?> generatedType, Class<?> baseClass);

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

        visitFields(classDetails, generationHandlers);
    }

    private void visitFields(ClassDetails type, List<ClassGenerationHandler> generationHandlers) {
        if (hasRelevantFields(type)) {
            for (ClassGenerationHandler handler : generationHandlers) {
                handler.hasFields();
            }
        }
    }

    private boolean hasRelevantFields(ClassDetails type) {
        List<Field> instanceFields = type.getInstanceFields();
        if (instanceFields.isEmpty()) {
            return false;
        }
        // Ignore irrelevant synthetic metaClass field injected by the Groovy compiler
        if (instanceFields.size() == 1 && isSyntheticMetaClassField(instanceFields.get(0))) {
            return false;
        }
        return true;
    }

    private boolean isSyntheticMetaClassField(Field field) {
        return field.isSynthetic() && field.getType() == MetaClass.class;
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
            if (property.getBackingField() != null) {
                propertyMetaData.field(property.getBackingField());
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

    private static boolean isManagedProperty(PropertyMetadata property) {
        // Property is readable and without a setter of property type and the type can be created
        return property.isReadableWithoutSetterOfPropertyType() && (MANAGED_PROPERTY_TYPES.contains(property.getType()) || property.hasAnnotation(Nested.class));
    }

    private static boolean isEagerAttachProperty(PropertyMetadata property) {
        // Property is readable and without a setter of property type and getter is final, so attach owner eagerly in constructor
        // This should apply to all 'managed' types however for backwards compatibility is applied only to property types
        return property.isReadableWithoutSetterOfPropertyType() && !property.getMainGetter().shouldOverride() && isPropertyType(property.getType());
    }

    private static boolean isIneligibleForConventionMapping(PropertyMetadata property) {
        // Provider API types should have conventions set through convention() instead of
        // using convention mapping.
        return Provider.class.isAssignableFrom(property.getType());
    }

    private static boolean isLazyAttachProperty(PropertyMetadata property) {
        // Property is readable and without a setter of property type and getter is not final, so attach owner lazily when queried
        // This should apply to all 'managed' types however only the Provider types and @Nested value current implement OwnerAware
        return property.isReadableWithoutSetterOfPropertyType() && !property.getOverridableGetters().isEmpty() && (Provider.class.isAssignableFrom(property.getType()) || property.hasAnnotation(Nested.class));
    }

    private static boolean isNameProperty(PropertyMetadata property) {
        // Property is read only, called "name", has type String and getter is abstract
        return property.isReadOnly() && "name".equals(property.getName()) && property.getType() == String.class && property.getMainGetter().isAbstract();
    }

    private static boolean isPropertyType(Class<?> type) {
        return Property.class.isAssignableFrom(type) ||
            HasMultipleValues.class.isAssignableFrom(type) ||
            MapProperty.class.isAssignableFrom(type);
    }

    private static boolean isAttachableType(MethodMetadata method) {
        return Provider.class.isAssignableFrom(method.getReturnType()) || method.method.getAnnotation(Nested.class) != null;
    }

    private boolean isRoleType(PropertyMetadata property) {
        for (Class<? extends Annotation> roleAnnotation : roleHandler.getAnnotationTypes()) {
            if (property.hasAnnotation(roleAnnotation)) {
                return true;
            }
        }
        return false;
    }

    protected class GeneratedClassImpl implements GeneratedClass<Object> {
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
                    constructor.setAccessible(true);
                    builder.add(new GeneratedConstructorImpl(constructor));
                }
            }
            this.constructors = Ordering.from(new ConstructorComparator()).sortedCopy(builder.build());
        }

        @Override
        public Class<Object> getGeneratedClass() {
            return Cast.uncheckedNonnullCast(generatedClass);
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

        @Override
        public SerializationConstructor<Object> getSerializationConstructor(Class<? super Object> baseClass) {
            return new SerializationConstructorImpl(baseClass);
        }

        private class SerializationConstructorImpl implements SerializationConstructor<Object> {
            private final InstantiationStrategy strategy;

            public SerializationConstructorImpl(Class<?> baseClass) {
                this.strategy = createForSerialization(generatedClass, baseClass);
            }

            @Override
            public Object newInstance(ServiceLookup services, InstanceGenerator nested) throws InvocationTargetException, IllegalAccessException, InstantiationException {
                return strategy.newInstance(services, nested, null, NO_PARAMS);
            }
        }

        private class GeneratedConstructorImpl implements GeneratedConstructor<Object> {
            private final Constructor<?> constructor;
            private final InstantiationStrategy strategy;

            public GeneratedConstructorImpl(Constructor<?> constructor) {
                this.constructor = constructor;
                this.strategy = createUsingConstructor(constructor);
            }

            @Override
            public Object newInstance(ServiceLookup services, InstanceGenerator nested, @Nullable Describable displayName, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
                return strategy.newInstance(services, nested, displayName, params);
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

    private static class ClassMetadata {
        private final Class<?> type;
        private final Map<String, PropertyMetadata> properties = new LinkedHashMap<>();

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
        private final List<Method> overridableSetters = new ArrayList<>();
        private final List<Method> setters = new ArrayList<>();
        private final List<Method> setMethods = new ArrayList<>();
        private MethodMetadata mainGetter;
        private Field backingField;

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

        public boolean isReadOnly() {
            return isReadable() && !isWritable();
        }

        public boolean isReadableWithoutSetterOfPropertyType() {
            return isReadable() && setters.stream().noneMatch(method -> method.getParameterTypes()[0].equals(getType()));
        }

        public boolean isReadable() {
            return mainGetter != null;
        }

        public boolean isWritable() {
            return !setters.isEmpty();
        }

        public boolean hasAnnotation(Class<? extends Annotation> type) {
            if (backingField != null && backingField.getAnnotation(type) != null) {
                return true;
            } else {
                return mainGetter.method.getAnnotation(type) != null;
            }
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
                // Prefer a real method over synthetic
                mainGetter = metadata;
            } else if (mainGetter.getReturnType().equals(Boolean.TYPE) && !metadata.getReturnType().equals(Boolean.TYPE)) {
                // Prefer non-boolean over boolean
                mainGetter = metadata;
            } else if (mainGetter.getReturnType().isAssignableFrom(metadata.getReturnType())) {
                // Prefer the most specialized type
                mainGetter = metadata;
            }
        }

        public void addSetter(Method method) {
            if (method.isBridge()) {
                // Ignore bridge methods and use the real method instead
                return;
            }
            setters.add(method);
            if (!Modifier.isFinal(method.getModifiers())) {
                overridableSetters.add(method);
            }
        }

        public void addSetMethod(Method method) {
            setMethods.add(method);
        }

        public void field(Field backingField) {
            this.backingField = backingField;
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
        private boolean providesOwnToString;
        private final List<PropertyMetadata> mutableProperties = new ArrayList<>();
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
            if (!property.isWritable()) {
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
            } else if (method.getName().equals("toString") && parameterTypes.length == 0 && method.getDeclaringClass() != Object.class) {
                providesOwnToString = true;
            }
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (providesOwnDynamicObject) {
                visitor.providesOwnDynamicObjectImplementation();
            }
            if (providesOwnToString) {
                visitor.providesOwnToString();
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
                    Set<Class<?>> appliedTo = new HashSet<>();
                    for (Method setter : property.setters) {
                        if (appliedTo.add(setter.getParameterTypes()[0])) {
                            visitor.addSetMethod(property, setter);
                        }
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
            Class<?>[] methodParameterTypes = method.getParameterTypes();
            for (Method candidate : candidates) {
                Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                if (candidateParameterTypes.length != methodParameterTypes.length) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < candidateParameterTypes.length - 1; i++) {
                    if (!candidateParameterTypes[i].equals(methodParameterTypes[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return candidate;
                }
            }
            return null;
        }
    }

    private class ExtensibleTypePropertyHandler extends ClassGenerationHandler implements UnclaimedPropertyHandler {
        private Class<?> type;
        private Class<?> noMappingClass;
        private boolean conventionAware;
        private boolean extensible;
        private boolean hasExtensionAwareImplementation;
        private final List<PropertyMetadata> conventionProperties = new ArrayList<>();

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
            for (PropertyMetadata property : conventionProperties) {
                boolean applyRole = isLazyAttachProperty(property) && isRoleType(property);
                if (applyRole) {
                    visitor.instantiatesNestedObjects();
                }
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
                boolean attachProperty = isLazyAttachProperty(property);
                boolean applyRole = attachProperty && isRoleType(property);
                for (MethodMetadata getter : property.getOverridableGetters()) {
                    boolean attachOwner = attachProperty && isAttachableType(getter);
                    visitor.applyConventionMappingToGetter(property, getter, attachOwner, applyRole);
                }
                for (Method setter : property.getOverridableSetters()) {
                    visitor.applyConventionMappingToSetter(property, setter);
                }
            }
        }
    }

    private class ManagedPropertiesHandler extends ClassGenerationHandler {
        private final List<PropertyMetadata> mutableProperties = new ArrayList<>();
        private final List<PropertyMetadata> readOnlyProperties = new ArrayList<>();
        private final List<PropertyMetadata> eagerAttachProperties = new ArrayList<>();
        private final List<PropertyMetadata> ineligibleProperties = new ArrayList<>();

        private boolean hasFields;

        @Override
        public void hasFields() {
            hasFields = true;
        }

        @Override
        void visitProperty(PropertyMetadata property) {
            if (isEagerAttachProperty(property)) {
                // Property is read-only and main getter is final, so attach eagerly in constructor
                // If the getter is not final, then attach lazily in the getter
                eagerAttachProperties.add(property);
            }

            if (isIneligibleForConventionMapping(property)) {
                ineligibleProperties.add(property);
            }
        }

        @Override
        boolean claimPropertyImplementation(PropertyMetadata property) {
            // Skip properties with non-abstract getter or setter implementations
            for (MethodMetadata getter : property.getters) {
                if (getter.shouldImplement() && !getter.isAbstract()) {
                    return false;
                }
            }
            for (Method setter : property.setters) {
                if (!Modifier.isAbstract(setter.getModifiers())) {
                    return false;
                }
            }

            // Property is readable and all getters and setters are abstract
            if (isManagedProperty(property)) {
                // Abstract read-only property with managed type
                readOnlyProperties.add(property);
                return true;
            } else if (property.isReadable() && property.isWritable()) {
                // Mutable property
                mutableProperties.add(property);
                return true;
            } else {
                // Read only but unrecognized type
                return false;
            }
        }

        @Override
        void applyTo(ClassInspectionVisitor visitor) {
            if (!hasFields) {
                visitor.mixInFullyManagedState();
            }
            if (!readOnlyProperties.isEmpty()) {
                visitor.instantiatesNestedObjects();
            }
            for (PropertyMetadata property : eagerAttachProperties) {
                boolean applyRole = isRoleType(property);
                visitor.attachDuringConstruction(property, applyRole);
            }
            for (PropertyMetadata property : ineligibleProperties) {
                visitor.markPropertyAsIneligibleForConventionMapping(property);
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
                boolean applyRole = isRoleType(property);
                for (MethodMetadata getter : property.getters) {
                    visitor.applyReadOnlyManagedStateToGetter(property, getter.method, applyRole);
                }
            }
            if (!hasFields) {
                visitor.addManagedMethods(mutableProperties, readOnlyProperties);
            }
        }
    }

    private static class NamePropertyHandler extends ClassGenerationHandler {

        private PropertyMetadata nameProperty;

        @Override
        void visitProperty(PropertyMetadata property) {
            if (isNameProperty(property)) {
                nameProperty = property;
            }
        }

        @Override
        boolean claimPropertyImplementation(PropertyMetadata property) {
            if (isNameProperty(property)) {
                nameProperty = property;
                return true;
            }
            return false;
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            if (nameProperty != null) {
                visitor.addNameProperty();
            }
        }

        boolean hasNameProperty() {
            return nameProperty != null;
        }
    }

    private static class PropertyTypePropertyHandler extends ClassGenerationHandler {
        private final List<PropertyMetadata> propertyTyped = new ArrayList<>();

        @Override
        void visitProperty(PropertyMetadata property) {
            if (property.isReadable() && isPropertyType(property.getType())) {
                propertyTyped.add(property);
            }
        }

        @Override
        void applyTo(ClassGenerationVisitor visitor) {
            for (PropertyMetadata property : propertyTyped) {
                visitor.addPropertySetterOverloads(property, property.mainGetter);
            }
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
            List<Class<? extends Annotation>> matches = new ArrayList<>();
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
        final List<PropertyMetadata> serviceInjectionProperties = new ArrayList<>();

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

        void providesOwnToString();

        void mixInFullyManagedState();

        void mixInServiceInjection();

        void instantiatesNestedObjects();

        void attachDuringConstruction(PropertyMetadata property, boolean applyRole);

        void markPropertyAsIneligibleForConventionMapping(PropertyMetadata property);

        ClassGenerationVisitor builder();
    }

    protected interface InstantiationStrategy {
        Object newInstance(ServiceLookup services, InstanceGenerator nested, @Nullable Describable displayName, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException;
    }

    protected interface ClassGenerationVisitor {
        void addConstructor(Constructor<?> constructor, boolean addNameParameter);

        default void addConstructor(Constructor<?> constructor) {
            addConstructor(constructor, false);
        }

        void addDefaultConstructor();

        void addNameConstructor();

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

        void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter, boolean applyRole);

        void addManagedMethods(List<PropertyMetadata> mutableProperties, List<PropertyMetadata> readOnlyProperties);

        void applyConventionMappingToProperty(PropertyMetadata property);

        void applyConventionMappingToGetter(PropertyMetadata property, MethodMetadata getter, boolean attachOwner, boolean applyRole);

        void applyConventionMappingToSetter(PropertyMetadata property, Method setter);

        void applyConventionMappingToSetMethod(PropertyMetadata property, Method setter);

        void addSetMethod(PropertyMetadata propertyMetaData, Method setter);

        void addActionMethod(Method method);

        void addPropertySetterOverloads(PropertyMetadata property, MethodMetadata getter);

        void addNameProperty();

        Class<?> generate() throws Exception;
    }
}
