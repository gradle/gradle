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
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaProperty;
import org.gradle.api.Action;
import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GeneratedSubclass;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Pair;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.extensibility.ConventionAwareHelper;
import org.gradle.internal.instantiation.ClassGenerationException;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.state.Managed;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.OwnerAware;
import org.gradle.model.internal.asm.AsmClassGenerator;
import org.gradle.model.internal.asm.ClassGeneratorSuffixRegistry;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.ConfigureUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import sun.reflect.ReflectionFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.getterSignature;
import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.signature;
import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.unboxOrCast;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACC_TRANSIENT;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.V1_8;
import static org.objectweb.asm.Type.VOID_TYPE;

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

    private AsmBackedClassGenerator(boolean decorate, String suffix,
                                    Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
                                    Collection<Class<? extends Annotation>> enabledInjectAnnotations,
                                    PropertyRoleAnnotationHandler roleHandler,
                                    CrossBuildInMemoryCache<Class<?>, GeneratedClassImpl> generatedClasses,
                                    int factoryId) {
        super(allKnownAnnotations, enabledInjectAnnotations, roleHandler, generatedClasses);
        this.decorate = decorate;
        this.suffix = suffix;
        this.factoryId = factoryId;
    }

    /**
     * Returns a generator that applies DSL mix-in, extensibility and service injection for generated classes.
     */
    static ClassGenerator decorateAndInject(Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
                                            PropertyRoleAnnotationHandler roleHandler,
                                            Collection<Class<? extends Annotation>> enabledInjectAnnotations,
                                            CrossBuildInMemoryCacheFactory cacheFactory,
                                            int factoryId) {
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
    static ClassGenerator injectOnly(Collection<? extends InjectAnnotationHandler> allKnownAnnotations,
                                     PropertyRoleAnnotationHandler roleHandler,
                                     Collection<Class<? extends Annotation>> enabledInjectAnnotations,
                                     CrossBuildInMemoryCacheFactory cacheFactory,
                                     int factoryId) {
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
            constructor = ReflectionFactory.getReflectionFactory().newConstructorForSerialization(generatedType, baseClass.getDeclaredConstructor());
        } catch (NoSuchMethodException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        Method method = CollectionUtils.findFirst(generatedType.getDeclaredMethods(), m -> m.getName().equals(ClassBuilderImpl.INIT_METHOD));
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
        private final List<Pair<PropertyMetadata, Boolean>> propertiesToAttach = new ArrayList<>();
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
            propertiesToAttach.add(Pair.of(property, applyRole));
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
            ClassBuilderImpl builder = new ClassBuilderImpl(type, decorate, suffix, factoryId, extensible, conventionAware, managed, providesOwnDynamicObjectImplementation, requiresToString, requiresServicesMethod, requiresFactory, propertiesToAttach, ineligibleProperties);
            builder.startClass();
            return builder;
        }
    }

    private static class ClassBuilderImpl implements ClassGenerationVisitor {
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
        private static final String CONVENTION_MAPPING_FIELD_DESCRIPTOR = Type.getDescriptor(ConventionMapping.class);
        private static final String META_CLASS_TYPE_DESCRIPTOR = Type.getDescriptor(MetaClass.class);
        private final static Type META_CLASS_TYPE = Type.getType(MetaClass.class);
        private final static Type GENERATED_SUBCLASS_TYPE = Type.getType(GeneratedSubclass.class);
        private final static Type MODEL_OBJECT_TYPE = Type.getType(ModelObject.class);
        private final static Type OWNER_AWARE_TYPE = Type.getType(OwnerAware.class);
        private final static Type CONVENTION_AWARE_TYPE = Type.getType(IConventionAware.class);
        private final static Type CONVENTION_AWARE_HELPER_TYPE = Type.getType(ConventionAwareHelper.class);
        private final static Type DYNAMIC_OBJECT_AWARE_TYPE = Type.getType(DynamicObjectAware.class);
        private final static Type EXTENSION_AWARE_TYPE = Type.getType(ExtensionAware.class);
        @SuppressWarnings("deprecation")
        private final static Type HAS_CONVENTION_TYPE = Type.getType(org.gradle.api.internal.HasConvention.class);
        private final static Type DYNAMIC_OBJECT_TYPE = Type.getType(DynamicObject.class);
        private final static Type CONVENTION_MAPPING_TYPE = Type.getType(ConventionMapping.class);
        private final static Type GROOVY_OBJECT_TYPE = Type.getType(GroovyObject.class);
        private final static Type CONVENTION_TYPE = Type.getType(Convention.class);
        private final static Type ASM_BACKED_CLASS_GENERATOR_TYPE = Type.getType(AsmBackedClassGenerator.class);
        private final static Type ABSTRACT_DYNAMIC_OBJECT_TYPE = Type.getType(AbstractDynamicObject.class);
        private final static Type EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE = Type.getType(MixInExtensibleDynamicObject.class);
        private final static Type NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE = Type.getType(BeanDynamicObject.class);
        private static final String JAVA_REFLECT_TYPE_DESCRIPTOR = Type.getDescriptor(java.lang.reflect.Type.class);
        private static final Type CONFIGURE_UTIL_TYPE = Type.getType(ConfigureUtil.class);
        private static final Type CLOSURE_TYPE = Type.getType(Closure.class);
        private static final Type SERVICE_REGISTRY_TYPE = Type.getType(ServiceRegistry.class);
        private static final Type SERVICE_LOOKUP_TYPE = Type.getType(ServiceLookup.class);
        private static final Type MANAGED_OBJECT_FACTORY_TYPE = Type.getType(ManagedObjectFactory.class);
        private static final Type JAVA_LANG_REFLECT_TYPE = Type.getType(java.lang.reflect.Type.class);
        private static final Type OBJECT_TYPE = Type.getType(Object.class);
        private static final Type CLASS_TYPE = Type.getType(Class.class);
        private static final Type METHOD_TYPE = Type.getType(Method.class);
        private static final Type STRING_TYPE = Type.getType(String.class);
        private static final Type CLASS_ARRAY_TYPE = Type.getType(Class[].class);
        private static final Type GROOVY_SYSTEM_TYPE = Type.getType(GroovySystem.class);
        private static final Type META_CLASS_REGISTRY_TYPE = Type.getType(MetaClassRegistry.class);
        private static final Type BOOLEAN_TYPE = Type.getType(Boolean.TYPE);
        private static final Type OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
        private static final Type ACTION_TYPE = Type.getType(Action.class);
        private static final Type PROPERTY_INTERNAL_TYPE = Type.getType(PropertyInternal.class);
        private static final Type MANAGED_TYPE = Type.getType(Managed.class);
        private static final Type EXTENSION_CONTAINER_TYPE = Type.getType(ExtensionContainer.class);
        private static final Type DESCRIBABLE_TYPE = Type.getType(Describable.class);
        private static final Type DISPLAY_NAME_TYPE = Type.getType(DisplayName.class);
        private static final Type INJECT_TYPE = Type.getType(Inject.class);

        private static final String RETURN_STRING = Type.getMethodDescriptor(STRING_TYPE);
        private static final String RETURN_DESCRIBABLE = Type.getMethodDescriptor(DESCRIBABLE_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT_SERVICE_LOOKUP = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, CLASS_TYPE, DYNAMIC_OBJECT_TYPE, SERVICE_LOOKUP_TYPE);
        private static final String RETURN_OBJECT_FROM_STRING_OBJECT_BOOLEAN = Type.getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE, STRING_TYPE, Type.BOOLEAN_TYPE);
        private static final String RETURN_CLASS = Type.getMethodDescriptor(CLASS_TYPE);
        private static final String RETURN_BOOLEAN = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
        private static final String RETURN_VOID = Type.getMethodDescriptor(Type.VOID_TYPE);
        private static final String RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION = Type.getMethodDescriptor(Type.VOID_TYPE, CONVENTION_AWARE_TYPE, CONVENTION_TYPE);
        private static final String RETURN_CONVENTION = Type.getMethodDescriptor(CONVENTION_TYPE);
        private static final String RETURN_CONVENTION_MAPPING = Type.getMethodDescriptor(CONVENTION_MAPPING_TYPE);
        private static final String RETURN_OBJECT = Type.getMethodDescriptor(OBJECT_TYPE);
        private static final String RETURN_EXTENSION_CONTAINER = Type.getMethodDescriptor(EXTENSION_CONTAINER_TYPE);
        private static final String RETURN_OBJECT_FROM_STRING = Type.getMethodDescriptor(OBJECT_TYPE, STRING_TYPE);
        private static final String RETURN_OBJECT_FROM_STRING_OBJECT = Type.getMethodDescriptor(OBJECT_TYPE, STRING_TYPE, OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_STRING_OBJECT = Type.getMethodDescriptor(Type.VOID_TYPE, STRING_TYPE, OBJECT_TYPE);
        private static final String RETURN_DYNAMIC_OBJECT = Type.getMethodDescriptor(DYNAMIC_OBJECT_TYPE);
        private static final String RETURN_META_CLASS_FROM_CLASS = Type.getMethodDescriptor(META_CLASS_TYPE, CLASS_TYPE);
        private static final String RETURN_BOOLEAN_FROM_STRING = Type.getMethodDescriptor(BOOLEAN_TYPE, STRING_TYPE);
        private static final String RETURN_META_CLASS_REGISTRY = Type.getMethodDescriptor(META_CLASS_REGISTRY_TYPE);
        private static final String RETURN_SERVICE_REGISTRY = Type.getMethodDescriptor(SERVICE_REGISTRY_TYPE);
        private static final String RETURN_SERVICE_LOOKUP = Type.getMethodDescriptor(SERVICE_LOOKUP_TYPE);
        private static final String RETURN_MANAGED_OBJECT_FACTORY = Type.getMethodDescriptor(MANAGED_OBJECT_FACTORY_TYPE);
        private static final String RETURN_META_CLASS = Type.getMethodDescriptor(META_CLASS_TYPE);
        private static final String RETURN_VOID_FROM_META_CLASS = Type.getMethodDescriptor(Type.VOID_TYPE, META_CLASS_TYPE);
        private static final String GET_DECLARED_METHOD_DESCRIPTOR = Type.getMethodDescriptor(METHOD_TYPE, STRING_TYPE, CLASS_ARRAY_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT_MODEL_OBJECT = Type.getMethodDescriptor(VOID_TYPE, OBJECT_TYPE, MODEL_OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_MODEL_OBJECT_DISPLAY_NAME = Type.getMethodDescriptor(VOID_TYPE, MODEL_OBJECT_TYPE, DISPLAY_NAME_TYPE);
        private static final String RETURN_OBJECT_FROM_TYPE = Type.getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE);
        private static final String RETURN_OBJECT_FROM_OBJECT_MODEL_OBJECT_STRING = Type.getMethodDescriptor(OBJECT_TYPE, OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE);
        private static final String RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS = Type.getMethodDescriptor(OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE, CLASS_TYPE);
        private static final String RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS = Type.getMethodDescriptor(OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE, CLASS_TYPE, CLASS_TYPE);
        private static final String RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS_CLASS = Type.getMethodDescriptor(OBJECT_TYPE, MODEL_OBJECT_TYPE, STRING_TYPE, CLASS_TYPE, CLASS_TYPE, CLASS_TYPE);
        private static final String RETURN_VOID_FROM_STRING = Type.getMethodDescriptor(VOID_TYPE, STRING_TYPE);

        private static final String[] EMPTY_STRINGS = new String[0];
        private static final Type[] EMPTY_TYPES = new Type[0];

        private final ClassWriter visitor;
        private final Class<?> type;
        private final boolean managed;
        private final Type generatedType;
        private final Type superclassType;
        private final Map<java.lang.reflect.Type, ReturnTypeEntry> genericReturnTypeConstantsIndex = Maps.newHashMap();
        private final AsmClassGenerator classGenerator;
        private final int factoryId;
        private boolean hasMappingField;
        private final boolean conventionAware;
        private final boolean mixInDsl;
        private final boolean extensible;
        private final boolean providesOwnDynamicObject;
        private final boolean requiresToString;
        private final List<Pair<PropertyMetadata, Boolean>> propertiesToAttach;
        private final List<PropertyMetadata> ineligibleProperties;
        private final boolean requiresServicesMethod;
        private final boolean requiresFactory;

        private ClassBuilderImpl(
            Class<?> type,
            boolean decorated,
            String suffix,
            int factoryId,
            boolean extensible,
            boolean conventionAware,
            boolean managed,
            boolean providesOwnDynamicObject,
            boolean requiresToString,
            boolean requiresServicesMethod,
            boolean requiresFactory,
            List<Pair<PropertyMetadata, Boolean>> propertiesToAttach,
            List<PropertyMetadata> ineligibleProperties
        ) {
            this.type = type;
            this.factoryId = factoryId;
            this.managed = managed;
            this.requiresToString = requiresToString;
            this.propertiesToAttach = propertiesToAttach;
            this.classGenerator = new AsmClassGenerator(type, suffix);
            this.visitor = classGenerator.getVisitor();
            this.generatedType = classGenerator.getGeneratedType();
            this.superclassType = Type.getType(type);
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

            visitor.visit(V1_8, ACC_PUBLIC | ACC_SYNTHETIC, generatedType.getInternalName(), null,
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
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "<init>", RETURN_VOID, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // this.super()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "<init>", RETURN_VOID, false);

            // this.init_method()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), INIT_METHOD, RETURN_VOID, false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addNameConstructor() {
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "<init>", RETURN_VOID_FROM_STRING, null, EMPTY_STRINGS);

            methodVisitor.visitAnnotation(INJECT_TYPE.getDescriptor(), true)
                .visitEnd();

            methodVisitor.visitCode();

            // this.super()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "<init>", RETURN_VOID, false);

            // this.name = name
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), NAME_FIELD, STRING_TYPE.getDescriptor());

            // this.init_method()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), INIT_METHOD, RETURN_VOID, false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addConstructor(Constructor<?> constructor, boolean addNameParameter) {
            List<Type> paramTypes = new ArrayList<>();
            for (Class<?> paramType : constructor.getParameterTypes()) {
                paramTypes.add(Type.getType(paramType));
            }
            String superMethodDescriptor = Type.getMethodDescriptor(VOID_TYPE, paramTypes.toArray(EMPTY_TYPES));

            String methodDescriptor;
            if (addNameParameter) {
                paramTypes.add(0, STRING_TYPE);
                methodDescriptor = Type.getMethodDescriptor(VOID_TYPE, paramTypes.toArray(EMPTY_TYPES));
            } else {
                methodDescriptor = superMethodDescriptor;
            }

            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "<init>", methodDescriptor, signature(constructor, addNameParameter), EMPTY_STRINGS);

            for (Annotation annotation : constructor.getDeclaredAnnotations()) {
                if (annotation.annotationType().getAnnotation(Inherited.class) != null) {
                    continue;
                }
                Retention retention = annotation.annotationType().getAnnotation(Retention.class);
                AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation(Type.getType(annotation.annotationType()).getDescriptor(), retention != null && retention.value() == RetentionPolicy.RUNTIME);
                annotationVisitor.visitEnd();
            }

            methodVisitor.visitCode();

            // this.super(p0 .. pn)
            methodVisitor.visitVarInsn(ALOAD, 0);
            for (int typeVar = addNameParameter ? 1 : 0, stackVar = addNameParameter ? 2 : 1; typeVar < paramTypes.size(); ++typeVar) {
                Type argType = paramTypes.get(typeVar);
                methodVisitor.visitVarInsn(argType.getOpcode(ILOAD), stackVar);
                stackVar += argType.getSize();
            }
            methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), "<init>", superMethodDescriptor, false);

            if (addNameParameter) {
                // this.name = name
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), NAME_FIELD, STRING_TYPE.getDescriptor());
            }

            // this.init_method()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), INIT_METHOD, RETURN_VOID, false);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generateInitMethod() {
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PRIVATE | ACC_SYNTHETIC, INIT_METHOD, RETURN_VOID, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            initializeFields(methodVisitor);

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void initializeFields(MethodVisitor methodVisitor) {
            // this.displayName = AsmBackedClassGenerator.getDisplayNameForNext()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), GET_DISPLAY_NAME_FOR_NEXT_METHOD_NAME, RETURN_DESCRIBABLE, false);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE.getDescriptor());

            if (requiresServicesMethod) {
                // this.services = AsmBackedClassGenerator.getServicesForNext()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), GET_SERVICES_FOR_NEXT_METHOD_NAME, RETURN_SERVICE_LOOKUP, false);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), SERVICES_FIELD, SERVICE_LOOKUP_TYPE.getDescriptor());
            }
            if (requiresFactory) {
                // this.factory = AsmBackedClassGenerator.getFactoryForNext()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), GET_FACTORY_FOR_NEXT_METHOD_NAME, RETURN_MANAGED_OBJECT_FACTORY, false);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), FACTORY_FIELD, MANAGED_OBJECT_FACTORY_TYPE.getDescriptor());
            }

            for (Pair<PropertyMetadata, Boolean> entry : propertiesToAttach) {
                // ManagedObjectFactory.attachOwner(get<prop>(), this, <property-name>))
                PropertyMetadata property = entry.left;
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), property.getMainGetter().getName(), Type.getMethodDescriptor(Type.getType(property.getMainGetter().getReturnType())), false);
                if (entry.right) {
                    methodVisitor.visitInsn(DUP);
                }
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitLdcInsn(property.getName());
                methodVisitor.visitMethodInsn(INVOKESTATIC, MANAGED_OBJECT_FACTORY_TYPE.getInternalName(), "attachOwner", RETURN_OBJECT_FROM_OBJECT_MODEL_OBJECT_STRING, false);
                if (entry.right) {
                    applyRoleTo(methodVisitor);
                }
            }

            // For classes that could have convention mapping, but implement IConventionAware themselves, we need to
            // mark ineligible-for-convention-mapping properties in a different way.
            // See mixInConventionAware() for how we do this for decorated types that do not implement IConventionAware manually
            //
            // Doing this for all types introduces a performance penalty for types that have Provider properties, even
            // if they don't use convention mapping.
            if (conventionAware && IConventionAware.class.isAssignableFrom(type)) {
                for (PropertyMetadata property : ineligibleProperties) {
                    // GENERATE getConventionMapping()
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getConventionMapping", RETURN_CONVENTION_MAPPING, false);
                    // GENERATE convention.ineligible(__property.getName()__)
                    methodVisitor.visitLdcInsn(property.getName());
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, CONVENTION_MAPPING_TYPE.getInternalName(), "ineligible", RETURN_VOID_FROM_STRING, true);
                }
            }
        }

        @Override
        public void addExtensionsProperty() {
            // GENERATE public ExtensionContainer getExtensions() { return getConvention(); }

            addGetter("getExtensions", EXTENSION_CONTAINER_TYPE, RETURN_EXTENSION_CONTAINER, null, visitor -> {

                // GENERATE getConvention()
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false);
            });
        }

        @Override
        public void mixInDynamicAware() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE private DynamicObject dynamicObjectHelper
            visitor.visitField(ACC_PRIVATE | ACC_TRANSIENT, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE.getDescriptor(), null, null);

            // END

            if (extensible) {

                // GENERATE public Convention getConvention() { return getAsDynamicObject().getConvention(); }
                addGetter("getConvention", CONVENTION_TYPE, RETURN_CONVENTION, null, visitor -> {

                    // GENERATE ((MixInExtensibleDynamicObject)getAsDynamicObject()).getConvention()
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);
                    visitor.visitTypeInsn(CHECKCAST, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                    visitor.visitMethodInsn(INVOKEVIRTUAL, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "getConvention", RETURN_CONVENTION, false);
                });

                // END

            }

            // END

            // GENERATE public DynamicObject getAsDynamicObject() {
            //      if (dynamicObjectHelper == null) {
            //          dynamicObjectHelper = <init>
            //      }
            //      return dynamicObjectHelper;
            // }

            addLazyGetter("getAsDynamicObject", DYNAMIC_OBJECT_TYPE, RETURN_DYNAMIC_OBJECT, null, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE, this::generateCreateDynamicObject);

            // END
        }

        private void generateCreateDynamicObject(MethodVisitor visitor) {
            if (extensible) {

                // GENERATE new MixInExtensibleDynamicObject(this, getClass().getSuperClass(), super.getAsDynamicObject(), this.services())

                visitor.visitTypeInsn(NEW, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                visitor.visitInsn(DUP);

                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getClass", RETURN_CLASS, false);
                visitor.visitMethodInsn(INVOKEVIRTUAL, CLASS_TYPE.getInternalName(), "getSuperclass", RETURN_CLASS, false);

                if (providesOwnDynamicObject) {
                    // GENERATE super.getAsDynamicObject()
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitMethodInsn(INVOKESPECIAL, Type.getType(type).getInternalName(),
                        "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);
                } else {
                    // GENERATE null
                    visitor.visitInsn(ACONST_NULL);
                }

                // GENERATE this.services()
                putServiceRegistryOnStack(visitor);

                visitor.visitMethodInsn(INVOKESPECIAL, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT_SERVICE_LOOKUP, false);
                // END
            } else {

                // GENERATE new BeanDynamicObject(this)

                visitor.visitTypeInsn(NEW, NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                visitor.visitInsn(DUP);

                visitor.visitVarInsn(ALOAD, 0);

                visitor.visitMethodInsn(INVOKESPECIAL, NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_OBJECT, false);
                // END
            }
        }

        @Override
        public void mixInConventionAware() {
            // GENERATE private ConventionMapping mapping

            visitor.visitField(ACC_PRIVATE | ACC_TRANSIENT, MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR, null, null);
            hasMappingField = true;

            // END

            // GENERATE public ConventionMapping getConventionMapping() {
            //     if (mapping == null) {
            //         mapping = new ConventionAwareHelper(this, getConvention());
            //     }
            //     return mapping;
            // }

            final MethodCodeBody initConventionAwareHelper = visitor -> {
                // GENERATE new ConventionAwareHelper(this, getConvention())

                visitor.visitTypeInsn(NEW, CONVENTION_AWARE_HELPER_TYPE.getInternalName());
                visitor.visitInsn(DUP);
                visitor.visitVarInsn(ALOAD, 0);

                // GENERATE getConvention()

                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false);

                // END

                visitor.visitMethodInsn(INVOKESPECIAL, CONVENTION_AWARE_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION, false);

                // END

                for (PropertyMetadata property : ineligibleProperties) {
                    // GENERATE convention.ineligible(__property.getName()__)
                    visitor.visitInsn(DUP);
                    visitor.visitLdcInsn(property.getName());
                    visitor.visitMethodInsn(INVOKEINTERFACE, CONVENTION_MAPPING_TYPE.getInternalName(), "ineligible", RETURN_VOID_FROM_STRING, true);
                }
            };

            addLazyGetter("getConventionMapping", CONVENTION_MAPPING_TYPE, RETURN_CONVENTION_MAPPING, null, MAPPING_FIELD, CONVENTION_MAPPING_TYPE, initConventionAwareHelper);

            // END
        }

        @Override
        public void mixInGroovyObject() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass())

            visitor.visitField(ACC_PRIVATE | ACC_TRANSIENT, META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR, null, null);

            // GENERATE public MetaClass getMetaClass() {
            //     if (metaClass == null) {
            //         metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
            //     }
            //     return metaClass;
            // }

            final MethodCodeBody initMetaClass = visitor -> {
                // GroovySystem.getMetaClassRegistry()
                visitor.visitMethodInsn(INVOKESTATIC, GROOVY_SYSTEM_TYPE.getInternalName(), "getMetaClassRegistry", RETURN_META_CLASS_REGISTRY, false);

                // this.getClass()
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitMethodInsn(INVOKEVIRTUAL, OBJECT_TYPE.getInternalName(), "getClass", RETURN_CLASS, false);

                // getMetaClass(..)
                visitor.visitMethodInsn(INVOKEINTERFACE, META_CLASS_REGISTRY_TYPE.getInternalName(), "getMetaClass", RETURN_META_CLASS_FROM_CLASS, true);
            };

            addLazyGetter("getMetaClass", META_CLASS_TYPE, RETURN_META_CLASS, null, META_CLASS_FIELD, META_CLASS_TYPE, initMetaClass);

            // END

            // GENERATE public void setMetaClass(MetaClass class) { this.metaClass = class; }

            addSetter("setMetaClass", RETURN_VOID_FROM_META_CLASS, visitor -> {
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitVarInsn(ALOAD, 1);
                visitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR);
            });
        }

        private void addSetter(String methodName, String methodDescriptor, MethodCodeBody body) {
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, methodName, methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addPropertySetterOverloads(PropertyMetadata property, MethodMetadata getter) {
            if (!mixInDsl) {
                return;
            }

            // GENERATE public void set<Name>(Object p) {
            //    ((PropertyInternal)<getter>()).setFromAnyValue(p);
            // }

            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, MetaProperty.getSetterName(property.getName()), RETURN_VOID_FROM_OBJECT, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), getter.getName(), Type.getMethodDescriptor(Type.getType(getter.getReturnType())), false);
            methodVisitor.visitTypeInsn(CHECKCAST, PROPERTY_INTERNAL_TYPE.getInternalName());
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, PROPERTY_INTERNAL_TYPE.getInternalName(), "setFromAnyValue", ClassBuilderImpl.RETURN_VOID_FROM_OBJECT, true);
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Adds a getter that returns the value of the given field, initializing it if null using the given code. The code should leave the value on the top of the stack.
         */
        private void addLazyGetter(String methodName, Type returnType, String methodDescriptor, @Nullable String signature, final String fieldName, final Type fieldType, final MethodCodeBody initializer) {
            addGetter(methodName, returnType, methodDescriptor, signature, visitor -> {
                // var = this.<field>
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), fieldName, fieldType.getDescriptor());
                visitor.visitVarInsn(ASTORE, 1);
                // if (var == null) { var = <code-body>; this.<field> = var; }
                visitor.visitVarInsn(ALOAD, 1);
                Label returnValue = new Label();
                visitor.visitJumpInsn(IFNONNULL, returnValue);
                initializer.add(visitor);
                visitor.visitVarInsn(ASTORE, 1);
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitVarInsn(ALOAD, 1);
                visitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), fieldName, fieldType.getDescriptor());
                visitor.visitLabel(returnValue);
                // return var
                visitor.visitVarInsn(ALOAD, 1);
            });
        }

        /**
         * Adds a getter that returns the value that the given code leaves on the top of the stack.
         */
        private void addGetter(String methodName, Type returnType, String methodDescriptor, @Nullable String signature, MethodCodeBody body) {
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, methodName, methodDescriptor, signature, EMPTY_STRINGS);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addDynamicMethods() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE public Object getProperty(String name) { return getAsDynamicObject().getProperty(name); }

            addGetter("getProperty", OBJECT_TYPE, RETURN_OBJECT_FROM_STRING, null, methodVisitor -> {
                // GENERATE getAsDynamicObject().getProperty(name);

                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "getProperty", RETURN_OBJECT_FROM_STRING, true);

                // END
            });

            // GENERATE public boolean hasProperty(String name) { return getAsDynamicObject().hasProperty(name) }

            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "hasProperty", RETURN_BOOLEAN_FROM_STRING, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().hasProperty(name);

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "hasProperty", RETURN_BOOLEAN_FROM_STRING, true);

            // END
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // GENERATE public void setProperty(String name, Object value) { getAsDynamicObject().setProperty(name, value); }

            addSetter("setProperty", RETURN_VOID_FROM_STRING_OBJECT, setter -> {
                // GENERATE getAsDynamicObject().setProperty(name, value)

                setter.visitVarInsn(ALOAD, 0);
                setter.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

                setter.visitVarInsn(ALOAD, 1);
                setter.visitVarInsn(ALOAD, 2);
                setter.visitMethodInsn(INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "setProperty", RETURN_VOID_FROM_STRING_OBJECT, true);

                // END
            });

            // GENERATE public Object invokeMethod(String name, Object params) { return getAsDynamicObject().invokeMethod(name, (Object[])params); }

            addGetter("invokeMethod", OBJECT_TYPE, RETURN_OBJECT_FROM_STRING_OBJECT, null, getter -> {
                String invokeMethodDesc = Type.getMethodDescriptor(OBJECT_TYPE, STRING_TYPE, OBJECT_ARRAY_TYPE);

                // GENERATE getAsDynamicObject().invokeMethod(name, (args instanceof Object[]) ? args : new Object[] { args })

                getter.visitVarInsn(ALOAD, 0);
                getter.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

                getter.visitVarInsn(ALOAD, 1);

                // GENERATE (args instanceof Object[]) ? args : new Object[] { args }
                getter.visitVarInsn(ALOAD, 2);
                getter.visitTypeInsn(INSTANCEOF, OBJECT_ARRAY_TYPE.getDescriptor());
                Label end = new Label();
                Label notArray = new Label();
                getter.visitJumpInsn(IFEQ, notArray);

                // Generate args
                getter.visitVarInsn(ALOAD, 2);
                getter.visitTypeInsn(CHECKCAST, OBJECT_ARRAY_TYPE.getDescriptor());
                getter.visitJumpInsn(GOTO, end);

                // Generate new Object[] { args }
                getter.visitLabel(notArray);
                getter.visitInsn(ICONST_1);
                getter.visitTypeInsn(ANEWARRAY, OBJECT_TYPE.getInternalName());
                getter.visitInsn(DUP);
                getter.visitInsn(ICONST_0);
                getter.visitVarInsn(ALOAD, 2);
                getter.visitInsn(AASTORE);

                getter.visitLabel(end);

                getter.visitMethodInsn(INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "invokeMethod", invokeMethodDesc, true);
            });
        }

        @Override
        public void applyServiceInjectionToProperty(PropertyMetadata property) {
            // GENERATE private <type> <property-field-name>;
            String fieldName = propFieldName(property);
            visitor.visitField(ACC_PRIVATE | ACC_TRANSIENT, fieldName, Type.getDescriptor(property.getType()), null, null);
        }

        private void generateServicesField() {
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC | ACC_TRANSIENT, SERVICES_FIELD, SERVICE_LOOKUP_TYPE.getDescriptor(), null, null);
        }

        private void generateGetServices() {
            MethodVisitor mv = visitor.visitMethod(ACC_PRIVATE | ACC_SYNTHETIC, SERVICES_METHOD, RETURN_SERVICE_LOOKUP, null, null);
            mv.visitCode();
            // GENERATE if (services != null) { return services; } else { return AsmBackedClassGenerator.getServicesForNext(); }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, generatedType.getInternalName(), SERVICES_FIELD, SERVICE_LOOKUP_TYPE.getDescriptor());
            mv.visitInsn(DUP);
            Label label = new Label();
            mv.visitJumpInsn(IFNULL, label);
            mv.visitInsn(ARETURN);
            mv.visitLabel(label);
            mv.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), GET_SERVICES_FOR_NEXT_METHOD_NAME, RETURN_SERVICE_LOOKUP, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, MethodMetadata getter) {
            applyServiceInjectionToGetter(property, null, getter);
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, @Nullable final Class<? extends Annotation> annotation, MethodMetadata getter) {
            // GENERATE public <type> <getter>() { if (<field> == null) { <field> = <services>>.get(<service-type>>); } return <field> }
            final String getterName = getter.getName();
            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            final Type serviceType = Type.getType(property.getType());
            final java.lang.reflect.Type genericServiceType = property.getGenericType();
            String propFieldName = propFieldName(property);
            String signature = getterSignature(getter.getGenericReturnType());

            addLazyGetter(getterName, returnType, methodDescriptor, signature, propFieldName, serviceType, methodVisitor -> {
                putServiceRegistryOnStack(methodVisitor);

                if (genericServiceType instanceof Class) {
                    // if the return type doesn't use generics, then it's faster to just rely on the type name directly
                    methodVisitor.visitLdcInsn(Type.getType((Class) genericServiceType));
                } else {
                    // load the static type descriptor from class constants
                    String constantFieldName = getConstantNameForGenericReturnType(genericServiceType, getterName);
                    methodVisitor.visitFieldInsn(GETSTATIC, generatedType.getInternalName(), constantFieldName, JAVA_REFLECT_TYPE_DESCRIPTOR);
                }

                if (annotation == null) {
                    // get(<type>)
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, SERVICE_LOOKUP_TYPE.getInternalName(), "get", RETURN_OBJECT_FROM_TYPE, true);
                } else {
                    // get(<type>, <annotation>)
                    methodVisitor.visitLdcInsn(Type.getType(annotation));
                    methodVisitor.visitMethodInsn(INVOKEINTERFACE, SERVICE_LOOKUP_TYPE.getInternalName(), "get", Type.getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE, CLASS_TYPE), true);
                }

                // (<type>)<service>
                methodVisitor.visitTypeInsn(CHECKCAST, serviceType.getInternalName());
            });
        }

        private void putServiceRegistryOnStack(MethodVisitor methodVisitor) {
            if (requiresServicesMethod) {
                // this.<services_method>()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), SERVICES_METHOD, RETURN_SERVICE_LOOKUP, false);
            } else {
                // this.getServices()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getServices", RETURN_SERVICE_REGISTRY, false);
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
            String fieldName = propFieldName(property);
            visitor.visitField(ACC_PRIVATE, fieldName, Type.getDescriptor(property.getType()), null, null);
        }

        @Override
        public void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter, boolean applyRole) {
            // GENERATE public <type> <getter>() {
            //     if (<field> == null) {
            //         <field> = getFactory().newInstance(this, <display-name>, <type>, <prop-name>);
            //     }
            //     return <field>;
            // }
            Type propType = Type.getType(property.getType());
            Type returnType = Type.getType(getter.getReturnType());
            addLazyGetter(getter.getName(), returnType, Type.getMethodDescriptor(returnType), null, propFieldName(property), propType, methodVisitor -> {
                // GENERATE factory = getFactory()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), FACTORY_METHOD, RETURN_MANAGED_OBJECT_FACTORY, false);

                // GENERATE return factory.newInstance(this, propertyName, ...)
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitLdcInsn(property.getName());

                int typeParamCount = property.getType().getTypeParameters().length;
                if (typeParamCount == 1) {
                    // GENERATE factory.newInstance(this, propertyName, type, valueType)
                    Type elementType = Type.getType(rawTypeParam(property, 0));
                    methodVisitor.visitLdcInsn(propType);
                    methodVisitor.visitLdcInsn(elementType);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, MANAGED_OBJECT_FACTORY_TYPE.getInternalName(), "newInstance", RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS, false);
                } else if (typeParamCount == 2) {
                    // GENERATE factory.newInstance(this, propertyName, type, keyType, valueType)
                    Type keyType = Type.getType(rawTypeParam(property, 0));
                    Type elementType = Type.getType(rawTypeParam(property, 1));
                    methodVisitor.visitLdcInsn(propType);
                    methodVisitor.visitLdcInsn(keyType);
                    methodVisitor.visitLdcInsn(elementType);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, MANAGED_OBJECT_FACTORY_TYPE.getInternalName(), "newInstance", RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS_CLASS_CLASS, false);
                } else {
                    // GENERATE factory.newInstance(this, propertyName, type)
                    methodVisitor.visitLdcInsn(propType);
                    methodVisitor.visitMethodInsn(INVOKEVIRTUAL, MANAGED_OBJECT_FACTORY_TYPE.getInternalName(), "newInstance", RETURN_OBJECT_FROM_MODEL_OBJECT_STRING_CLASS, false);
                }

                if (applyRole) {
                    methodVisitor.visitInsn(DUP);
                    applyRoleTo(methodVisitor);
                }

                methodVisitor.visitTypeInsn(CHECKCAST, propType.getInternalName());
            });
        }

        // Caller should place property value on the top of the stack
        private void applyRoleTo(MethodVisitor methodVisitor) {
            // GENERATE getFactory().applyRole(<value>)
            // GENERATE factory = getFactory()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), FACTORY_METHOD, RETURN_MANAGED_OBJECT_FACTORY, false);
            methodVisitor.visitInsn(SWAP);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, MANAGED_OBJECT_FACTORY_TYPE.getInternalName(), "applyRole", RETURN_VOID_FROM_OBJECT_MODEL_OBJECT, false);
        }

        @Override
        public void applyManagedStateToGetter(PropertyMetadata property, Method getter) {
            // GENERATE public <type> <getter>() { return <field> }
            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            String fieldName = propFieldName(property);
            addGetter(getter.getName(), returnType, methodDescriptor, null, methodVisitor -> {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), fieldName, returnType.getDescriptor());
            });
        }

        @Override
        public void applyManagedStateToSetter(PropertyMetadata property, Method setter) {
            addSetterForProperty(property, setter);
        }

        private void addSetterForProperty(PropertyMetadata property, Method setter) {
            // GENERATE public void <setter>(<type> value) { <field> == value }
            String methodDescriptor = Type.getMethodDescriptor(setter);
            Type fieldType = Type.getType(property.getType());
            String propFieldName = propFieldName(property);

            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, setter.getName(), methodDescriptor, signature(setter), EMPTY_STRINGS);
            methodVisitor.visitCode();

            // this.field = value
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(fieldType.getOpcode(ILOAD), 1);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName, fieldType.getDescriptor());

            // return
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generateGeneratedSubtypeMethods() {
            // Generate: Class publicType() { ... }
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "publicType", RETURN_CLASS, null, EMPTY_STRINGS);
            methodVisitor.visitLdcInsn(superclassType);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: static Class generatedFrom() { ... }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC | ACC_STATIC, "generatedFrom", RETURN_CLASS, null, EMPTY_STRINGS);
            methodVisitor.visitLdcInsn(superclassType);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generateModelObjectMethods() {
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC, DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE.getDescriptor(), null, null);
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC, OWNER_FIELD, MODEL_OBJECT_TYPE.getDescriptor(), null, null);

            // GENERATE boolean hasUsefulDisplayName() { ... }
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "hasUsefulDisplayName", RETURN_BOOLEAN, null, EMPTY_STRINGS);
            if (requiresToString) {
                // Type has a generated toString() implementation
                // Generate: return displayName != null
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE.getDescriptor());
                Label label = new Label();
                methodVisitor.visitJumpInsn(IFNULL, label);
                methodVisitor.visitLdcInsn(true);
                methodVisitor.visitInsn(BOOLEAN_TYPE.getOpcode(IRETURN));
                methodVisitor.visitLabel(label);
                methodVisitor.visitLdcInsn(false);
                methodVisitor.visitInsn(BOOLEAN_TYPE.getOpcode(IRETURN));
            } else {
                // Type has its own toString implementation
                // Generate: return true
                methodVisitor.visitLdcInsn(true);
                methodVisitor.visitInsn(BOOLEAN_TYPE.getOpcode(IRETURN));
            }
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // GENERATE getModelIdentityDisplayName() { return displayName }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "getModelIdentityDisplayName", RETURN_DESCRIBABLE, null, EMPTY_STRINGS);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE.getDescriptor());
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // GENERATE getTaskThatOwnsThisObject() { ... }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "getTaskThatOwnsThisObject", Type.getMethodDescriptor(Type.getType(Task.class)), null, EMPTY_STRINGS);
            if (Task.class.isAssignableFrom(type)) {
                // return this
                methodVisitor.visitVarInsn(ALOAD, 0);
            } else {
                // if (owner != null) { return owner.getTaskThatOwnsThisObject() } else { return null }
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), OWNER_FIELD, MODEL_OBJECT_TYPE.getDescriptor());
                methodVisitor.visitInsn(DUP);
                Label useNull = new Label();
                methodVisitor.visitJumpInsn(IFNULL, useNull);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, MODEL_OBJECT_TYPE.getInternalName(), "getTaskThatOwnsThisObject", Type.getMethodDescriptor(Type.getType(Task.class)), true);
                methodVisitor.visitLabel(useNull);
            }
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // GENERATE attachOwner(owner, displayName) { this.displayName = displayName }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "attachOwner", RETURN_VOID_FROM_MODEL_OBJECT_DISPLAY_NAME, null, EMPTY_STRINGS);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), OWNER_FIELD, MODEL_OBJECT_TYPE.getDescriptor());
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(ALOAD, 2);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE.getDescriptor());
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addManagedMethods(Iterable<PropertyMetadata> mutableProperties, Iterable<PropertyMetadata> readOnlyProperties) {
            visitor.visitField(ACC_PRIVATE | ACC_STATIC, FACTORY_ID_FIELD, Type.INT_TYPE.getDescriptor(), null, null);

            // Generate: <init>(Object[] state) { }
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "<init>", Type.getMethodDescriptor(VOID_TYPE, OBJECT_ARRAY_TYPE), null, EMPTY_STRINGS);
            methodVisitor.visitVarInsn(ALOAD, 0);
            if (type.isInterface()) {
                methodVisitor.visitMethodInsn(INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "<init>", RETURN_VOID, false);
            } else {
                methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), "<init>", RETURN_VOID, false);
            }
            int propertyIndex = 0;
            for (PropertyMetadata propertyMetaData : mutableProperties) {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitLdcInsn(propertyIndex);
                methodVisitor.visitInsn(AALOAD);
                unboxOrCast(methodVisitor, Type.getType(propertyMetaData.getType()));
                String propFieldName = propFieldName(propertyMetaData);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName, Type.getType(propertyMetaData.getType()).getDescriptor());
                propertyIndex++;
            }
            int mutablePropertySize = propertyIndex;
            propertyIndex = 0;
            for (PropertyMetadata propertyMetaData : readOnlyProperties) {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitLdcInsn(propertyIndex + mutablePropertySize);
                methodVisitor.visitInsn(AALOAD);
                unboxOrCast(methodVisitor, Type.getType(propertyMetaData.getType()));
                String propFieldName = propFieldName(propertyMetaData);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName, Type.getType(propertyMetaData.getType()).getDescriptor());
                propertyIndex++;
            }
            int readOnlyPropertySize = propertyIndex;
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: Class immutable() { return <properties.empty> && <read-only-properties.empty> }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "isImmutable", RETURN_BOOLEAN, null, EMPTY_STRINGS);
            // Could return true if all of the read only properties point to immutable objects, but at this stage there are no such types supported
            methodVisitor.visitLdcInsn(mutablePropertySize == 0 && readOnlyPropertySize == 0);
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: Object[] unpackState() { state = new Object[<size>]; state[x] = <prop-field>; return state; }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "unpackState", RETURN_OBJECT, null, EMPTY_STRINGS);
            methodVisitor.visitLdcInsn(mutablePropertySize + readOnlyPropertySize);
            methodVisitor.visitTypeInsn(ANEWARRAY, OBJECT_TYPE.getInternalName());
            // TODO - property order needs to be deterministic across JVM invocations, i.e. sort the properties by name
            propertyIndex = 0;
            for (PropertyMetadata property : mutableProperties) {
                String propFieldName = propFieldName(property);
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitLdcInsn(propertyIndex);
                methodVisitor.visitVarInsn(ALOAD, 0);
                Type propertyType = Type.getType(property.getType());
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), propFieldName, propertyType.getDescriptor());
                maybeBox(methodVisitor, property.getType(), propertyType);
                methodVisitor.visitInsn(AASTORE);
                propertyIndex++;
            }
            propertyIndex = 0;
            for (PropertyMetadata property : readOnlyProperties) {
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitLdcInsn(propertyIndex + mutablePropertySize);
                methodVisitor.visitVarInsn(ALOAD, 0);
                MethodMetadata getter = property.getMainGetter();
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), getter.getName(), Type.getMethodDescriptor(Type.getType(getter.getReturnType())), false);
                maybeBox(methodVisitor, property.getType(), Type.getType(property.getType()));
                methodVisitor.visitInsn(AASTORE);
                propertyIndex++;
            }
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: int getFactoryId() { return <factory-id-field> }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "getFactoryId", Type.getMethodDescriptor(Type.INT_TYPE), null, EMPTY_STRINGS);
            methodVisitor.visitFieldInsn(GETSTATIC, generatedType.getInternalName(), FACTORY_ID_FIELD, Type.INT_TYPE.getDescriptor());
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void applyConventionMappingToProperty(PropertyMetadata property) {
            if (!conventionAware) {
                return;
            }

            // GENERATE private boolean <flag-name>;
            String flagName = propFieldName(property);
            visitor.visitField(ACC_PRIVATE | ACC_TRANSIENT, flagName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);
        }

        @Override
        public void applyConventionMappingToGetter(PropertyMetadata property, MethodMetadata getter, boolean attachOwner, boolean applyRole) {
            if (!conventionAware && !attachOwner) {
                return;
            }

            String getterName = getter.getName();
            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, getterName, methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            if (conventionAware) {
                // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__); }
                Label finish = new Label();

                if (hasMappingField) {
                    // if (conventionMapping == null) { return super.<getter>; }
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR);
                    Label useConvention = new Label();
                    methodVisitor.visitJumpInsn(IFNONNULL, useConvention);
                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, type.isInterface());
                    methodVisitor.visitJumpInsn(GOTO, finish);
                    methodVisitor.visitLabel(useConvention);
                }
                // else { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__);  }
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKEINTERFACE, CONVENTION_AWARE_TYPE.getInternalName(), "getConventionMapping", Type.getMethodDescriptor(CONVENTION_MAPPING_TYPE), true);

                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, type.isInterface());

                maybeBox(methodVisitor, getter.getReturnType(), returnType);

                methodVisitor.visitLdcInsn(property.getName());

                String flagName = propFieldName(property);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), flagName,
                    Type.BOOLEAN_TYPE.getDescriptor());

                methodVisitor.visitMethodInsn(INVOKEINTERFACE, CONVENTION_MAPPING_TYPE.getInternalName(), "getConventionValue", RETURN_OBJECT_FROM_STRING_OBJECT_BOOLEAN, true);

                unboxOrCast(methodVisitor, returnType);

                methodVisitor.visitLabel(finish);
            } else {
                // GENERATE super.<getter>()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, type.isInterface());
            }

            if (attachOwner) {
                // GENERATE ManagedObjectFactory.attachOwner(<value>, this, <property-name>)
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitLdcInsn(property.getName());
                methodVisitor.visitMethodInsn(INVOKESTATIC, MANAGED_OBJECT_FACTORY_TYPE.getInternalName(), "attachOwner", RETURN_OBJECT_FROM_OBJECT_MODEL_OBJECT_STRING, false);
                methodVisitor.visitInsn(POP);
                if (applyRole) {
                    // GENERATE ManagedObjectFactory.applyRole(<value>)
                    methodVisitor.visitInsn(DUP);
                    applyRoleTo(methodVisitor);
                }
            }

            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Boxes the value at the top of the stack, if primitive
         */
        private void maybeBox(MethodVisitor methodVisitor, Class<?> valueClass, Type valueType) {
            if (valueClass.isPrimitive()) {
                // Box value
                Type boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueClass));
                String valueOfMethodDescriptor = Type.getMethodDescriptor(boxedType, valueType);
                methodVisitor.visitMethodInsn(INVOKESTATIC, boxedType.getInternalName(), "valueOf", valueOfMethodDescriptor, false);
            }
        }

        @Override
        public void applyConventionMappingToSetter(PropertyMetadata property, Method setter) {
            if (!conventionAware) {
                return;
            }

            // GENERATE public <return-type> <setter>(<type> v) { <return-type> v = super.<setter>(v); __<prop>__ = true; return v; }

            Type paramType = Type.getType(setter.getParameterTypes()[0]);
            Type returnType = Type.getType(setter.getReturnType());
            String setterDescriptor = Type.getMethodDescriptor(returnType, paramType);
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, setter.getName(), setterDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE super.<setter>(v)

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(ILOAD), 1);

            methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), setter.getName(), setterDescriptor, false);

            // END

            // GENERATE __<prop>__ = true

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitLdcInsn(true);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName(property), Type.BOOLEAN_TYPE.getDescriptor());

            // END

            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addSetMethod(PropertyMetadata property, Method setter) {
            if (!mixInDsl) {
                return;
            }

            Type paramType = Type.getType(setter.getParameterTypes()[0]);
            Type returnType = Type.getType(setter.getReturnType());
            String setterDescriptor = Type.getMethodDescriptor(returnType, paramType);

            // GENERATE public void <propName>(<type> v) { <setter>(v) }
            String setMethodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, paramType);
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, property.getName(), setMethodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE <setter>(v)

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(ILOAD), 1);

            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), setter.getName(), setterDescriptor, false);

            // END

            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void applyConventionMappingToSetMethod(PropertyMetadata property, Method method) {
            if (!mixInDsl || !conventionAware) {
                return;
            }

            Type paramType = Type.getType(method.getParameterTypes()[0]);
            Type returnType = Type.getType(method.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType, paramType);

            // GENERATE public <returnType> <propName>(<type> v) { val = super.<propName>(v); __<prop>__ = true; return val; }
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, method.getName(), methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE super.<propName>(v)

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(ILOAD), 1);

            methodVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), method.getName(), methodDescriptor, false);

            // GENERATE __<prop>__ = true

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitLdcInsn(true);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName(property), Type.BOOLEAN_TYPE.getDescriptor());

            // END

            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addActionMethod(Method method) {
            if (!mixInDsl) {
                return;
            }

            Type returnType = Type.getType(method.getReturnType());

            Type[] originalParameterTypes = CollectionUtils.collectArray(method.getParameterTypes(), Type.class, (Transformer<Type, Class>) Type::getType);
            int numParams = originalParameterTypes.length;
            Type[] closurisedParameterTypes = new Type[numParams];
            System.arraycopy(originalParameterTypes, 0, closurisedParameterTypes, 0, numParams);
            closurisedParameterTypes[numParams - 1] = CLOSURE_TYPE;

            String methodDescriptor = Type.getMethodDescriptor(returnType, closurisedParameterTypes);

            // GENERATE public <return type> <method>(Closure v) { return <method>(…, ConfigureUtil.configureUsing(v)); }
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, method.getName(), methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE <method>(…, ConfigureUtil.configureUsing(v));
            methodVisitor.visitVarInsn(ALOAD, 0);

            int stackVar = 1;
            for (int typeVar = 0; typeVar < numParams - 1; ++typeVar) {
                Type argType = closurisedParameterTypes[typeVar];
                methodVisitor.visitVarInsn(argType.getOpcode(ILOAD), stackVar);
                stackVar += argType.getSize();
            }

            // GENERATE ConfigureUtil.configureUsing(v);
            methodVisitor.visitVarInsn(ALOAD, stackVar);
            methodDescriptor = Type.getMethodDescriptor(ACTION_TYPE, CLOSURE_TYPE);
            methodVisitor.visitMethodInsn(INVOKESTATIC, CONFIGURE_UTIL_TYPE.getInternalName(), "configureUsing", methodDescriptor, false);

            methodDescriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), originalParameterTypes);
            methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), method.getName(), methodDescriptor, false);

            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
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
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "toString", RETURN_STRING, null, null);
            methodVisitor.visitCode();

            // Generate: if (displayName != null) { return displayName.getDisplayName() }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), DISPLAY_NAME_FIELD, DESCRIBABLE_TYPE.getDescriptor());
            methodVisitor.visitInsn(DUP);
            Label label1 = new Label();
            methodVisitor.visitJumpInsn(IFNULL, label1);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, DESCRIBABLE_TYPE.getInternalName(), "getDisplayName", RETURN_STRING, true);
            methodVisitor.visitInsn(ARETURN);

            // Generate: if (...) { return ... }
            methodVisitor.visitLabel(label1);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), GET_DISPLAY_NAME_FOR_NEXT_METHOD_NAME, RETURN_DESCRIBABLE, false);
            methodVisitor.visitInsn(DUP);
            Label label2 = new Label();
            methodVisitor.visitJumpInsn(IFNULL, label2);
            methodVisitor.visitMethodInsn(INVOKEINTERFACE, DESCRIBABLE_TYPE.getInternalName(), "getDisplayName", RETURN_STRING, true);
            methodVisitor.visitInsn(ARETURN);

            // Generate: return super.toString()
            methodVisitor.visitLabel(label2);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "toString", RETURN_STRING, false);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generateServiceRegistrySupport() {
            generateServicesField();
            generateGetServices();
        }

        private void generateManagedPropertyCreationSupport() {
            generateManagedObjectFactoryField();
            generateGetManagedObjectFactory();
        }

        private void generateManagedObjectFactoryField() {
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC | ACC_TRANSIENT, FACTORY_FIELD, MANAGED_OBJECT_FACTORY_TYPE.getDescriptor(), null, null);
        }

        private void generateGetManagedObjectFactory() {
            MethodVisitor mv = visitor.visitMethod(ACC_PRIVATE | ACC_SYNTHETIC, FACTORY_METHOD, RETURN_MANAGED_OBJECT_FACTORY, null, null);
            mv.visitCode();
            // GENERATE if (factory != null) { return factory; } else { return AsmBackedClassGenerator.getFactoryForNext(); }
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, generatedType.getInternalName(), FACTORY_FIELD, MANAGED_OBJECT_FACTORY_TYPE.getDescriptor());
            mv.visitInsn(DUP);
            Label label = new Label();
            mv.visitJumpInsn(IFNULL, label);
            mv.visitInsn(ARETURN);
            mv.visitLabel(label);
            mv.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), GET_FACTORY_FOR_NEXT_METHOD_NAME, RETURN_MANAGED_OBJECT_FACTORY, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private void includeNotInheritedAnnotations() {
            for (Annotation annotation : type.getDeclaredAnnotations()) {
                if (annotation.annotationType().getAnnotation(Inherited.class) != null) {
                    continue;
                }
                Retention retention = annotation.annotationType().getAnnotation(Retention.class);
                boolean visible = retention != null && retention.value() == RetentionPolicy.RUNTIME;
                AnnotationVisitor annotationVisitor = visitor.visitAnnotation(Type.getType(annotation.annotationType()).getDescriptor(), visible);
                visitAnnotationValues(annotation, annotationVisitor);
                annotationVisitor.visitEnd();
            }
        }

        private void visitAnnotationValues(Annotation annotation, AnnotationVisitor annotationVisitor) {
            for (Method method : annotation.annotationType().getDeclaredMethods()) {
                String name = method.getName();
                Class<?> returnType = method.getReturnType();
                if (returnType.isEnum()) {
                    annotationVisitor.visitEnum(name, Type.getType(returnType).getDescriptor(), getAnnotationParameterValue(annotation, method).toString());
                } else if (returnType.isArray() && !PRIMITIVE_TYPES.contains(returnType.getComponentType())) {
                    AnnotationVisitor arrayVisitor = annotationVisitor.visitArray(name);
                    Object[] elements = (Object[]) getAnnotationParameterValue(annotation, method);
                    visitArrayElements(arrayVisitor, returnType.getComponentType(), elements);
                    arrayVisitor.visitEnd();
                } else if (returnType.equals(Class.class)) {
                    Class<?> clazz = (Class<?>) getAnnotationParameterValue(annotation, method);
                    annotationVisitor.visit(name, Type.getType(clazz));
                } else if (returnType.isAnnotation()) {
                    Annotation nestedAnnotation = (Annotation) getAnnotationParameterValue(annotation, method);
                    AnnotationVisitor nestedAnnotationVisitor = annotationVisitor.visitAnnotation(name, Type.getType(returnType).getDescriptor());
                    visitAnnotationValues(nestedAnnotation, nestedAnnotationVisitor);
                    nestedAnnotationVisitor.visitEnd();
                } else {
                    annotationVisitor.visit(name, getAnnotationParameterValue(annotation, method));
                }
            }
        }

        private void visitArrayElements(AnnotationVisitor arrayVisitor, Class arrayElementType, Object[] arrayElements) {
            if (arrayElementType.isEnum()) {
                String enumDescriptor = Type.getType(arrayElementType).getDescriptor();
                for (Object value : arrayElements) {
                    arrayVisitor.visitEnum(null, enumDescriptor, value.toString());
                }
            } else if (arrayElementType.equals(Class.class)) {
                for (Object value : arrayElements) {
                    Class<?> clazz = (Class<?>) value;
                    arrayVisitor.visit(null, Type.getType(clazz));
                }
            } else if (arrayElementType.isAnnotation()) {
                for (Object annotation : arrayElements) {
                    AnnotationVisitor nestedAnnotationVisitor = arrayVisitor.visitAnnotation(null, Type.getType(arrayElementType).getDescriptor());
                    visitAnnotationValues((Annotation) annotation, nestedAnnotationVisitor);
                    nestedAnnotationVisitor.visitEnd();
                }
            } else {
                for (Object value : arrayElements) {
                    arrayVisitor.visit(null, value);
                }
            }
        }

        private Object getAnnotationParameterValue(Annotation annotation, Method method) {
            try {
                return method.invoke(annotation);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private void attachFactoryIdToImplType(Class<?> implClass, int id) {
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
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, NAME_FIELD, STRING_TYPE.getDescriptor(), null, null);
            addGetter("getName", STRING_TYPE, Type.getMethodDescriptor(STRING_TYPE), null, methodVisitor -> {
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), NAME_FIELD, STRING_TYPE.getDescriptor());
            });
        }

        @Override
        public Class<?> generate() {
            writeGenericReturnTypeFields();
            visitor.visitEnd();

            Class<?> generatedClass = classGenerator.define();

            if (managed) {
                attachFactoryIdToImplType(generatedClass, factoryId);
            }

            return generatedClass;
        }

        private void writeGenericReturnTypeFields() {
            if (!genericReturnTypeConstantsIndex.isEmpty()) {
                MethodVisitor mv = visitor.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                mv.visitCode();

                for (Map.Entry<java.lang.reflect.Type, ReturnTypeEntry> entry : genericReturnTypeConstantsIndex.entrySet()) {
                    ReturnTypeEntry returnType = entry.getValue();
                    visitor.visitField(PV_FINAL_STATIC, returnType.fieldName, JAVA_REFLECT_TYPE_DESCRIPTOR, null, null);
                    writeGenericReturnTypeFieldInitializer(mv, returnType);
                }

                mv.visitInsn(RETURN);
                mv.visitMaxs(1, 1);
                mv.visitEnd();
            }
        }

        private void writeGenericReturnTypeFieldInitializer(MethodVisitor mv, ReturnTypeEntry returnType) {
            mv.visitLdcInsn(generatedType);

            // <class>.getDeclaredMethod(<getter-name>)
            mv.visitLdcInsn(returnType.getterName);
            mv.visitInsn(ICONST_0);
            mv.visitTypeInsn(ANEWARRAY, CLASS_TYPE.getInternalName());
            mv.visitMethodInsn(INVOKEVIRTUAL, CLASS_TYPE.getInternalName(), "getDeclaredMethod", GET_DECLARED_METHOD_DESCRIPTOR, false);
            // <method>.getGenericReturnType()
            mv.visitMethodInsn(INVOKEVIRTUAL, METHOD_TYPE.getInternalName(), "getGenericReturnType", Type.getMethodDescriptor(JAVA_LANG_REFLECT_TYPE), false);
            mv.visitFieldInsn(PUTSTATIC, generatedType.getInternalName(), returnType.fieldName, JAVA_REFLECT_TYPE_DESCRIPTOR);

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

    private interface MethodCodeBody {
        void add(MethodVisitor visitor);
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
        public void addManagedMethods(Iterable<PropertyMetadata> mutableProperties, Iterable<PropertyMetadata> readOnlyProperties) {
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
        public void applyConventionMappingToSetMethod(PropertyMetadata property, Method metaMethod) {
        }

        @Override
        public void addSetMethod(PropertyMetadata propertyMetaData, Method setter) {
        }

        @Override
        public void addActionMethod(Method method) {
        }

        @Override
        public void addPropertySetterOverloads(PropertyMetadata property, MethodMetadata getter) {
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
