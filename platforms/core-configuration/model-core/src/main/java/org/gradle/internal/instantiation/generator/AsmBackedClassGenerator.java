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

import com.google.common.collect.ImmutableSet;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.IsolatedAction;
import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GeneratedSubclass;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.provider.support.LazyGroovySupport;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.services.ServiceReference;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.extensibility.ConventionAwareHelper;
import org.gradle.internal.instantiation.ClassGenerationException;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.state.Managed;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.OwnerAware;
import org.gradle.model.internal.asm.AsmClassGenerator;
import org.gradle.model.internal.asm.BytecodeFragment;
import org.gradle.model.internal.asm.ClassGeneratorSuffixRegistry;
import org.gradle.model.internal.asm.ClassVisitorScope;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.gradle.util.internal.ConfigureUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static groovy.lang.MetaProperty.getSetterName;
import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.getterSignature;
import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.signature;
import static org.gradle.util.internal.CollectionUtils.collectArray;
import static org.gradle.util.internal.CollectionUtils.findFirst;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.objectweb.asm.Opcodes.H_INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.V1_8;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;
import static org.objectweb.asm.Type.getDescriptor;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;
import static sun.reflect.ReflectionFactory.getReflectionFactory;

public class AsmBackedClassGenerator extends AbstractClassGenerator {
    private static final ThreadLocal<ObjectCreationDetails> SERVICES_FOR_NEXT_OBJECT = new ThreadLocal<>();
    private static final AtomicReference<CrossBuildInMemoryCache<Class<?>, GeneratedClassImpl>> GENERATED_CLASSES_CACHES = new AtomicReference<>();
    private final boolean decorate;
    private final String suffix;
    private final int factoryId;

    private static final String GET_DISPLAY_NAME_FOR_NEXT_METHOD_NAME = "getDisplayNameForNext";

    // Used by generated code, see ^
    @SuppressWarnings("unused")
    @Nullable
    public static Describable getDisplayNameForNext() {
        ObjectCreationDetails details = SERVICES_FOR_NEXT_OBJECT.get();
        if (details == null) {
            return null;
        }
        return details.displayName;
    }

    private static final String GET_SERVICES_FOR_NEXT_METHOD_NAME = "getServicesForNext";

    // Used by generated code, see ^
    @SuppressWarnings("unused")
    public static ServiceLookup getServicesForNext() {
        return SERVICES_FOR_NEXT_OBJECT.get().services;
    }

    private static final String GET_FACTORY_FOR_NEXT_METHOD_NAME = "getFactoryForNext";

    // Used by generated code, see ^
    @SuppressWarnings("unused")
    public static ManagedObjectFactory getFactoryForNext() {
        ObjectCreationDetails details = SERVICES_FOR_NEXT_OBJECT.get();
        return new ManagedObjectFactory(details.services, details.instantiator, details.roleHandler);
    }

    private AsmBackedClassGenerator(
        boolean decorate, String suffix,
        Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
        Collection<Class<? extends Annotation>> enabledInjectAnnotations,
        PropertyRoleAnnotationHandler roleHandler,
        CrossBuildInMemoryCache<Class<?>, GeneratedClassImpl> generatedClasses,
        int factoryId
    ) {
        super(allKnownAnnotations, enabledInjectAnnotations, roleHandler, generatedClasses);
        this.decorate = decorate;
        this.suffix = suffix;
        this.factoryId = factoryId;
    }

    /**
     * Returns a generator that applies DSL mix-in, extensibility and service injection for generated classes.
     */
    static ClassGenerator decorateAndInject(
        Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
        PropertyRoleAnnotationHandler roleHandler,
        Collection<Class<? extends Annotation>> enabledInjectAnnotations,
        CrossBuildInMemoryCacheFactory cacheFactory,
        int factoryId
    ) {
        String suffix;
        CrossBuildInMemoryCache<Class<?>, GeneratedClassImpl> generatedClasses;
        if (enabledInjectAnnotations.isEmpty()) {
            // TODO wolfs: We use `_Decorated` here, since IDEA import currently relies on this
            // See https://github.com/gradle/gradle/issues/8244
            suffix = "_Decorated";
            // Because the same suffix is used for all decorating class generator instances, share the same cache as well
            if (GENERATED_CLASSES_CACHES.get() == null) {
                if (GENERATED_CLASSES_CACHES.compareAndSet(null, cacheFactory.newClassMap())) {
                    ClassGeneratorSuffixRegistry.register(suffix);
                }
            }
            generatedClasses = GENERATED_CLASSES_CACHES.get();
        } else {
            // TODO - the suffix should be a deterministic function of the known and enabled annotations
            // For now, just assign using a counter
            suffix = ClassGeneratorSuffixRegistry.assign("$Decorated");
            generatedClasses = cacheFactory.newClassMap();
        }

        return new AsmBackedClassGenerator(true, suffix, allKnownAnnotations, enabledInjectAnnotations, roleHandler, generatedClasses, factoryId);
    }

    /**
     * Returns a generator that applies service injection only for generated classes, and will generate classes only if required.
     */
    static ClassGenerator injectOnly(
        Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
        PropertyRoleAnnotationHandler roleHandler,
        Collection<Class<? extends Annotation>> enabledInjectAnnotations,
        CrossBuildInMemoryCacheFactory cacheFactory,
        int factoryId
    ) {
        // TODO - the suffix should be a deterministic function of the known and enabled annotations
        // For now, just assign using a counter
        String suffix = ClassGeneratorSuffixRegistry.assign("$Inject");
        return new AsmBackedClassGenerator(false, suffix, allKnownAnnotations, enabledInjectAnnotations, roleHandler, cacheFactory.newClassMap(), factoryId);
    }

    @Override
    protected InstantiationStrategy createUsingConstructor(Constructor<?> constructor) {
        return new InvokeConstructorStrategy(constructor, getRoleHandler());
    }

    @Override
    protected InstantiationStrategy createForSerialization(Class<?> generatedType, Class<?> baseClass) {
        Constructor<?> constructor;
        try {
            constructor = getReflectionFactory().newConstructorForSerialization(generatedType, baseClass.getDeclaredConstructor());
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        Method method = findFirst(generatedType.getDeclaredMethods(), m -> m.getName().equals(ClassBuilderImpl.INIT_METHOD));
        assert method != null;
        method.setAccessible(true);

        return new InvokeSerializationConstructorAndInitializeFieldsStrategy(constructor, method, getRoleHandler());
    }

    @Override
    protected ClassInspectionVisitor start(Class<?> type) {
        if (type.isAnnotation() || type.isEnum()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is not a class or interface.");
            throw new ClassGenerationException(formatter.toString());
        }
        return new ClassInspectionVisitorImpl(type, decorate, suffix, factoryId);
    }

    private static class AttachedProperty {

        public static AttachedProperty of(PropertyMetadata property, boolean applyRole) {
            return new AttachedProperty(property, applyRole);
        }

        public final PropertyMetadata property;
        public final boolean applyRole;

        private AttachedProperty(PropertyMetadata property, boolean applyRole) {
            this.property = property;
            this.applyRole = applyRole;
        }
    }

    private static class ClassInspectionVisitorImpl implements ClassInspectionVisitor {
        private final Class<?> type;
        private final boolean decorate;
        private final String suffix;
        private final int factoryId;
        private boolean extensible;
        private boolean serviceInjection;
        private boolean conventionAware;
        private boolean managed;
        private boolean providesOwnDynamicObjectImplementation;
        private boolean providesOwnServicesImplementation;
        private boolean providesOwnToStringImplementation;
        private boolean requiresFactory;
        private final List<AttachedProperty> propertiesToAttachAtConstruction = new ArrayList<>();
        private final List<AttachedProperty> propertiesToAttachOnDemand = new ArrayList<>();
        private final List<PropertyMetadata> ineligibleProperties = new ArrayList<>();

        public ClassInspectionVisitorImpl(Class<?> type, boolean decorate, String suffix, int factoryId) {
            this.type = type;
            this.decorate = decorate;
            this.suffix = suffix;
            this.factoryId = factoryId;
        }

        @Override
        public void mixInServiceInjection() {
            serviceInjection = true;
        }

        @Override
        public void mixInExtensible() {
            if (decorate) {
                extensible = true;
            }
        }

        @Override
        public void mixInConventionAware() {
            if (decorate) {
                conventionAware = true;
            }
        }

        @Override
        public void mixInFullyManagedState() {
            managed = true;
        }

        @Override
        public void providesOwnServicesImplementation() {
            providesOwnServicesImplementation = true;
        }

        @Override
        public void providesOwnDynamicObjectImplementation() {
            providesOwnDynamicObjectImplementation = true;
        }

        @Override
        public void providesOwnToString() {
            providesOwnToStringImplementation = true;
        }

        @Override
        public void instantiatesNestedObjects() {
            requiresFactory = true;
        }

        @Override
        public void attachDuringConstruction(PropertyMetadata property, boolean applyRole) {
            attachTo(propertiesToAttachAtConstruction, property, applyRole);
        }

        @Override
        public void attachOnDemand(PropertyMetadata property, boolean applyRole) {
            attachTo(propertiesToAttachOnDemand, property, applyRole);
        }

        private void attachTo(List<AttachedProperty> properties, PropertyMetadata property, boolean applyRole) {
            properties.add(AttachedProperty.of(property, applyRole));
            if (applyRole) {
                requiresFactory = true;
            }
        }

        @Override
        public void markPropertyAsIneligibleForConventionMapping(PropertyMetadata property) {
            ineligibleProperties.add(property);
        }

        @Override
        public ClassGenerationVisitor builder() {
            if (!decorate && !serviceInjection && !Modifier.isAbstract(type.getModifiers())) {
                // Don't need to generate a subclass
                return new NoOpBuilder(type);
            }

            int modifiers = type.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node(type);
                formatter.append(" is private.");
                throw new ClassGenerationException(formatter.toString());
            }
            if (Modifier.isFinal(modifiers)) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node(type);
                formatter.append(" is final.");
                throw new ClassGenerationException(formatter.toString());
            }
            boolean requiresServicesMethod = (extensible || serviceInjection) && !providesOwnServicesImplementation;
            boolean requiresToString = !providesOwnToStringImplementation;
            ClassBuilderImpl builder = new ClassBuilderImpl(
                new AsmClassGenerator(type, suffix),
                decorate,
                factoryId,
                extensible,
                conventionAware,
                managed,
                providesOwnDynamicObjectImplementation,
                requiresToString,
                requiresServicesMethod,
                requiresFactory,
                propertiesToAttachAtConstruction,
                propertiesToAttachOnDemand,
                ineligibleProperties
            );
            builder.startClass();
            return builder;
        }
    }

    @NonNullApi
    private static class ClassBuilderImpl extends ClassVisitorScope implements ClassGenerationVisitor {
        public static final int PV_FINAL_STATIC = ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC;
        private static final Set<? extends Class<?>> PRIMITIVE_TYPES = ImmutableSet.of(Byte.TYPE, Boolean.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE);
        private static final String DYNAMIC_OBJECT_HELPER_FIELD = "_gr_dyn_";
        private static final String MAPPING_FIELD = "_gr_map_";
        private static final String META_CLASS_FIELD = "_gr_mc_";
        private static final String SERVICES_FIELD = "_gr_svcs_";
        private static final String NAME_FIELD = "_gr_n_";
        private static final String DISPLAY_NAME_FIELD = "_gr_dn_";
        private static final String OWNER_FIELD = "_gr_owner_";
        private static final String FACTORY_ID_FIELD = "_gr_fid_";
        private static final String FACTORY_FIELD = "_gr_f_";
        private static final String SERVICES_METHOD = "$gradleServices";
        private static final String FACTORY_METHOD = "$gradleFactory";
        private static final String INIT_METHOD = "$gradleInit";
        private static final String INIT_WORK_METHOD = INIT_METHOD + "Work";
        private static final String INIT_ATTACH_METHOD = INIT_METHOD + "Attach";
        private static final String CONVENTION_MAPPING_FIELD_DESCRIPTOR = getDescriptor(ConventionMapping.class);
        private static final String META_CLASS_TYPE_DESCRIPTOR = getDescriptor(MetaClass.class);
        private final static Type META_CLASS_TYPE = getType(MetaClass.class);
        private final static Type GENERATED_SUBCLASS_TYPE = getType(GeneratedSubclass.class);
        private final static Type MODEL_OBJECT_TYPE = getType(ModelObject.class);
        private final static Type OWNER_AWARE_TYPE = getType(OwnerAware.class);
        private final static Type CONVENTION_AWARE_TYPE = getType(IConventionAware.class);
        private final static Type CONVENTION_AWARE_HELPER_TYPE = getType(ConventionAwareHelper.class);
        private final static Type DYNAMIC_OBJECT_AWARE_TYPE = getType(DynamicObjectAware.class);
        private final static Type EXTENSION_AWARE_TYPE = getType(ExtensionAware.class);
        @SuppressWarnings("deprecation")
        private final static Type HAS_CONVENTION_TYPE = getType(org.gradle.api.internal.HasConvention.class);
        private final static Type DYNAMIC_OBJECT_TYPE = getType(DynamicObject.class);
        private final static Type CONVENTION_MAPPING_TYPE = getType(ConventionMapping.class);
        private final static Type GROOVY_OBJECT_TYPE = getType(GroovyObject.class);
        @SuppressWarnings("deprecation")
        private final static Type CONVENTION_TYPE = getType(org.gradle.api.plugins.Convention.class);
        private final static Type ASM_BACKED_CLASS_GENERATOR_TYPE = getType(AsmBackedClassGenerator.class);
        private final static Type ABSTRACT_DYNAMIC_OBJECT_TYPE = getType(AbstractDynamicObject.class);
        private final static Type EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE = getType(MixInExtensibleDynamicObject.class);
        private final static Type NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE = getType(BeanDynamicObject.class);
        private static final String JAVA_REFLECT_TYPE_DESCRIPTOR = getDescriptor(java.lang.reflect.Type.class);
        private static final Type CONFIGURE_UTIL_TYPE = getType(ConfigureUtil.class);
        private static final Type CLOSURE_TYPE = getType(Closure.class);
        private static final Type SERVICE_REGISTRY_TYPE = getType(ServiceRegistry.class);
        private static final Type SERVICE_LOOKUP_TYPE = getType(ServiceLookup.class);
        private static final Type MANAGED_OBJECT_FACTORY_TYPE = getType(ManagedObjectFactory.class);
        private static final Type DEFAULT_PROPERTY_TYPE = getType(DefaultProperty.class);
        private static final Type BUILD_SERVICE_PROVIDER_TYPE = getType("Lorg/gradle/api/services/internal/BuildServiceProvider;");
        private static final Type INSTRUMENTED_EXECUTION_ACCESS_TYPE = getType("Lorg/gradle/internal/classpath/InstrumentedExecutionAccess;");

        // This set is unlikely to change often, so instead of introducing an additional level of indirection,
        // we are storing it here despite its relationship to Configuration Cache logic.
        // The full prohibited hierarchy is stored because there is no efficient way to check the class hierarchy via `org.objectweb.asm.Type`.
        private static final Set<Type> DISALLOWED_AT_EXECUTION_INJECTED_SERVICES_TYPES = ImmutableSet.of(
            getType(Project.class),
            getType("Lorg/gradle/api/internal/project/ProjectInternal;"),
            getType(Gradle.class),
            getType("Lorg/gradle/api/internal/GradleInternal;")
        );
        private static final Type JAVA_LANG_REFLECT_TYPE = getType(java.lang.reflect.Type.class);
        private static final Type OBJECT_TYPE = getType(Object.class);
        private static final Type CLASS_TYPE = getType(Class.class);
        private static final Type METHOD_TYPE = getType(Method.class);
        private static final Type STRING_TYPE = getType(String.class);
        private static final Type CLASS_ARRAY_TYPE = getType(Class[].class);
        private static final Type GROOVY_SYSTEM_TYPE = getType(GroovySystem.class);
        private static final Type META_CLASS_REGISTRY_TYPE = getType(MetaClassRegistry.class);
        private static final Type OBJECT_ARRAY_TYPE = getType(Object[].class);
        private static final Type ACTION_TYPE = getType(Action.class);
        private static final Type ISOLATED_ACTION_TYPE = getType(IsolatedAction.class);
        private static final Type LAZY_GROOVY_SUPPORT_TYPE = getType(LazyGroovySupport.class);
        private static final Type MANAGED_TYPE = getType(Managed.class);
        private static final Type EXTENSION_CONTAINER_TYPE = getType(ExtensionContainer.class);
        private static final Type DESCRIBABLE_TYPE = getType(Describable.class);
        private static final Type DISPLAY_NAME_TYPE = getType(DisplayName.class);
        private static final Type INJECT_TYPE = getType(Inject.class);
        private static final Type RUNNABLE_TYPE = getType(Runnable.class);
        private static final Type FACTORY_TYPE = getType(Factory.class);
        private static final Type LAMBDA_METAFACTORY_TYPE = getType(LambdaMetafactory.class);
        private static final Type METHOD_HANDLES_TYPE = getType(MethodHandles.class);
        private static final Type METHOD_HANDLES_LOOKUP_TYPE = getType(MethodHandles.Lookup.class);
        private static final Type METHOD_TYPE_TYPE = getType(MethodType.class);
        private static final Type DEPRECATION_LOGGER_TYPE = getType(DeprecationLogger.class);
        private static final String RETURN_STRING = getMethodDescriptor(STRING_TYPE);
        private static final String RETURN_DESCRIBABLE = getMethodDescriptor(DESCRIBABLE_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT = getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT_SERVICE_LOOKUP = getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, CLASS_TYPE, DYNAMIC_OBJECT_TYPE, SERVICE_LOOKUP_TYPE);
        private static final String RETURN_OBJECT_FROM_STRING_OBJECT_BOOLEAN = getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE, STRING_TYPE, BOOLEAN_TYPE);
        private static final String RETURN_CLASS = getMethodDescriptor(CLASS_TYPE);
        private static final String RETURN_BOOLEAN = getMethodDescriptor(BOOLEAN_TYPE);
        private static final String RETURN_VOID = getMethodDescriptor(Type.VOID_TYPE);
        private static final String RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION = getMethodDescriptor(Type.VOID_TYPE, CONVENTION_AWARE_TYPE, CONVENTION_TYPE);
        private static final String RETURN_CONVENTION = getMethodDescriptor(CONVENTION_TYPE);
        private static final String RETURN_CONVENTION_MAPPING = getMethodDescriptor(CONVENTION_MAPPING_TYPE);
        private static final String RETURN_OBJECT = getMethodDescriptor(OBJECT_TYPE);
        private static final String RETURN_EXTENSION_CONTAINER = getMethodDescriptor(EXTENSION_CONTAINER_TYPE);
        private static final String RETURN_OBJECT_FROM_STRING = getMethodDescriptor(OBJECT_TYPE, STRING_TYPE);
        private static final String RETURN_OBJECT_FROM_STRING_OBJECT = getMethodDescriptor(OBJECT_TYPE, STRING_TYPE, OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_STRING_OBJECT = getMethodDescriptor(Type.VOID_TYPE, STRING_TYPE, OBJECT_TYPE);
        private static final String RETURN_DYNAMIC_OBJECT = getMethodDescriptor(DYNAMIC_OBJECT_TYPE);
        private static final String RETURN_META_CLASS_FROM_CLASS = getMethodDescriptor(META_CLASS_TYPE, CLASS_TYPE);
        private static final String RETURN_BOOLEAN_FROM_STRING = getMethodDescriptor(BOOLEAN_TYPE, STRING_TYPE);
        private static final String RETURN_META_CLASS_REGISTRY = getMethodDescriptor(META_CLASS_REGISTRY_TYPE);
        private static final String RETURN_SERVICE_REGISTRY = getMethodDescriptor(SERVICE_REGISTRY_TYPE);
        private static final String RETURN_SERVICE_LOOKUP = getMethodDescriptor(SERVICE_LOOKUP_TYPE);
        private static final String RETURN_MANAGED_OBJECT_FACTORY = getMethodDescriptor(MANAGED_OBJECT_FACTORY_TYPE);
        private static final String RETURN_META_CLASS = getMethodDescriptor(META_CLASS_TYPE);
        private static final String RETURN_VOID_FROM_META_CLASS = getMethodDescriptor(Type.VOID_TYPE, META_CLASS_TYPE);
        private static final String GET_DECLARED_METHOD_DESCRIPTOR = getMethodDescriptor(METHOD_TYPE, STRING_TYPE, CLASS_ARRAY_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT_MODEL_OBJECT = getMethodDescriptor(VOID_TYPE, OBJECT_TYPE, MODEL_OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_DEFAULT_PROPERTY_SERVICE_LOOKUP_STRING = getMethodDescriptor(VOID_TYPE, DEFAULT_PROPERTY_TYPE, SERVICE_LOOKUP_TYPE, STRING_TYPE);
        private static final String RETURN_VOID_FROM_MODEL_OBJECT_DISPLAY_NAME = getMethodDescriptor(VOID_TYPE, MODEL_OBJECT_TYPE, DISPLAY_NAME_TYPE);
        private static final String RETURN_OBJECT_FROM_TYPE = getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE);
        private static final String RETURN_OBJECT_FROM_OBJECT_MODEL_OBJECT_STRING = getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE);
        private static final String RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS = getMethodDescriptor(OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE, CLASS_TYPE);
        private static final String RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS = getMethodDescriptor(OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE, CLASS_TYPE, CLASS_TYPE);
        private static final String RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS_CLASS = getMethodDescriptor(OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE, CLASS_TYPE, CLASS_TYPE, CLASS_TYPE);
        private static final String RETURN_VOID_FROM_STRING = getMethodDescriptor(VOID_TYPE, STRING_TYPE);
        private static final String LAMBDA_METAFACTORY_METHOD = getMethodDescriptor(getType(CallSite.class), METHOD_HANDLES_LOOKUP_TYPE, STRING_TYPE, METHOD_TYPE_TYPE, METHOD_TYPE_TYPE, getType(MethodHandle.class), METHOD_TYPE_TYPE);
        private static final String DEPRECATION_LOGGER_WHILE_DISABLED_RUNNABLE_METHOD = getMethodDescriptor(Type.VOID_TYPE, RUNNABLE_TYPE);
        private static final String DEPRECATION_LOGGER_WHILE_DISABLED_FACTORY_METHOD = getMethodDescriptor(OBJECT_TYPE, FACTORY_TYPE);
        private static final Handle LAMBDA_BOOTSTRAP_HANDLE = new Handle(H_INVOKESTATIC, LAMBDA_METAFACTORY_TYPE.getInternalName(), "metafactory", LAMBDA_METAFACTORY_METHOD, false);
        private static final Type RETURN_VOID_METHOD_TYPE = Type.getMethodType(RETURN_VOID);
        private static final Type RETURN_OBJECT_METHOD_TYPE = Type.getMethodType(RETURN_OBJECT);
        private static final Type RETURN_CONVENTION_METHOD_TYPE = Type.getMethodType(RETURN_CONVENTION);

        private static final String[] EMPTY_STRINGS = new String[0];
        private static final Type[] EMPTY_TYPES = new Type[0];

        private final Class<?> type;
        private final boolean managed;
        private final Type generatedType;
        private final Type superclassType;
        private final Map<java.lang.reflect.Type, ReturnTypeEntry> genericReturnTypeConstantsIndex = new HashMap<>();
        private final AsmClassGenerator classGenerator;
        private final int factoryId;
        private boolean hasMappingField;
        private final boolean conventionAware;
        private final boolean mixInDsl;
        private final boolean extensible;
        private final boolean providesOwnDynamicObject;
        private final boolean requiresToString;
        private final List<AttachedProperty> propertiesToAttachAtConstruction;
        private final List<AttachedProperty> propertiesToAttachOnDemand;
        private final List<PropertyMetadata> ineligibleProperties;
        private final boolean requiresServicesMethod;
        private final boolean requiresFactory;

        private ClassBuilderImpl(
            AsmClassGenerator classGenerator,
            boolean decorated,
            int factoryId,
            boolean extensible,
            boolean conventionAware,
            boolean managed,
            boolean providesOwnDynamicObject,
            boolean requiresToString,
            boolean requiresServicesMethod,
            boolean requiresFactory,
            List<AttachedProperty> propertiesToAttachAtConstruction,
            List<AttachedProperty> propertiesToAttachOnDemand,
            List<PropertyMetadata> ineligibleProperties
        ) {
            super(classGenerator.getVisitor());
            this.classGenerator = classGenerator;
            this.type = classGenerator.getTargetType();
            this.generatedType = classGenerator.getGeneratedType();
            this.factoryId = factoryId;
            this.managed = managed;
            this.requiresToString = requiresToString;
            this.propertiesToAttachAtConstruction = propertiesToAttachAtConstruction;
            this.propertiesToAttachOnDemand = propertiesToAttachOnDemand;
            this.superclassType = getType(type);
            this.mixInDsl = decorated;
            this.extensible = extensible;
            this.conventionAware = conventionAware;
            this.providesOwnDynamicObject = providesOwnDynamicObject;
            this.requiresServicesMethod = requiresServicesMethod;
            this.requiresFactory = requiresFactory;
            this.ineligibleProperties = ineligibleProperties;
        }

        public void startClass() {
            List<String> interfaceTypes = new ArrayList<>();

            Type superclass = superclassType;
            if (type.isInterface()) {
                interfaceTypes.add(superclassType.getInternalName());
                superclass = OBJECT_TYPE;
            }

            interfaceTypes.add(GENERATED_SUBCLASS_TYPE.getInternalName());
            interfaceTypes.add(MODEL_OBJECT_TYPE.getInternalName());
            interfaceTypes.add(OWNER_AWARE_TYPE.getInternalName());

            if (conventionAware) {
                interfaceTypes.add(CONVENTION_AWARE_TYPE.getInternalName());
            }

            if (extensible) {
                interfaceTypes.add(EXTENSION_AWARE_TYPE.getInternalName());
                interfaceTypes.add(HAS_CONVENTION_TYPE.getInternalName());
            }

            if (mixInDsl) {
                interfaceTypes.add(DYNAMIC_OBJECT_AWARE_TYPE.getInternalName());
                interfaceTypes.add(GROOVY_OBJECT_TYPE.getInternalName());
            }

            if (managed) {
                interfaceTypes.add(MANAGED_TYPE.getInternalName());
            }

            includeNotInheritedAnnotations();

            visit(V1_8, ACC_PUBLIC | ACC_SYNTHETIC, generatedType.getInternalName(), null,
                superclass.getInternalName(), interfaceTypes.toArray(EMPTY_STRINGS));

            generateInitMethod();
            generateGeneratedSubtypeMethods();
            generateModelObjectMethods();

            if (requiresToString) {
                generateToStringSupport();
            }
            if (requiresServicesMethod) {
                generateServiceRegistrySupport();
            }
            if (requiresFactory) {
                generateManagedPropertyCreationSupport();
            }
        }

        @Override
        public void addDefaultConstructor() {
            publicMethod("<init>", RETURN_VOID, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // this.super()
                _ALOAD(0);
                _INVOKESPECIAL(OBJECT_TYPE, "<init>", RETURN_VOID);

                // this.init_method()
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, INIT_METHOD, RETURN_VOID);

                _RETURN();
            }});
        }

        @Override
        public void addNameConstructor() {
            publicMethod("<init>", RETURN_VOID_FROM_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                visitAnnotation(INJECT_TYPE.getDescriptor(), true).visitEnd();

                // this.super()
                _ALOAD(0);
                _INVOKESPECIAL(OBJECT_TYPE, "<init>", RETURN_VOID);

                // this.name = name
                _ALOAD(0);
                _ALOAD(1);
                _PUTFIELD(generatedType, NAME_FIELD, STRING_TYPE);

                // this.init_method()
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, INIT_METHOD, RETURN_VOID);
                _RETURN();
            }});
        }

        @Override
        public void addConstructor(Constructor<?> constructor, boolean addNameParameter) {
            List<Type> paramTypes = paramTypesOf(constructor, addNameParameter);
            String superMethodDescriptor = getMethodDescriptor(VOID_TYPE, paramTypes.toArray(EMPTY_TYPES));
            String methodDescriptor;
            if (addNameParameter) {
                paramTypes.add(0, STRING_TYPE);
                methodDescriptor = getMethodDescriptor(VOID_TYPE, paramTypes.toArray(EMPTY_TYPES));
            } else {
                methodDescriptor = superMethodDescriptor;
            }

            publicMethod("<init>", methodDescriptor, signature(constructor, addNameParameter), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                visitDeclaredAnnotationsOf(constructor, mv);

                // this.super(p0 .. pn)
                _ALOAD(0);
                for (int typeVar = addNameParameter ? 1 : 0, stackVar = addNameParameter ? 2 : 1; typeVar < paramTypes.size(); ++typeVar) {
                    Type argType = paramTypes.get(typeVar);
                    _ILOAD_OF(argType, stackVar);
                    stackVar += argType.getSize();
                }
                _INVOKESPECIAL(superclassType, "<init>", superMethodDescriptor);

                if (addNameParameter) {
                    // this.name = name
                    _ALOAD(0);
                    _ALOAD(1);
                    _PUTFIELD(generatedType, NAME_FIELD, STRING_TYPE);
                }

                // this.init_method()
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, INIT_METHOD, RETURN_VOID);

                _RETURN();
            }});
        }

        @Nonnull
        private static List<Type> paramTypesOf(Constructor<?> constructor, boolean addNameParameter) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            List<Type> paramTypes = new ArrayList<>(parameterTypes.length + (addNameParameter ? 1 : 0));
            for (Class<?> paramType : parameterTypes) {
                paramTypes.add(getType(paramType));
            }
            return paramTypes;
        }

        private static void visitDeclaredAnnotationsOf(Constructor<?> constructor, MethodVisitor methodVisitor) {
            for (Annotation annotation : constructor.getDeclaredAnnotations()) {
                Class<? extends Annotation> annotationType = annotation.annotationType();
                if (annotationType.getAnnotation(Inherited.class) != null) {
                    continue;
                }
                Retention retention = annotationType.getAnnotation(Retention.class);
                methodVisitor
                    .visitAnnotation(descriptorOf(annotationType), retention != null && retention.value() == RetentionPolicy.RUNTIME)
                    .visitEnd();
            }
        }

        /**
         * Generates the init method with deprecation logging disabled.
         *
         * This is necessary because the initialization work invokes property getters on the decorated instance.
         */
        private void generateInitMethod() {

            // private void $gradleInit() { DeprecationLogger.whileDisabled(this::$gradleInitWork) }
            visitInnerClass(METHOD_HANDLES_LOOKUP_TYPE.getInternalName(), METHOD_HANDLES_TYPE.getInternalName(), "Lookup", ACC_PRIVATE | ACC_SYNTHETIC);
            privateSyntheticMethod(INIT_METHOD, RETURN_VOID, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _INVOKEDYNAMIC("run", getMethodDescriptor(RUNNABLE_TYPE, generatedType), LAMBDA_BOOTSTRAP_HANDLE, Arrays.asList(
                    RETURN_VOID_METHOD_TYPE,
                    new Handle(H_INVOKESPECIAL, generatedType.getInternalName(), INIT_WORK_METHOD, RETURN_VOID, false),
                    RETURN_VOID_METHOD_TYPE
                ));
                _INVOKESTATIC(DEPRECATION_LOGGER_TYPE, "whileDisabled", DEPRECATION_LOGGER_WHILE_DISABLED_RUNNABLE_METHOD);
                _RETURN();
            }});

            privateSyntheticMethod(INIT_ATTACH_METHOD, RETURN_VOID, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                for (AttachedProperty attached : propertiesToAttachAtConstruction) {
                    attachProperty(attached);
                }
                _RETURN();
            }});

            // private void $gradleInitWork() { ... do the work ... }
            privateSyntheticMethod(INIT_WORK_METHOD, RETURN_VOID, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{

                // this.displayName = AsmBackedClassGenerator.getDisplayNameForNext()
                _ALOAD(0);
                _INVOKESTATIC(ASM_BACKED_CLASS_GENERATOR_TYPE, GET_DISPLAY_NAME_FOR_NEXT_METHOD_NAME, RETURN_DESCRIBABLE);
                _PUTFIELD(generatedType, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE);

                if (requiresServicesMethod) {
                    // this.services = AsmBackedClassGenerator.getServicesForNext()
                    _ALOAD(0);
                    _INVOKESTATIC(ASM_BACKED_CLASS_GENERATOR_TYPE, GET_SERVICES_FOR_NEXT_METHOD_NAME, RETURN_SERVICE_LOOKUP);
                    _PUTFIELD(generatedType, SERVICES_FIELD, SERVICE_LOOKUP_TYPE);
                }
                if (requiresFactory) {
                    // this.factory = AsmBackedClassGenerator.getFactoryForNext()
                    _ALOAD(0);
                    _INVOKESTATIC(ASM_BACKED_CLASS_GENERATOR_TYPE, GET_FACTORY_FOR_NEXT_METHOD_NAME, RETURN_MANAGED_OBJECT_FACTORY);
                    _PUTFIELD(generatedType, FACTORY_FIELD, MANAGED_OBJECT_FACTORY_TYPE);
                }

                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, INIT_ATTACH_METHOD, RETURN_VOID);

                // For classes that could have convention mapping, but implement IConventionAware themselves, we need to
                // mark ineligible-for-convention-mapping properties in a different way.
                // See mixInConventionAware() for how we do this for decorated types that do not implement IConventionAware manually
                //
                // Doing this for all types introduces a performance penalty for types that have Provider properties, even
                // if they don't use convention mapping.
                if (conventionAware && IConventionAware.class.isAssignableFrom(type)) {
                    for (PropertyMetadata property : ineligibleProperties) {
                        // GENERATE getConventionMapping()
                        _ALOAD(0);
                        _INVOKEVIRTUAL(generatedType, "getConventionMapping", RETURN_CONVENTION_MAPPING);
                        // GENERATE convention.ineligible(__property.getName()__)
                        _LDC(property.getName());
                        _INVOKEINTERFACE(CONVENTION_MAPPING_TYPE, "ineligible", RETURN_VOID_FROM_STRING);
                    }
                }
                _RETURN();
            }});
        }

        @Override
        public void addNoDeprecationConventionPrivateGetter() {
            // GENERATE private Convention getConventionWhileDisabledDeprecationLogger() {
            //     return DeprecationLogger.whileDisabled { getConvention() }
            // }
            privateSyntheticMethod("getConventionWhileDisabledDeprecationLogger", RETURN_CONVENTION, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _INVOKEDYNAMIC("create", getMethodDescriptor(FACTORY_TYPE, generatedType), LAMBDA_BOOTSTRAP_HANDLE, Arrays.asList(
                    RETURN_OBJECT_METHOD_TYPE,
                    new Handle(H_INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false),
                    RETURN_CONVENTION_METHOD_TYPE
                ));
                _INVOKESTATIC(DEPRECATION_LOGGER_TYPE, "whileDisabled", DEPRECATION_LOGGER_WHILE_DISABLED_FACTORY_METHOD);
                _CHECKCAST(CONVENTION_TYPE);
                _ARETURN();
            }});
        }

        @Override
        public void addExtensionsProperty() {
            // GENERATE public ExtensionContainer getExtensions() { return getConventionWhileDisabledDeprecationLogger(); }
            addGetter("getExtensions", EXTENSION_CONTAINER_TYPE, RETURN_EXTENSION_CONTAINER, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GENERATE getConventionWhileDisabledDeprecationLogger()
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, "getConventionWhileDisabledDeprecationLogger", RETURN_CONVENTION);
            }});
        }

        @Override
        public void mixInDynamicAware() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE private DynamicObject dynamicObjectHelper
            addField(ACC_PRIVATE | ACC_TRANSIENT, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE);
            // END

            if (extensible) {
                // GENERATE public Convention getConvention() { return getAsDynamicObject().getConvention(); }
                addGetter("getConvention", CONVENTION_TYPE, RETURN_CONVENTION, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                    // GENERATE ((MixInExtensibleDynamicObject)getAsDynamicObject()).getConvention()
                    _ALOAD(0);
                    _INVOKEVIRTUAL(generatedType, "getAsDynamicObject", RETURN_DYNAMIC_OBJECT);
                    _CHECKCAST(EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE);
                    _INVOKEVIRTUAL(EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE, "getConvention", RETURN_CONVENTION);
                }});
                // END
            }
            // END

            // GENERATE public DynamicObject getAsDynamicObject() {
            //      if (dynamicObjectHelper == null) {
            //          dynamicObjectHelper = <init>
            //      }
            //      return dynamicObjectHelper;
            // }
            addLazyGetter("getAsDynamicObject", DYNAMIC_OBJECT_TYPE, RETURN_DYNAMIC_OBJECT, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                if (extensible) {
                    // GENERATE new MixInExtensibleDynamicObject(this, getClass().getSuperClass(), super.getAsDynamicObject(), this.services())
                    _NEW(EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE);
                    _DUP();

                    _ALOAD(0);
                    _ALOAD(0);
                    _INVOKEVIRTUAL(generatedType, "getClass", RETURN_CLASS);
                    _INVOKEVIRTUAL(CLASS_TYPE, "getSuperclass", RETURN_CLASS);

                    if (providesOwnDynamicObject) {
                        // GENERATE super.getAsDynamicObject()
                        _ALOAD(0);
                        _INVOKESPECIAL(getType(type), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT);
                    } else {
                        // GENERATE null
                        _ACONST_NULL();
                    }

                    // GENERATE this.services()
                    putServiceRegistryOnStack();
                    _INVOKESPECIAL(EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE, "<init>", RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT_SERVICE_LOOKUP);
                    // END
                } else {
                    // GENERATE new BeanDynamicObject(this)
                    _NEW(NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE);
                    _DUP();
                    _ALOAD(0);
                    _INVOKESPECIAL(NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE, "<init>", RETURN_VOID_FROM_OBJECT);
                    // END
                }
            }});
            // END
        }

        @Override
        public void mixInConventionAware() {
            // GENERATE private ConventionMapping mapping
            addField(ACC_PRIVATE | ACC_TRANSIENT, MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR);
            hasMappingField = true;
            // END

            // GENERATE public ConventionMapping getConventionMapping() {
            //     if (mapping == null) {
            //         mapping = new ConventionAwareHelper(this, getConventionWhileDisabledDeprecationLogger());
            //         ineligibleProperties.forEach { mapping.ineligible(it.name) }
            //     }
            //     return mapping;
            // }
            addLazyGetter("getConventionMapping", CONVENTION_MAPPING_TYPE, RETURN_CONVENTION_MAPPING, MAPPING_FIELD, CONVENTION_MAPPING_TYPE, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GENERATE new ConventionAwareHelper(this, getConventionWhileDisabledDeprecationLogger())
                _NEW(CONVENTION_AWARE_HELPER_TYPE);
                _DUP();
                _ALOAD(0);

                // GENERATE getConvention()
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, "getConventionWhileDisabledDeprecationLogger", RETURN_CONVENTION);
                // END

                _INVOKESPECIAL(CONVENTION_AWARE_HELPER_TYPE, "<init>", RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION);
                // END

                for (PropertyMetadata property : ineligibleProperties) {
                    // GENERATE mapping.ineligible(__property.getName()__)
                    _DUP();
                    _LDC(property.getName());
                    _INVOKEINTERFACE(CONVENTION_MAPPING_TYPE, "ineligible", RETURN_VOID_FROM_STRING);
                }
            }});
            // END
        }

        @Override
        public void mixInGroovyObject() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass())
            addField(ACC_PRIVATE | ACC_TRANSIENT, META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR);

            // GENERATE public MetaClass getMetaClass() {
            //     if (metaClass == null) {
            //         metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
            //     }
            //     return metaClass;
            // }
            addLazyGetter("getMetaClass", META_CLASS_TYPE, RETURN_META_CLASS, META_CLASS_FIELD, META_CLASS_TYPE, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GroovySystem.getMetaClassRegistry()
                _INVOKESTATIC(GROOVY_SYSTEM_TYPE, "getMetaClassRegistry", RETURN_META_CLASS_REGISTRY);

                // this.getClass()
                _ALOAD(0);
                _INVOKEVIRTUAL(OBJECT_TYPE, "getClass", RETURN_CLASS);

                // getMetaClass(..)
                _INVOKEINTERFACE(META_CLASS_REGISTRY_TYPE, "getMetaClass", RETURN_META_CLASS_FROM_CLASS);
            }});
            // END

            // GENERATE public void setMetaClass(MetaClass class) { this.metaClass = class; }
            addSetter("setMetaClass", RETURN_VOID_FROM_META_CLASS, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _ALOAD(1);
                _PUTFIELD(generatedType, META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR);
            }});
        }

        private void addSetter(String methodName, String methodDescriptor, BytecodeFragment body) {
            addSetter(methodName, methodDescriptor, null, body);
        }

        private void addSetter(String methodName, String methodDescriptor, @Nullable String signature, BytecodeFragment body) {
            publicMethod(methodName, methodDescriptor, signature, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                emit(body);
                _RETURN();
            }});
        }

        @Override
        public void addLazyGroovySupportSetterOverloads(PropertyMetadata property, MethodMetadata getter) {
            if (!mixInDsl) {
                return;
            }

            // GENERATE public void set<Name>(Object p) {
            //    ((LazyGroovySupport)<getter>()).setFromAnyValue(p);
            // }
            addSetter(getSetterName(property.getName()), RETURN_VOID_FROM_OBJECT, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, getter.getName(), getMethodDescriptor(getType(getter.getReturnType())));
                _CHECKCAST(LAZY_GROOVY_SUPPORT_TYPE);
                _ALOAD(1);
                _INVOKEINTERFACE(LAZY_GROOVY_SUPPORT_TYPE, "setFromAnyValue", ClassBuilderImpl.RETURN_VOID_FROM_OBJECT);
            }});
        }

        /**
         * Adds a getter that returns the value of the given field, initializing it if null using the given code. The code should leave the value on the top of the stack.
         */
        private void addLazyGetter(
            String methodName,
            Type returnType,
            String methodDescriptor,
            final String fieldName,
            final Type fieldType,
            final BytecodeFragment initializer
        ) {
            addLazyGetter(methodName, returnType, methodDescriptor, null, fieldName, fieldType, initializer, BytecodeFragment.NO_OP);
        }

        private void addLazyGetter(
            String methodName,
            Type returnType,
            String methodDescriptor,
            @Nullable String signature,
            final String fieldName,
            final Type fieldType,
            final BytecodeFragment initializer,
            final BytecodeFragment epilogue
        ) {
            addGetter(methodName, returnType, methodDescriptor, signature, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // var = this.<field>
                _ALOAD(0);
                _GETFIELD(generatedType, fieldName, fieldType);
                _ASTORE(1);
                // if (var == null) { var = <code-body>; this.<field> = var; }
                _ALOAD(1);
                Label returnValue = new Label();
                _IFNONNULL(returnValue);
                emit(initializer);
                _ASTORE(1);
                _ALOAD(0);
                _ALOAD(1);
                _PUTFIELD(generatedType, fieldName, fieldType);
                // return var
                visitLabel(returnValue);
                emit(epilogue);
                _ALOAD(1);
            }});
        }

        @Override
        public void addDynamicMethods() {
            if (!mixInDsl) {
                return;
            }

            // DefaultProject has its own implementation of GroovyObject's methods, and we want to keep those implementations.
            // TODO: introduce a better way to communicate this for classes that don't need generated dynamic-object methods?
            if (type.getName().equals("org.gradle.api.internal.project.DefaultProject")) {
                return;
            }

            // GENERATE public Object getProperty(String name) { return getAsDynamicObject().getProperty(name); }
            addGetter("getProperty", OBJECT_TYPE, RETURN_OBJECT_FROM_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GENERATE getAsDynamicObject().getProperty(name);
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, "getAsDynamicObject", RETURN_DYNAMIC_OBJECT);

                _ALOAD(1);
                _INVOKEINTERFACE(DYNAMIC_OBJECT_TYPE, "getProperty", RETURN_OBJECT_FROM_STRING);
                // END
            }});

            // GENERATE public boolean hasProperty(String name) { return getAsDynamicObject().hasProperty(name) }
            publicMethod("hasProperty", RETURN_BOOLEAN_FROM_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GENERATE getAsDynamicObject().hasProperty(name);
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, "getAsDynamicObject", RETURN_DYNAMIC_OBJECT);

                _ALOAD(1);
                _INVOKEINTERFACE(DYNAMIC_OBJECT_TYPE, "hasProperty", RETURN_BOOLEAN_FROM_STRING);

                // END
                _IRETURN();
            }});

            // GENERATE public void setProperty(String name, Object value) { getAsDynamicObject().setProperty(name, value); }
            addSetter("setProperty", RETURN_VOID_FROM_STRING_OBJECT, setter -> new MethodVisitorScope(setter) {{
                // GENERATE getAsDynamicObject().setProperty(name, value)
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, "getAsDynamicObject", RETURN_DYNAMIC_OBJECT);

                _ALOAD(1);
                _ALOAD(2);
                _INVOKEINTERFACE(DYNAMIC_OBJECT_TYPE, "setProperty", RETURN_VOID_FROM_STRING_OBJECT);
                // END
            }});

            // GENERATE public Object invokeMethod(String name, Object params) { return getAsDynamicObject().invokeMethod(name, (Object[])params); }
            addGetter("invokeMethod", OBJECT_TYPE, RETURN_OBJECT_FROM_STRING_OBJECT, getter -> new MethodVisitorScope(getter) {{

                // GENERATE getAsDynamicObject().invokeMethod(name, (args instanceof Object[]) ? args : new Object[] { args })
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, "getAsDynamicObject", RETURN_DYNAMIC_OBJECT);

                _ALOAD(1);

                // GENERATE (args instanceof Object[]) ? args : new Object[] { args }
                Label end = new Label();
                Label notArray = new Label();
                _ALOAD(2);
                _INSTANCEOF(OBJECT_ARRAY_TYPE);
                _IFEQ(notArray);

                // (Object[]) args
                _ALOAD(2);
                _CHECKCAST(OBJECT_ARRAY_TYPE);
                _GOTO(end);

                // new Object[] { args }
                visitLabel(notArray);
                _ICONST_1();
                _ANEWARRAY(OBJECT_TYPE);
                _DUP();
                _ICONST_0();
                _ALOAD(2);
                _AASTORE();

                visitLabel(end);

                _INVOKEINTERFACE(DYNAMIC_OBJECT_TYPE, "invokeMethod", getMethodDescriptor(OBJECT_TYPE, STRING_TYPE, OBJECT_ARRAY_TYPE));
            }});
        }

        @Override
        public void applyServiceInjectionToProperty(PropertyMetadata property) {
            // GENERATE private <type> <property-field-name>;
            addField(ACC_PRIVATE | ACC_TRANSIENT, propFieldName(property), property.getType());
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, MethodMetadata getter) {
            applyServiceInjectionToGetter(property, null, getter);
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, @Nullable final Class<? extends Annotation> annotation, MethodMetadata getter) {
            // GENERATE public <type> <getter>() { if (<field> == null) { <field> = <services>>.get(<service-type>>); } return <field> }
            final String getterName = getter.getName();
            Type returnType = getType(getter.getReturnType());
            String methodDescriptor = getMethodDescriptor(returnType);
            final Type serviceType = getType(property.getType());
            final java.lang.reflect.Type genericServiceType = property.getGenericType();
            String propFieldName = propFieldName(property);
            String signature = getterSignature(getter.getGenericReturnType());

            BytecodeFragment getterInitializer = methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{

                putServiceRegistryOnStack();

                if (genericServiceType instanceof Class) {
                    // if the return type doesn't use generics, then it's faster to just rely on the type name directly
                    _LDC(getType((Class<?>) genericServiceType));
                } else {
                    // load the static type descriptor from class constants
                    String constantFieldName = getConstantNameForGenericReturnType(genericServiceType, getterName);
                    _GETSTATIC(generatedType, constantFieldName, JAVA_REFLECT_TYPE_DESCRIPTOR);
                }

                if (annotation == null) {
                    // get(<type>)
                    _INVOKEINTERFACE(SERVICE_LOOKUP_TYPE, "get", RETURN_OBJECT_FROM_TYPE);
                } else {
                    // get(<type>, <annotation>)
                    _LDC(getType(annotation));
                    _INVOKEINTERFACE(SERVICE_LOOKUP_TYPE, "get", getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE, CLASS_TYPE));
                }

                // (<type>)<service>
                _CHECKCAST(serviceType);
            }};

            BytecodeFragment getterEpilogue = getInjectedServiceGetterEpilogue(serviceType, getterName);

            addLazyGetter(getterName, returnType, methodDescriptor, signature, propFieldName, serviceType, getterInitializer, getterEpilogue);
        }

        private BytecodeFragment getInjectedServiceGetterEpilogue(Type serviceType, String getterName) {
            if (DISALLOWED_AT_EXECUTION_INJECTED_SERVICES_TYPES.contains(serviceType)) {
                return methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                    // InstrumentedExecutionAccess.disallowedAtExecutionInjectedServiceAccessed(<service-type>,<getter-name>,<this-type-name>)
                    _LDC(serviceType);
                    _LDC(getterName);
                    _LDC(type.getName());
                    _INVOKESTATIC(INSTRUMENTED_EXECUTION_ACCESS_TYPE, "disallowedAtExecutionInjectedServiceAccessed", getMethodDescriptor(VOID_TYPE, CLASS_TYPE, STRING_TYPE, STRING_TYPE));
                }};
            } else {
                return BytecodeFragment.NO_OP;
            }
        }

        @Override
        public void applyServiceInjectionToSetter(PropertyMetadata property, Class<? extends Annotation> annotation, Method setter) {
            applyServiceInjectionToSetter(property, setter);
        }

        private String getConstantNameForGenericReturnType(java.lang.reflect.Type genericReturnType, String getterName) {
            ReturnTypeEntry entry = genericReturnTypeConstantsIndex.get(genericReturnType);
            if (entry == null) {
                String fieldName = "_GENERIC_RETURN_TYPE_" + genericReturnTypeConstantsIndex.size();
                entry = new ReturnTypeEntry(fieldName, getterName);
                genericReturnTypeConstantsIndex.put(genericReturnType, entry);
            }
            return entry.fieldName;
        }

        @Override
        public void applyServiceInjectionToSetter(PropertyMetadata property, Method setter) {
            addSetterForProperty(property, setter);
        }

        @Override
        public void applyManagedStateToProperty(PropertyMetadata property) {
            // GENERATE private <type> <property-field-name>;
            addField(ACC_PRIVATE, propFieldName(property), property.getType());
        }

        @Override
        public void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter, boolean applyRole) {
            // GENERATE public <type> <getter>() {
            //     if (<field> == null) {
            //         <field> = getFactory().newInstance(this, <display-name>, <type>, <prop-name>);
            //     }
            //     return <field>;
            // }
            Type propType = getType(property.getType());
            Type returnType = getType(getter.getReturnType());
            String descriptor = getMethodDescriptor(returnType);
            String fieldName = propFieldName(property);
            addLazyGetter(getter.getName(), returnType, descriptor, fieldName, propType, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{

                // GENERATE factory = getFactory()
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, FACTORY_METHOD, RETURN_MANAGED_OBJECT_FACTORY);

                // GENERATE return factory.newInstance(this, propertyName, ...)
                _ALOAD(0);
                _LDC(property.getName());

                switch (property.getType().getTypeParameters().length) {
                    case 1:
                        // GENERATE factory.newInstance(this, propertyName, propType, elementType)
                        Type elementType = getType(rawTypeParam(property, 0));
                        _LDC(propType);
                        _LDC(elementType);
                        _INVOKEVIRTUAL(MANAGED_OBJECT_FACTORY_TYPE, "newInstance", RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS);
                        break;
                    case 2:
                        // GENERATE factory.newInstance(this, propertyName, propType, keyType, valueType)
                        Type keyType = getType(rawTypeParam(property, 0));
                        Type valueType = getType(rawTypeParam(property, 1));
                        _LDC(propType);
                        _LDC(keyType);
                        _LDC(valueType);
                        _INVOKEVIRTUAL(MANAGED_OBJECT_FACTORY_TYPE, "newInstance", RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS_CLASS);
                        break;
                    default:
                        // GENERATE factory.newInstance(this, propertyName, propType)
                        _LDC(propType);
                        _INVOKEVIRTUAL(MANAGED_OBJECT_FACTORY_TYPE, "newInstance", RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS);
                        break;
                }

                if (applyRole) {
                    _DUP();
                    applyRole();
                }

                String buildServiceName = getBuildServiceName(property);
                if (buildServiceName != null) {
                    // property is a service reference
                    _DUP();
                    setBuildServiceConvention(buildServiceName);
                }

                _CHECKCAST(propType);
            }});
        }

        /**
         * Local extensions to {@link MethodVisitorScope}.
         */
        private class LocalMethodVisitorScope extends MethodVisitorScope {

            public LocalMethodVisitorScope(MethodVisitor methodVisitor) {
                super(methodVisitor);
            }

            protected void attachProperty(AttachedProperty attached) {
                // ManagedObjectFactory.attachOwner(get<prop>(), this, <property-name>))
                PropertyMetadata property = attached.property;
                boolean applyRole = attached.applyRole;
                MethodMetadata getter = property.getMainGetter();
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, getter.getName(), getMethodDescriptor(getType(getter.getReturnType())));
                if (applyRole) {
                    _DUP();
                }
                _ALOAD(0);
                _LDC(property.getName());
                _INVOKESTATIC(MANAGED_OBJECT_FACTORY_TYPE, "attachOwner", RETURN_OBJECT_FROM_OBJECT_MODEL_OBJECT_STRING);
                if (applyRole) {
                    applyRole();
                }
            }

            // Caller should place property value on the top of the stack
            protected void applyRole() {
                // GENERATE getFactory().applyRole(<value>)
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, FACTORY_METHOD, RETURN_MANAGED_OBJECT_FACTORY);
                _SWAP();
                _ALOAD(0);
                _INVOKEVIRTUAL(MANAGED_OBJECT_FACTORY_TYPE, "applyRole", RETURN_VOID_FROM_OBJECT_MODEL_OBJECT);
            }

            // Caller should place property value on the top of the stack
            protected void setBuildServiceConvention(@Nullable String serviceName) {
                // GENERATE BuildServiceProvider.setBuildServiceAsConvention(defaultProperty, getServices(), "<serviceName>")
                _CHECKCAST(DEFAULT_PROPERTY_TYPE);
                putServiceRegistryOnStack();
                _LDC(serviceName);
                _INVOKESTATIC(BUILD_SERVICE_PROVIDER_TYPE, "setBuildServiceAsConvention", RETURN_VOID_FROM_DEFAULT_PROPERTY_SERVICE_LOOKUP_STRING);
            }

            protected void putServiceRegistryOnStack() {
                if (requiresServicesMethod) {
                    // this.<services_method>()
                    _ALOAD(0);
                    _INVOKEVIRTUAL(generatedType, SERVICES_METHOD, RETURN_SERVICE_LOOKUP);
                } else {
                    // this.getServices()
                    _ALOAD(0);
                    _INVOKEVIRTUAL(generatedType, "getServices", RETURN_SERVICE_REGISTRY);
                }
            }
        }

        @Override
        public void applyManagedStateToGetter(PropertyMetadata property, Method getter) {
            // GENERATE public <type> <getter>() { return <field> }
            Type returnType = getType(getter.getReturnType());
            addGetter(getter.getName(), returnType, getMethodDescriptor(returnType), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _GETFIELD(generatedType, propFieldName(property), returnType);
            }});
        }

        @Override
        public void applyManagedStateToSetter(PropertyMetadata property, Method setter) {
            addSetterForProperty(property, setter);
        }

        private void addSetterForProperty(PropertyMetadata property, Method setter) {
            // GENERATE public void <setter>(<type> value) { <field> == value }
            Type fieldType = getType(property.getType());
            addSetter(setter.getName(), getMethodDescriptor(setter), signature(setter), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // this.field = value
                _ALOAD(0);
                _ILOAD_OF(fieldType, 1);
                _PUTFIELD(generatedType, propFieldName(property), fieldType);
            }});
        }

        private void generateGeneratedSubtypeMethods() {
            // Generate: Class publicType() { ... }
            publicMethod("publicType", RETURN_CLASS, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _LDC(superclassType);
                _ARETURN();
            }});

            // Generate: static Class generatedFrom() { ... }
            addMethod(ACC_PUBLIC | ACC_STATIC, "generatedFrom", RETURN_CLASS, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _LDC(superclassType);
                _ARETURN();
            }});
        }

        private void generateModelObjectMethods() {
            addField(ACC_PRIVATE | ACC_SYNTHETIC, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE);
            addField(ACC_PRIVATE | ACC_SYNTHETIC, OWNER_FIELD, MODEL_OBJECT_TYPE);

            // GENERATE boolean hasUsefulDisplayName() { ... }
            publicMethod("hasUsefulDisplayName", RETURN_BOOLEAN, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                if (requiresToString) {
                    // Type has a generated toString() implementation
                    // Generate: return displayName != null
                    _ALOAD(0);
                    _GETFIELD(generatedType, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE);
                    Label label = new Label();
                    _IFNULL(label);
                    _LDC(true);
                    _IRETURN_OF(BOOLEAN_TYPE);
                    visitLabel(label);
                    _LDC(false);
                    _IRETURN_OF(BOOLEAN_TYPE);
                } else {
                    // Type has its own toString implementation
                    // Generate: return true
                    _LDC(true);
                    _IRETURN_OF(BOOLEAN_TYPE);
                }
            }});

            // GENERATE getModelIdentityDisplayName() { return displayName }
            publicMethod("getModelIdentityDisplayName", RETURN_DESCRIBABLE, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _GETFIELD(generatedType, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE);
                _ARETURN();
            }});

            // GENERATE getTaskThatOwnsThisObject() { ... }
            publicMethod("getTaskThatOwnsThisObject", getMethodDescriptor(getType(Task.class)), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                if (Task.class.isAssignableFrom(type)) {
                    // return this
                    _ALOAD(0);
                } else {
                    // if (owner != null) { return owner.getTaskThatOwnsThisObject() } else { return null }
                    _ALOAD(0);
                    _GETFIELD(generatedType, OWNER_FIELD, MODEL_OBJECT_TYPE);
                    _DUP();
                    Label useNull = new Label();
                    _IFNULL(useNull);
                    _INVOKEINTERFACE(MODEL_OBJECT_TYPE, "getTaskThatOwnsThisObject", getMethodDescriptor(getType(Task.class)));
                    visitLabel(useNull);
                }
                _ARETURN();
            }});

            // GENERATE attachOwner(owner, displayName) { this.displayName = displayName }
            publicMethod("attachOwner", RETURN_VOID_FROM_MODEL_OBJECT_DISPLAY_NAME, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _ALOAD(1);
                _PUTFIELD(generatedType, OWNER_FIELD, MODEL_OBJECT_TYPE);
                _ALOAD(0);
                _ALOAD(2);
                _PUTFIELD(generatedType, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE);
                _RETURN();
            }});

            publicMethod("attachModelProperties", RETURN_VOID, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _INVOKEVIRTUAL(generatedType, INIT_ATTACH_METHOD, RETURN_VOID);
                for (AttachedProperty attached : propertiesToAttachOnDemand) {
                    attachProperty(attached);
                }
                _RETURN();
            }});
        }

        @Override
        public void addManagedMethods(List<PropertyMetadata> mutableProperties, List<PropertyMetadata> readOnlyProperties) {
            addField(ACC_PRIVATE | ACC_STATIC, FACTORY_ID_FIELD, INT_TYPE);

            final int mutablePropertySize = mutableProperties.size();
            final int readOnlyPropertySize = readOnlyProperties.size();

            // Generate: void initFromState(Object[] state) { }
            // See ManagedTypeFactory for how it's used.
            addMethod(ACC_PUBLIC | ACC_SYNTHETIC, "initFromState", getMethodDescriptor(VOID_TYPE, OBJECT_ARRAY_TYPE), methodVisitor -> new MethodVisitorScope(methodVisitor) {

                {
                    // for each property
                    //   this.$property = state[$propertyIndex];
                    loadPropertiesFromState(mutableProperties);
                    loadPropertiesFromState(readOnlyProperties);
                    _RETURN();
                }

                int propertyIndex = 0;

                private void loadPropertiesFromState(List<PropertyMetadata> properties) {
                    for (PropertyMetadata property : properties) {
                        _ALOAD(0);
                        _ALOAD(1);
                        _LDC(propertyIndex);
                        _AALOAD();
                        Type propertyType = getType(property.getType());
                        _UNBOX(propertyType);
                        _PUTFIELD(generatedType, propFieldName(property), propertyType);
                        propertyIndex++;
                    }
                }
            });

            // Generate: Class immutable() { return <properties.empty> && <read-only-properties.empty> }
            publicMethod("isImmutable", RETURN_BOOLEAN, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // Could return true if all the read only properties point to immutable objects, but at this stage there are no such types supported
                _LDC(mutablePropertySize == 0 && readOnlyPropertySize == 0);
                _IRETURN();
            }});

            // Generate: Object[] unpackState() { state = new Object[<size>]; state[x] = <prop-field>; return state; }
            publicMethod("unpackState", RETURN_OBJECT, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _LDC(mutablePropertySize + readOnlyPropertySize);
                _ANEWARRAY(OBJECT_TYPE);
                // TODO - property order needs to be deterministic across JVM invocations, i.e. sort the properties by name
                int propertyIndex = 0;
                for (PropertyMetadata property : mutableProperties) {
                    String propFieldName = propFieldName(property);
                    _DUP();
                    _LDC(propertyIndex);
                    _ALOAD(0);
                    Type propertyType = getType(property.getType());
                    _GETFIELD(generatedType, propFieldName, propertyType);
                    _AUTOBOX(property.getType(), propertyType);
                    _AASTORE();
                    propertyIndex++;
                }
                for (PropertyMetadata property : readOnlyProperties) {
                    _DUP();
                    _LDC(propertyIndex);
                    _ALOAD(0);
                    MethodMetadata getter = property.getMainGetter();
                    _INVOKEVIRTUAL(generatedType, getter.getName(), getMethodDescriptor(getType(getter.getReturnType())));
                    Type propertyType = getType(property.getType());
                    _AUTOBOX(property.getType(), propertyType);
                    _AASTORE();
                    propertyIndex++;
                }
                _ARETURN();
            }});

            // Generate: int getFactoryId() { return <factory-id-field> }
            publicMethod("getFactoryId", getMethodDescriptor(INT_TYPE), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _GETSTATIC(generatedType, FACTORY_ID_FIELD, INT_TYPE);
                _IRETURN();
            }});
        }

        @Override
        public void applyConventionMappingToProperty(PropertyMetadata property) {
            if (!conventionAware) {
                return;
            }

            // GENERATE private boolean <flag-name>;
            addField(ACC_PRIVATE | ACC_TRANSIENT, propFieldName(property), BOOLEAN_TYPE);
        }

        @Override
        public void applyConventionMappingToGetter(PropertyMetadata property, MethodMetadata getter, boolean attachOwner, boolean applyRole) {
            if (!conventionAware && !attachOwner) {
                return;
            }

            String getterName = getter.getName();
            Type returnType = getType(getter.getReturnType());
            String methodDescriptor = getMethodDescriptor(returnType);
            publicMethod(getterName, methodDescriptor, methodVisitor -> new LocalMethodVisitorScope(methodVisitor) {{
                if (conventionAware) {
                    // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__); }
                    Label finish = new Label();

                    if (hasMappingField) {
                        // if (conventionMapping == null) { return super.<getter>; }
                        _ALOAD(0);
                        _GETFIELD(generatedType, MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR);
                        Label useConvention = new Label();
                        _IFNONNULL(useConvention);
                        _ALOAD(0);
                        _INVOKESPECIAL(superclassType, getterName, methodDescriptor, type.isInterface());
                        _GOTO(finish);
                        visitLabel(useConvention);
                    }
                    // else { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__);  }
                    _ALOAD(0);
                    _INVOKEINTERFACE(CONVENTION_AWARE_TYPE, "getConventionMapping", getMethodDescriptor(CONVENTION_MAPPING_TYPE));

                    _ALOAD(0);
                    _INVOKESPECIAL(superclassType, getterName, methodDescriptor, type.isInterface());

                    _AUTOBOX(getter.getReturnType(), returnType);

                    _LDC(property.getName());
                    _ALOAD(0);
                    _GETFIELD(generatedType, propFieldName(property), BOOLEAN_TYPE);
                    _INVOKEINTERFACE(CONVENTION_MAPPING_TYPE, "getConventionValue", RETURN_OBJECT_FROM_STRING_OBJECT_BOOLEAN);
                    _UNBOX(returnType);

                    visitLabel(finish);
                } else {
                    // GENERATE super.<getter>()
                    _ALOAD(0);
                    _INVOKESPECIAL(superclassType, getterName, methodDescriptor, type.isInterface());
                }

                if (attachOwner) {
                    // GENERATE ManagedObjectFactory.attachOwner(<value>, this, <property-name>)
                    _DUP();
                    _ALOAD(0);
                    _LDC(property.getName());
                    _INVOKESTATIC(MANAGED_OBJECT_FACTORY_TYPE, "attachOwner", RETURN_OBJECT_FROM_OBJECT_MODEL_OBJECT_STRING);
                    _POP();
                    if (applyRole) {
                        // GENERATE ManagedObjectFactory.applyRole(<value>)
                        _DUP();
                        applyRole();
                    }
                }

                _IRETURN_OF(returnType);
            }});
        }

        @Override
        public void addSetMethod(PropertyMetadata property, Method setter) {
            if (!mixInDsl) {
                return;
            }

            Type paramType = getType(setter.getParameterTypes()[0]);
            Type returnType = getType(setter.getReturnType());
            String setterDescriptor = getMethodDescriptor(returnType, paramType);

            // GENERATE public void <propName>(<type> v) { <setter>(v) }
            addSetter(property.getName(), getMethodDescriptor(VOID_TYPE, paramType), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GENERATE <setter>(v)
                _ALOAD(0);
                _ILOAD_OF(paramType, 1);
                _INVOKEVIRTUAL(generatedType, setter.getName(), setterDescriptor);
            }});
        }

        @Override
        public void applyConventionMappingToSetter(PropertyMetadata property, Method setter) {
            if (!conventionAware) {
                return;
            }

            // GENERATE public <return-type> <setter>(<type> v) { <return-type> v = super.<setter>(v); __<prop>__ = true; return v; }
            addConventionSetter(setter, property);
        }

        @Override
        public void applyConventionMappingToSetMethod(PropertyMetadata property, Method setter) {
            if (!mixInDsl || !conventionAware) {
                return;
            }

            // GENERATE public <returnType> <propName>(<type> v) { val = super.<propName>(v); __<prop>__ = true; return val; }
            addConventionSetter(setter, property);
        }

        private void addConventionSetter(Method setter, PropertyMetadata property) {
            Type paramType = getType(setter.getParameterTypes()[0]);
            Type returnType = getType(setter.getReturnType());
            String methodDescriptor = getMethodDescriptor(returnType, paramType);

            publicMethod(setter.getName(), methodDescriptor, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                // GENERATE super.<propName>(v)
                _ALOAD(0);
                _ILOAD_OF(paramType, 1);
                _INVOKESPECIAL(superclassType, setter.getName(), methodDescriptor);

                // GENERATE __<prop>__ = true
                _ALOAD(0);
                _LDC(true);
                _PUTFIELD(generatedType, propFieldName(property), BOOLEAN_TYPE);

                // END
                _IRETURN_OF(returnType);
            }});
        }

        @Override
        public void addActionMethod(Method method) {
            if (!mixInDsl) {
                return;
            }

            Type returnType = getType(method.getReturnType());

            Type[] originalParameterTypes = collectArray(method.getParameterTypes(), Type.class, Type::getType);
            Type lastParameterType = originalParameterTypes[originalParameterTypes.length - 1];

            int numParams = originalParameterTypes.length;
            Type[] closurisedParameterTypes = new Type[numParams];
            System.arraycopy(originalParameterTypes, 0, closurisedParameterTypes, 0, numParams);
            closurisedParameterTypes[numParams - 1] = CLOSURE_TYPE;

            final String methodDescriptor = getMethodDescriptor(returnType, closurisedParameterTypes);

            // GENERATE public <return type> <method>(Closure v) { return <method>(…, ConfigureUtil.configureUsing(v)); }
            publicMethod(method.getName(), methodDescriptor, methodVisitor -> new MethodVisitorScope(methodVisitor) {{

                // GENERATE <method>(…, ConfigureUtil.configureUsing(v));
                _ALOAD(0);

                int stackVar = 1;
                for (int typeVar = 0; typeVar < numParams - 1; ++typeVar) {
                    Type argType = closurisedParameterTypes[typeVar];
                    _ILOAD_OF(argType, stackVar);
                    stackVar += argType.getSize();
                }

                // GENERATE ConfigureUtil.configureUsing(v);
                _ALOAD(stackVar);

                assert lastParameterType.equals(ACTION_TYPE) || lastParameterType.equals(ISOLATED_ACTION_TYPE);

                String methodName = lastParameterType.equals(ISOLATED_ACTION_TYPE)
                    ? "configureUsingIsolatedAction"
                    : "configureUsing";
                _INVOKESTATIC(CONFIGURE_UTIL_TYPE, methodName, getMethodDescriptor(lastParameterType, CLOSURE_TYPE));
                _INVOKEVIRTUAL(generatedType, method.getName(), getMethodDescriptor(getType(method.getReturnType()), originalParameterTypes));

                _IRETURN_OF(returnType);
            }});
        }

        private void generateToStringSupport() {
            // Generate
            // if (displayName != null) {
            //     return displayName.getDisplayName()
            // } else if (AsmBackedClassGenerator.getDisplayNameForNext() != null) {
            //     return AsmBackedClassGenerator.getDisplayNameForNext().getDisplayName()
            // } else {
            //     return super.toString()
            // }
            publicMethod("toString", RETURN_STRING, methodVisitor -> new MethodVisitorScope(methodVisitor) {{

                // Generate: if (displayName != null) { return displayName.getDisplayName() }
                _ALOAD(0);
                _GETFIELD(generatedType, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE);
                _DUP();
                Label label1 = new Label();
                _IFNULL(label1);
                _INVOKEINTERFACE(DESCRIBABLE_TYPE, "getDisplayName", RETURN_STRING);
                _ARETURN();

                // Generate: if (...) { return ... }
                visitLabel(label1);
                _ALOAD(0);
                _INVOKESTATIC(ASM_BACKED_CLASS_GENERATOR_TYPE, GET_DISPLAY_NAME_FOR_NEXT_METHOD_NAME, RETURN_DESCRIBABLE);
                _DUP();
                Label label2 = new Label();
                _IFNULL(label2);
                _INVOKEINTERFACE(DESCRIBABLE_TYPE, "getDisplayName", RETURN_STRING);
                _ARETURN();

                // Generate: return super.toString()
                visitLabel(label2);
                _ALOAD(0);
                _INVOKESPECIAL(OBJECT_TYPE, "toString", RETURN_STRING);
                _ARETURN();
            }});
        }

        private void generateServiceRegistrySupport() {
            // GENERATE private transient ServiceLookup services;
            // GENERATE if (services != null) { return services; } else { return AsmBackedClassGenerator.getServicesForNext(); }
            addServiceSupport(SERVICES_FIELD, SERVICE_LOOKUP_TYPE, SERVICES_METHOD, GET_SERVICES_FOR_NEXT_METHOD_NAME, RETURN_SERVICE_LOOKUP);
        }

        private void generateManagedPropertyCreationSupport() {
            // GENERATE private transient ManagedObjectFactory factory;
            // GENERATE if (factory != null) { return factory; } else { return AsmBackedClassGenerator.getFactoryForNext(); }
            addServiceSupport(FACTORY_FIELD, MANAGED_OBJECT_FACTORY_TYPE, FACTORY_METHOD, GET_FACTORY_FOR_NEXT_METHOD_NAME, RETURN_MANAGED_OBJECT_FACTORY);
        }

        private void addServiceSupport(String fieldName, Type fieldType, String getterName, String runtimeGetterName, String getterDescriptor) {
            addField(ACC_PRIVATE | ACC_SYNTHETIC | ACC_TRANSIENT, fieldName, fieldType);
            addServiceGetter(getterName, fieldName, fieldType, runtimeGetterName, getterDescriptor);
        }

        private void includeNotInheritedAnnotations() {
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                if (annotation.annotationType().getAnnotation(Inherited.class) != null) {
                    continue;
                }
                Retention retention = annotation.annotationType().getAnnotation(Retention.class);
                boolean visible = retention != null && retention.value() == RetentionPolicy.RUNTIME;
                AnnotationVisitor annotationVisitor = visitAnnotation(descriptorOf(annotation.annotationType()), visible);
                visitAnnotationValues(annotation, annotationVisitor);
                annotationVisitor.visitEnd();
            }
        }

        private void visitAnnotationValues(Annotation annotation, AnnotationVisitor annotationVisitor) {
            for (Method method : annotation.annotationType().getDeclaredMethods()) {
                String name = method.getName();
                Class<?> returnType = method.getReturnType();
                if (returnType.isEnum()) {
                    annotationVisitor.visitEnum(name, descriptorOf(returnType), getAnnotationParameterValue(annotation, method).toString());
                } else if (returnType.isArray() && !PRIMITIVE_TYPES.contains(returnType.getComponentType())) {
                    AnnotationVisitor arrayVisitor = annotationVisitor.visitArray(name);
                    Object[] elements = (Object[]) getAnnotationParameterValue(annotation, method);
                    visitArrayElements(arrayVisitor, returnType.getComponentType(), elements);
                    arrayVisitor.visitEnd();
                } else if (returnType.equals(Class.class)) {
                    Class<?> clazz = (Class<?>) getAnnotationParameterValue(annotation, method);
                    annotationVisitor.visit(name, getType(clazz));
                } else if (returnType.isAnnotation()) {
                    Annotation nestedAnnotation = (Annotation) getAnnotationParameterValue(annotation, method);
                    AnnotationVisitor nestedAnnotationVisitor = annotationVisitor.visitAnnotation(name, descriptorOf(returnType));
                    visitAnnotationValues(nestedAnnotation, nestedAnnotationVisitor);
                    nestedAnnotationVisitor.visitEnd();
                } else {
                    annotationVisitor.visit(name, getAnnotationParameterValue(annotation, method));
                }
            }
        }

        private void visitArrayElements(AnnotationVisitor arrayVisitor, Class<?> arrayElementType, Object[] arrayElements) {
            if (arrayElementType.isEnum()) {
                String enumDescriptor = descriptorOf(arrayElementType);
                for (Object value : arrayElements) {
                    arrayVisitor.visitEnum(null, enumDescriptor, value.toString());
                }
            } else if (arrayElementType.equals(Class.class)) {
                for (Object value : arrayElements) {
                    Class<?> clazz = (Class<?>) value;
                    arrayVisitor.visit(null, getType(clazz));
                }
            } else if (arrayElementType.isAnnotation()) {
                for (Object annotation : arrayElements) {
                    AnnotationVisitor nestedAnnotationVisitor = arrayVisitor.visitAnnotation(null, descriptorOf(arrayElementType));
                    visitAnnotationValues((Annotation) annotation, nestedAnnotationVisitor);
                    nestedAnnotationVisitor.visitEnd();
                }
            } else {
                for (Object value : arrayElements) {
                    arrayVisitor.visit(null, value);
                }
            }
        }

        private static Object getAnnotationParameterValue(Annotation annotation, Method method) {
            try {
                return method.invoke(annotation);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private static void attachFactoryIdToImplType(Class<?> implClass, int id) {
            try {
                Field factoryField = implClass.getDeclaredField(FACTORY_ID_FIELD);
                factoryField.setAccessible(true);
                factoryField.set(null, id);
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void addNameProperty() {
            addField(ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, NAME_FIELD, STRING_TYPE);
            addGetter("getName", STRING_TYPE, getMethodDescriptor(STRING_TYPE), methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _GETFIELD(generatedType, NAME_FIELD, STRING_TYPE);
            }});
        }

        @Override
        public Class<?> generate() {
            writeGenericReturnTypeFields();
            visitEnd();

            Class<?> generatedClass = classGenerator.define();

            if (managed) {
                attachFactoryIdToImplType(generatedClass, factoryId);
            }

            return generatedClass;
        }

        private void writeGenericReturnTypeFields() {
            if (!genericReturnTypeConstantsIndex.isEmpty()) {
                addMethod(ACC_STATIC, "<clinit>", "()V", methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                    for (Map.Entry<java.lang.reflect.Type, ReturnTypeEntry> entry : genericReturnTypeConstantsIndex.entrySet()) {
                        ReturnTypeEntry returnType = entry.getValue();
                        addField(PV_FINAL_STATIC, returnType.fieldName, JAVA_REFLECT_TYPE_DESCRIPTOR);

                        // <class>.getDeclaredMethod(<getter-name>)
                        _LDC(generatedType);
                        _LDC(returnType.getterName);
                        _ICONST_0();
                        _ANEWARRAY(CLASS_TYPE);
                        _INVOKEVIRTUAL(CLASS_TYPE, "getDeclaredMethod", GET_DECLARED_METHOD_DESCRIPTOR);

                        // <method>.getGenericReturnType()
                        _INVOKEVIRTUAL(METHOD_TYPE, "getGenericReturnType", getMethodDescriptor(JAVA_LANG_REFLECT_TYPE));
                        _PUTSTATIC(generatedType, returnType.fieldName, JAVA_REFLECT_TYPE_DESCRIPTOR);
                    }
                    _RETURN();
                }});
            }
        }

        /**
         * GENERATE {@code $name { return ($fieldName != null) ? $fieldName : AsmBackedClassGenerator.$runtimeGetterName(); }}
         */
        private void addServiceGetter(String name, String fieldName, Type fieldType, String runtimeGetterName, String getterDescriptor) {
            privateSyntheticMethod(name, getterDescriptor, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
                _ALOAD(0);
                _GETFIELD(generatedType, fieldName, fieldType);
                _DUP();
                Label label = new Label();
                _IFNULL(label);
                _ARETURN();
                visitLabel(label);
                _INVOKESTATIC(ASM_BACKED_CLASS_GENERATOR_TYPE, runtimeGetterName, getterDescriptor);
                _ARETURN();
            }});
        }

        private final static class ReturnTypeEntry {
            private final String fieldName;
            private final String getterName;

            private ReturnTypeEntry(String fieldName, String getterName) {
                this.fieldName = fieldName;
                this.getterName = getterName;
            }
        }
    }

    @Nullable
    private static String getBuildServiceName(PropertyMetadata property) {
        ServiceReference annotation = property.findAnnotation(ServiceReference.class);
        if (annotation != null) {
            return annotation.value();
        }
        return null;
    }

    @Nonnull
    private static String descriptorOf(Class<?> type) {
        return getType(type).getDescriptor();
    }

    private static String propFieldName(PropertyMetadata property) {
        return propFieldName(property.getName());
    }

    public static String propFieldName(String name) {
        return "__" + name + "__";
    }

    private static Class<?> rawTypeParam(PropertyMetadata property, int paramNum) {
        java.lang.reflect.Type type = property.getGenericType();
        if (!(type instanceof ParameterizedType)) {
            throw new IllegalArgumentException("Declaration of property " + property.getName() + " does not include any type arguments in its property type " + type);
        }
        java.lang.reflect.Type argument = ((ParameterizedType) type).getActualTypeArguments()[paramNum];
        if (argument instanceof Class) {
            return (Class<?>) argument;
        }
        return (Class<?>) ((ParameterizedType) argument).getRawType();
    }

    private static class ObjectCreationDetails {
        final InstanceGenerator instantiator;
        final ServiceLookup services;
        @Nullable
        final Describable displayName;
        PropertyRoleAnnotationHandler roleHandler;

        ObjectCreationDetails(InstanceGenerator instantiator, ServiceLookup services, @Nullable Describable displayName, PropertyRoleAnnotationHandler roleHandler) {
            this.instantiator = instantiator;
            this.services = services;
            this.displayName = displayName;
            this.roleHandler = roleHandler;
        }
    }

    private static class NoOpBuilder implements ClassGenerationVisitor {
        private final Class<?> type;

        public NoOpBuilder(Class<?> type) {
            this.type = type;
        }

        @Override
        public void addConstructor(Constructor<?> constructor, boolean addNameParameter) {
        }

        @Override
        public void addDefaultConstructor() {
        }

        @Override
        public void addNameConstructor() {
        }

        @Override
        public void mixInDynamicAware() {
        }

        @Override
        public void addNoDeprecationConventionPrivateGetter() {
        }

        @Override
        public void mixInConventionAware() {
        }

        @Override
        public void mixInGroovyObject() {
        }

        @Override
        public void addDynamicMethods() {
        }

        @Override
        public void addExtensionsProperty() {
        }

        @Override
        public void applyServiceInjectionToProperty(PropertyMetadata property) {
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, MethodMetadata getter) {
        }

        @Override
        public void applyServiceInjectionToSetter(PropertyMetadata property, Method setter) {
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, Class<? extends Annotation> annotation, MethodMetadata getter) {
        }

        @Override
        public void applyServiceInjectionToSetter(PropertyMetadata property, Class<? extends Annotation> annotation, Method setter) {
        }

        @Override
        public void applyManagedStateToProperty(PropertyMetadata property) {
        }

        @Override
        public void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter, boolean applyRole) {
        }

        @Override
        public void applyManagedStateToGetter(PropertyMetadata property, Method getter) {
        }

        @Override
        public void applyManagedStateToSetter(PropertyMetadata property, Method setter) {
        }

        @Override
        public void addManagedMethods(List<PropertyMetadata> mutableProperties, List<PropertyMetadata> readOnlyProperties) {
        }

        @Override
        public void applyConventionMappingToProperty(PropertyMetadata property) {
        }

        @Override
        public void applyConventionMappingToGetter(PropertyMetadata property, MethodMetadata getter, boolean attachOwner, boolean applyRole) {
        }

        @Override
        public void applyConventionMappingToSetter(PropertyMetadata property, Method setter) {
        }

        @Override
        public void applyConventionMappingToSetMethod(PropertyMetadata property, Method setter) {
        }

        @Override
        public void addSetMethod(PropertyMetadata propertyMetaData, Method setter) {
        }

        @Override
        public void addActionMethod(Method method) {
        }

        @Override
        public void addLazyGroovySupportSetterOverloads(PropertyMetadata property, MethodMetadata getter) {
        }

        @Override
        public void addNameProperty() {
        }

        @Override
        public Class<?> generate() {
            return type;
        }
    }

    private static class InvokeConstructorStrategy implements InstantiationStrategy {
        private final Constructor<?> constructor;
        private final PropertyRoleAnnotationHandler roleHandler;

        public InvokeConstructorStrategy(Constructor<?> constructor, PropertyRoleAnnotationHandler roleHandler) {
            this.constructor = constructor;
            this.roleHandler = roleHandler;
        }

        @Override
        public Object newInstance(ServiceLookup services, InstanceGenerator nested, @Nullable Describable displayName, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
            ObjectCreationDetails previous = SERVICES_FOR_NEXT_OBJECT.get();
            SERVICES_FOR_NEXT_OBJECT.set(new ObjectCreationDetails(nested, services, displayName, roleHandler));
            try {
                return constructor.newInstance(params);
            } finally {
                SERVICES_FOR_NEXT_OBJECT.set(previous);
            }
        }
    }

    private static class InvokeSerializationConstructorAndInitializeFieldsStrategy implements InstantiationStrategy {
        private final PropertyRoleAnnotationHandler roleHandler;
        private final Constructor<?> constructor;
        private final Method initMethod;

        public InvokeSerializationConstructorAndInitializeFieldsStrategy(Constructor<?> constructor, Method initMethod, PropertyRoleAnnotationHandler roleHandler) {
            this.constructor = constructor;
            this.initMethod = initMethod;
            this.roleHandler = roleHandler;
        }

        @Override
        public Object newInstance(ServiceLookup services, InstanceGenerator nested, @Nullable Describable displayName, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
            ObjectCreationDetails previous = SERVICES_FOR_NEXT_OBJECT.get();
            SERVICES_FOR_NEXT_OBJECT.set(new ObjectCreationDetails(nested, services, displayName, roleHandler));
            try {
                Object instance = constructor.newInstance();
                initMethod.invoke(instance);
                return instance;
            } finally {
                SERVICES_FOR_NEXT_OBJECT.set(previous);
            }
        }
    }
}
