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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaProperty;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GeneratedSubclass;
import org.gradle.api.internal.HasConvention;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.extensibility.ConventionAwareHelper;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.state.Managed;
import org.gradle.model.internal.asm.AsmClassGenerator;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.getterSignature;
import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.signature;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;
import static org.objectweb.asm.Type.VOID_TYPE;

public class AsmBackedClassGenerator extends AbstractClassGenerator {
    private static final ThreadLocal<ObjectCreationDetails> SERVICES_FOR_NEXT_OBJECT = new ThreadLocal<ObjectCreationDetails>();
    private final boolean decorate;
    private final String suffix;
    private final Integer key;

    // Used by generated code
    @SuppressWarnings("unused")
    public static ServiceLookup getServicesForNext() {
        return SERVICES_FOR_NEXT_OBJECT.get().services;
    }

    private AsmBackedClassGenerator(boolean decorate, String suffix, Collection<? extends InjectAnnotationHandler> allKnownAnnotations, Collection<Class<? extends Annotation>> enabledAnnotations) {
        super(allKnownAnnotations, enabledAnnotations);
        this.decorate = decorate;
        this.suffix = suffix;
        // TODO - this isn't correct, fix this. It's just enough to get the tests to pass
        this.key = enabledAnnotations.size() << 1 | (decorate ? 1 : 0);
    }

    /**
     * Returns a generator that applies DSL mix-in, extensibility and service injection for generated classes.
     */
    static ClassGenerator decorateAndInject(Collection<? extends InjectAnnotationHandler> allKnownAnnotations, Collection<Class<? extends Annotation>> enabledAnnotations) {
        // TODO wolfs: We use `_Decorated` here, since IDEA import currently relies on this
        // See https://github.com/gradle/gradle/issues/8244
        return new AsmBackedClassGenerator(true, "_Decorated", allKnownAnnotations, enabledAnnotations);
    }

    /**
     * Returns a generator that applies service injection only for generated classes, and will generate classes only if required.
     */
    static ClassGenerator injectOnly(Collection<? extends InjectAnnotationHandler> allKnownAnnotations, Collection<Class<? extends Annotation>> enabledAnnotations) {
        return new AsmBackedClassGenerator(false, "$Inject", allKnownAnnotations, enabledAnnotations);
    }

    @Override
    protected Object key() {
        return key;
    }

    @Override
    protected <T> T newInstance(Constructor<T> constructor, ServiceLookup services, Instantiator nested, Object[] params) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        ObjectCreationDetails previous = SERVICES_FOR_NEXT_OBJECT.get();

        SERVICES_FOR_NEXT_OBJECT.set(new ObjectCreationDetails(nested, services));
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(params);
        } finally {
            SERVICES_FOR_NEXT_OBJECT.set(previous);
        }
    }

    @Override
    protected ClassInspectionVisitor start(Class<?> type) {
        if (type.isAnnotation() || type.isEnum()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is not a class or interface.");
            throw new ClassGenerationException(formatter.toString());
        }
        return new ClassInspectionVisitorImpl(type, decorate, suffix);
    }

    private static class ClassInspectionVisitorImpl implements ClassInspectionVisitor {
        private final Class<?> type;
        private final boolean decorate;
        private final String suffix;
        private boolean extensible;
        private boolean serviceInjection;
        private boolean conventionAware;
        private boolean managed;
        private boolean providesOwnDynamicObjectImplementation;
        private boolean providesOwnServicesImplementation;

        public ClassInspectionVisitorImpl(Class<?> type, boolean decorate, String suffix) {
            this.type = type;
            this.decorate = decorate;
            this.suffix = suffix;
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
        public void mixInManaged() {
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
            ClassBuilderImpl builder = new ClassBuilderImpl(type, decorate, suffix, extensible, conventionAware, managed, providesOwnDynamicObjectImplementation, requiresServicesMethod);
            builder.startClass();
            return builder;
        }
    }

    private static class ClassBuilderImpl implements ClassGenerationVisitor {
        public static final int PV_FINAL_STATIC = Opcodes.ACC_PRIVATE | ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
        private static final Set<? extends Class<?>> PRIMITIVE_TYPES = ImmutableSet.of(Byte.TYPE, Boolean.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE);
        private static final String DYNAMIC_OBJECT_HELPER_FIELD = "_gr_dyn_";
        private static final String MAPPING_FIELD = "_gr_map_";
        private static final String META_CLASS_FIELD = "_gr_mc_";
        private static final String SERVICES_FIELD = "_gr_svcs_";
        private static final String SERVICES_METHOD = "$gradleServices";
        private static final String FACTORY_FIELD = "_gr_factory_";
        private static final String CONVENTION_MAPPING_FIELD_DESCRIPTOR = Type.getDescriptor(ConventionMapping.class);
        private static final String META_CLASS_TYPE_DESCRIPTOR = Type.getDescriptor(MetaClass.class);
        private final static Type META_CLASS_TYPE = Type.getType(MetaClass.class);
        private final static Type GENERATED_SUBCLASS_TYPE = Type.getType(GeneratedSubclass.class);
        private final static Type CONVENTION_AWARE_TYPE = Type.getType(IConventionAware.class);
        private final static Type CONVENTION_AWARE_HELPER_TYPE = Type.getType(ConventionAwareHelper.class);
        private final static Type DYNAMIC_OBJECT_AWARE_TYPE = Type.getType(DynamicObjectAware.class);
        private final static Type EXTENSION_AWARE_TYPE = Type.getType(ExtensionAware.class);
        private final static Type HAS_CONVENTION_TYPE = Type.getType(HasConvention.class);
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
        private static final Type INSTANTIATOR_TYPE = Type.getType(Instantiator.class);
        private static final Type INSTANTIATOR_FACTORY_TYPE = Type.getType(InstantiatorFactory.class);
        private static final Type OBJECT_FACTORY_TYPE = Type.getType(ObjectFactory.class);
        private static final Type CONFIGURABLE_FILE_COLLECTION_TYPE = Type.getType(ConfigurableFileCollection.class);
        private static final Type MANAGED_TYPE = Type.getType(Managed.class);
        private static final Type REGULAR_FILE_PROPERTY_TYPE = Type.getType(RegularFileProperty.class);
        private static final Type DIRECTORY_PROPERTY_TYPE = Type.getType(DirectoryProperty.class);
        private static final Type PROPERTY_TYPE = Type.getType(Property.class);
        private static final Type LIST_PROPERTY_TYPE = Type.getType(ListProperty.class);
        private static final Type SET_PROPERTY_TYPE = Type.getType(SetProperty.class);
        private static final Type MAP_PROPERTY = Type.getType(MapProperty.class);
        private static final Type EXTENSION_CONTAINER_TYPE = Type.getType(ExtensionContainer.class);

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
        private static final String RETURN_META_CLASS = Type.getMethodDescriptor(META_CLASS_TYPE);
        private static final String RETURN_VOID_FROM_META_CLASS = Type.getMethodDescriptor(Type.VOID_TYPE, META_CLASS_TYPE);
        private static final String GET_DECLARED_METHOD_DESCRIPTOR = Type.getMethodDescriptor(METHOD_TYPE, STRING_TYPE, CLASS_ARRAY_TYPE);
        private static final String RETURN_OBJECT_FROM_TYPE = Type.getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE);
        private static final String RETURN_CONFIGURABLE_FILE_COLLECTION = Type.getMethodDescriptor(CONFIGURABLE_FILE_COLLECTION_TYPE);
        private static final String RETURN_REGULAR_FILE_PROPERTY = Type.getMethodDescriptor(REGULAR_FILE_PROPERTY_TYPE);
        private static final String RETURN_DIRECTORY_PROPERTY = Type.getMethodDescriptor(DIRECTORY_PROPERTY_TYPE);
        private static final String RETURN_PROPERTY_FROM_CLASS = Type.getMethodDescriptor(PROPERTY_TYPE, CLASS_TYPE);
        private static final String RETURN_LIST_PROPERTY_FROM_CLASS = Type.getMethodDescriptor(LIST_PROPERTY_TYPE, CLASS_TYPE);
        private static final String RETURN_SET_PROPERTY_FROM_CLASS = Type.getMethodDescriptor(SET_PROPERTY_TYPE, CLASS_TYPE);
        private static final String RETURN_MAP_PROPERTY_FROM_CLASS_CLASS = Type.getMethodDescriptor(MAP_PROPERTY, CLASS_TYPE, CLASS_TYPE);

        private static final String[] EMPTY_STRINGS = new String[0];
        private static final Type[] EMPTY_TYPES = new Type[0];

        private final ClassWriter visitor;
        private final Class<?> type;
        private final boolean managed;
        private final Type generatedType;
        private final Type superclassType;
        private final Map<java.lang.reflect.Type, ReturnTypeEntry> genericReturnTypeConstantsIndex = Maps.newHashMap();
        private final AsmClassGenerator classGenerator;
        private boolean hasMappingField;
        private final boolean conventionAware;
        private final boolean mixInDsl;
        private final boolean extensible;
        private final boolean providesOwnDynamicObject;
        private final boolean requiresServicesMethod;

        private ClassBuilderImpl(Class<?> type, boolean decorated, String suffix, boolean extensible, boolean conventionAware, boolean managed, boolean providesOwnDynamicObject, boolean requiresServicesMethod) {
            this.type = type;
            this.managed = managed;
            classGenerator = new AsmClassGenerator(type, suffix);
            visitor = classGenerator.getVisitor();
            generatedType = classGenerator.getGeneratedType();
            superclassType = Type.getType(type);
            mixInDsl = decorated;
            this.extensible = extensible;
            this.conventionAware = conventionAware;
            this.providesOwnDynamicObject = providesOwnDynamicObject;
            this.requiresServicesMethod = requiresServicesMethod;
        }

        public void startClass() {
            List<String> interfaceTypes = new ArrayList<String>();

            Type superclass = superclassType;
            if (type.isInterface()) {
                interfaceTypes.add(superclassType.getInternalName());
                superclass = OBJECT_TYPE;
            }

            interfaceTypes.add(GENERATED_SUBCLASS_TYPE.getInternalName());

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

            if (requiresServicesMethod) {
                generateServiceRegistrySupportMethods();
            }
            generatePublicTypeMethod();
        }

        @Override
        public void addDefaultConstructor() {
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", RETURN_VOID, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            // this.super()
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "<init>", RETURN_VOID, false);

            initializeFields(methodVisitor);

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addConstructor(Constructor<?> constructor) {
            List<Type> paramTypes = new ArrayList<Type>();
            for (Class<?> paramType : constructor.getParameterTypes()) {
                paramTypes.add(Type.getType(paramType));
            }
            String methodDescriptor = Type.getMethodDescriptor(VOID_TYPE, paramTypes.toArray(EMPTY_TYPES));

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", methodDescriptor, signature(constructor), EMPTY_STRINGS);

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
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                methodVisitor.visitVarInsn(Type.getType(constructor.getParameterTypes()[i]).getOpcode(Opcodes.ILOAD), i + 1);
            }
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>", methodDescriptor, false);

            initializeFields(methodVisitor);

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void initializeFields(MethodVisitor methodVisitor) {
            if (requiresServicesMethod) {
                // this.services = AsmBackedClassGenerator.getServicesForNext()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), "getServicesForNext", RETURN_SERVICE_LOOKUP, false);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), SERVICES_FIELD, SERVICE_LOOKUP_TYPE.getDescriptor());
            }
        }

        @Override
        public void addExtensionsProperty() {
            // GENERATE public ExtensionContainer getExtensions() { return getConvention(); }

            addGetter("getExtensions", EXTENSION_CONTAINER_TYPE, RETURN_EXTENSION_CONTAINER, null, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) {

                    // GENERATE getConvention()

                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false);
                }
            });
        }

        @Override
        public void mixInDynamicAware() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE private DynamicObject dynamicObjectHelper
            visitor.visitField(Opcodes.ACC_PRIVATE, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE.getDescriptor(), null, null);

            // END

            if (extensible) {

                // GENERATE public Convention getConvention() { return getAsDynamicObject().getConvention(); }

                addGetter("getConvention", CONVENTION_TYPE, RETURN_CONVENTION, null, new MethodCodeBody() {
                    @Override
                    public void add(MethodVisitor visitor) {

                        // GENERATE ((MixInExtensibleDynamicObject)getAsDynamicObject()).getConvention()

                        visitor.visitVarInsn(ALOAD, 0);
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);
                        visitor.visitTypeInsn(Opcodes.CHECKCAST, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "getConvention", RETURN_CONVENTION, false);
                    }
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

            addLazyGetter("getAsDynamicObject", DYNAMIC_OBJECT_TYPE, RETURN_DYNAMIC_OBJECT, null, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) {
                    generateCreateDynamicObject(visitor);
                }
            });

            // END
        }

        private void generateCreateDynamicObject(MethodVisitor visitor) {
            if (extensible) {

                // GENERATE new MixInExtensibleDynamicObject(this, getClass().getSuperClass(), super.getAsDynamicObject(), this.services())

                visitor.visitTypeInsn(Opcodes.NEW, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitVarInsn(ALOAD, 0);
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getClass", RETURN_CLASS, false);
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLASS_TYPE.getInternalName(), "getSuperclass", RETURN_CLASS, false);

                if (providesOwnDynamicObject) {
                    // GENERATE super.getAsDynamicObject()
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getType(type).getInternalName(),
                            "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);
                } else {
                    // GENERATE null
                    visitor.visitInsn(Opcodes.ACONST_NULL);
                }

                // GENERATE this.services()
                putServiceRegistryOnStack(visitor);

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT_SERVICE_LOOKUP, false);
                // END
            } else {

                // GENERATE new BeanDynamicObject(this)

                visitor.visitTypeInsn(Opcodes.NEW, NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(ALOAD, 0);

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_OBJECT, false);
                // END
            }
        }

        @Override
        public void mixInConventionAware() {
            // GENERATE private ConventionMapping mapping

            visitor.visitField(Opcodes.ACC_PRIVATE, MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR, null, null);
            hasMappingField = true;

            // END

            // GENERATE public ConventionMapping getConventionMapping() {
            //     if (mapping == null) {
            //         mapping = new ConventionAwareHelper(this, getConvention());
            //     }
            //     return mapping;
            // }

            final MethodCodeBody initConventionAwareHelper = new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) {
                    // GENERATE new ConventionAwareHelper(this, getConvention())

                    visitor.visitTypeInsn(Opcodes.NEW, CONVENTION_AWARE_HELPER_TYPE.getInternalName());
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitVarInsn(ALOAD, 0);

                    // GENERATE getConvention()

                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false);

                    // END

                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, CONVENTION_AWARE_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION, false);

                    // END
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

            visitor.visitField(Opcodes.ACC_PRIVATE, META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR, null, null);

            // GENERATE public MetaClass getMetaClass() {
            //     if (metaClass == null) {
            //         metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
            //     }
            //     return metaClass;
            // }

            final MethodCodeBody initMetaClass = new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) {
                    // GroovySystem.getMetaClassRegistry()
                    visitor.visitMethodInsn(Opcodes.INVOKESTATIC, GROOVY_SYSTEM_TYPE.getInternalName(), "getMetaClassRegistry", RETURN_META_CLASS_REGISTRY, false);

                    // this.getClass()
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OBJECT_TYPE.getInternalName(), "getClass", RETURN_CLASS, false);

                    // getMetaClass(..)
                    visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, META_CLASS_REGISTRY_TYPE.getInternalName(), "getMetaClass", RETURN_META_CLASS_FROM_CLASS, true);
                }
            };

            addLazyGetter("getMetaClass", META_CLASS_TYPE, RETURN_META_CLASS, null, META_CLASS_FIELD, META_CLASS_TYPE, initMetaClass);

            // END

            // GENERATE public void setMetaClass(MetaClass class) { this.metaClass = class; }

            addSetter("setMetaClass", RETURN_VOID_FROM_META_CLASS, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) {
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitVarInsn(ALOAD, 1);
                    visitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR);
                }
            });
        }

        private void addSetter(String methodName, String methodDescriptor, MethodCodeBody body) {
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addPropertySetters(PropertyMetadata property, Method getter) {
            if (!mixInDsl) {
                return;
            }

            // GENERATE public void set<Name>(Object p) {
            //    ((PropertyInternal)<getter>()).setFromAnyValue(p);
            // }


            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, MetaProperty.getSetterName(property.getName()), RETURN_VOID_FROM_OBJECT, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), getter.getName(), Type.getMethodDescriptor(getter), false);
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, PROPERTY_INTERNAL_TYPE.getInternalName());
            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, PROPERTY_INTERNAL_TYPE.getInternalName(), "setFromAnyValue", ClassBuilderImpl.RETURN_VOID_FROM_OBJECT, true);
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Adds a getter that returns the value of the given field, initializing it if null using the given code. The code should leave the value on the top of the stack.
         */
        private void addLazyGetter(String methodName, Type returnType, String methodDescriptor, String signature, final String fieldName, final Type fieldType, final MethodCodeBody initializer) {
            addGetter(methodName, returnType, methodDescriptor, signature, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) {
                    // var = this.<field>
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), fieldName, fieldType.getDescriptor());
                    visitor.visitVarInsn(Opcodes.ASTORE, 1);
                    // if (var == null) { var = <code-body>; this.<field> = var; }
                    visitor.visitVarInsn(ALOAD, 1);
                    Label returnValue = new Label();
                    visitor.visitJumpInsn(Opcodes.IFNONNULL, returnValue);
                    initializer.add(visitor);
                    visitor.visitVarInsn(Opcodes.ASTORE, 1);
                    visitor.visitVarInsn(ALOAD, 0);
                    visitor.visitVarInsn(ALOAD, 1);
                    visitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), fieldName, fieldType.getDescriptor());
                    visitor.visitLabel(returnValue);
                    // return var
                    visitor.visitVarInsn(ALOAD, 1);
                }
            });
        }

        /**
         * Adds a getter that returns the value that the given code leaves on the top of the stack.
         */
        private void addGetter(String methodName, Type returnType, String methodDescriptor, String signature, MethodCodeBody body) {
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDescriptor, signature, EMPTY_STRINGS);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addDynamicMethods() {
            if (!mixInDsl) {
                return;
            }

            // GENERATE public Object getProperty(String name) { return getAsDynamicObject().getProperty(name); }

            addGetter("getProperty", OBJECT_TYPE, RETURN_OBJECT_FROM_STRING, null, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor methodVisitor) {
                    // GENERATE getAsDynamicObject().getProperty(name);

                    methodVisitor.visitVarInsn(ALOAD, 0);
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

                    methodVisitor.visitVarInsn(ALOAD, 1);
                    String getPropertyDesc = RETURN_OBJECT_FROM_STRING;
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "getProperty", getPropertyDesc, true);

                    // END
                }
            });

            // GENERATE public boolean hasProperty(String name) { return getAsDynamicObject().hasProperty(name) }

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "hasProperty", RETURN_BOOLEAN_FROM_STRING, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().hasProperty(name);

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

            methodVisitor.visitVarInsn(ALOAD, 1);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "hasProperty", RETURN_BOOLEAN_FROM_STRING, true);

            // END
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // GENERATE public void setProperty(String name, Object value) { getAsDynamicObject().setProperty(name, value); }

            addSetter("setProperty", RETURN_VOID_FROM_STRING_OBJECT,
                    new MethodCodeBody() {
                        @Override
                        public void add(MethodVisitor methodVisitor) {
                            // GENERATE getAsDynamicObject().setProperty(name, value)

                            methodVisitor.visitVarInsn(ALOAD, 0);
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

                            methodVisitor.visitVarInsn(ALOAD, 1);
                            methodVisitor.visitVarInsn(ALOAD, 2);
                            String setPropertyDesc = RETURN_VOID_FROM_STRING_OBJECT;
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "setProperty", setPropertyDesc, true);

                            // END
                        }
                    });

            // GENERATE public Object invokeMethod(String name, Object params) { return getAsDynamicObject().invokeMethod(name, (Object[])params); }

            addGetter("invokeMethod", OBJECT_TYPE, RETURN_OBJECT_FROM_STRING_OBJECT, null,
                    new MethodCodeBody() {
                        @Override
                        public void add(MethodVisitor methodVisitor) {
                            String invokeMethodDesc = Type.getMethodDescriptor(OBJECT_TYPE, STRING_TYPE, OBJECT_ARRAY_TYPE);

                            // GENERATE getAsDynamicObject().invokeMethod(name, (args instanceof Object[]) ? args : new Object[] { args })

                            methodVisitor.visitVarInsn(ALOAD, 0);
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);

                            methodVisitor.visitVarInsn(ALOAD, 1);

                            // GENERATE (args instanceof Object[]) ? args : new Object[] { args }
                            methodVisitor.visitVarInsn(ALOAD, 2);
                            methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, OBJECT_ARRAY_TYPE.getDescriptor());
                            Label end = new Label();
                            Label notArray = new Label();
                            methodVisitor.visitJumpInsn(Opcodes.IFEQ, notArray);

                            // Generate args
                            methodVisitor.visitVarInsn(ALOAD, 2);
                            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, OBJECT_ARRAY_TYPE.getDescriptor());
                            methodVisitor.visitJumpInsn(Opcodes.GOTO, end);

                            // Generate new Object[] { args }
                            methodVisitor.visitLabel(notArray);
                            methodVisitor.visitInsn(Opcodes.ICONST_1);
                            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, OBJECT_TYPE.getInternalName());
                            methodVisitor.visitInsn(Opcodes.DUP);
                            methodVisitor.visitInsn(Opcodes.ICONST_0);
                            methodVisitor.visitVarInsn(ALOAD, 2);
                            methodVisitor.visitInsn(Opcodes.AASTORE);

                            methodVisitor.visitLabel(end);

                            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "invokeMethod", invokeMethodDesc, true);
                        }
                    });
        }

        @Override
        public void applyServiceInjectionToProperty(PropertyMetadata property) {
            // GENERATE private <type> <property-field-name>;
            String fieldName = propFieldName(property);
            visitor.visitField(Opcodes.ACC_PRIVATE, fieldName, Type.getDescriptor(property.getType()), null, null);
        }

        private void generateServicesField() {
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC, SERVICES_FIELD, SERVICE_LOOKUP_TYPE.getDescriptor(), null, null);
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
            mv.visitMethodInsn(INVOKESTATIC, ASM_BACKED_CLASS_GENERATOR_TYPE.getInternalName(), "getServicesForNext", RETURN_SERVICE_LOOKUP, false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, MethodMetadata getter) {
            applyServiceInjectionToGetter(property, null, getter);
        }

        @Override
        public void applyServiceInjectionToGetter(PropertyMetadata property, final Class<? extends Annotation> annotation, MethodMetadata getter) {
            // GENERATE public <type> <getter>() { if (<field> == null) { <field> = <services>>.get(<service-type>>); } return <field> }
            final String getterName = getter.getName();
            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            final Type serviceType = Type.getType(property.getType());
            final java.lang.reflect.Type genericServiceType = property.getGenericType();
            String propFieldName = propFieldName(property);
            String signature = getterSignature(getter.getGenericReturnType());

            addLazyGetter(getterName, returnType, methodDescriptor, signature, propFieldName, serviceType, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor methodVisitor) {
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
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SERVICE_LOOKUP_TYPE.getInternalName(), "get", RETURN_OBJECT_FROM_TYPE, true);
                    } else {
                        // get(<type>, <annotation>)
                        methodVisitor.visitLdcInsn(Type.getType(annotation));
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SERVICE_LOOKUP_TYPE.getInternalName(), "get", Type.getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE, CLASS_TYPE), true);
                    }

                    // (<type>)<service>
                    methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, serviceType.getInternalName());
                }
            });
        }

        private void putServiceRegistryOnStack(MethodVisitor methodVisitor) {
            if (requiresServicesMethod) {
                // this.<services_method>()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), SERVICES_METHOD, RETURN_SERVICE_LOOKUP, false);
            } else {
                // this.getServices()
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getServices", RETURN_SERVICE_REGISTRY, false);
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
            visitor.visitField(Opcodes.ACC_PRIVATE, fieldName, Type.getDescriptor(property.getType()), null, null);
        }

        @Override
        public void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter) {
            // GENERATE public <type> <getter>() { if (<field> == null) { <field> = services.get(ObjectFactory.class).<factory-method>(xxx); } return <field> }
            Type propType = Type.getType(property.getType());
            Type returnType = Type.getType(getter.getReturnType());
            addLazyGetter(getter.getName(), returnType, Type.getMethodDescriptor(returnType), null, propFieldName(property), propType, methodVisitor -> {
                // GENERATE services.get(ObjectFactory.class)
                putServiceRegistryOnStack(methodVisitor);
                methodVisitor.visitLdcInsn(OBJECT_FACTORY_TYPE);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SERVICE_LOOKUP_TYPE.getInternalName(), "get", RETURN_OBJECT_FROM_TYPE, true);
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, OBJECT_FACTORY_TYPE.getInternalName());

                if (property.getType().equals(ConfigurableFileCollection.class)) {
                    // GENERATE objectFactory.fileCollection()
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "fileCollection", RETURN_CONFIGURABLE_FILE_COLLECTION, true);
                } else if (property.getType().equals(RegularFileProperty.class)) {
                    // GENERATE objectFactory.fileProperty()
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "fileProperty", RETURN_REGULAR_FILE_PROPERTY, true);
                } else if (property.getType().equals(DirectoryProperty.class)) {
                    // GENERATE objectFactory.directoryProperty()
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "directoryProperty", RETURN_DIRECTORY_PROPERTY, true);
                } else if (property.getType().equals(Property.class)) {
                    // GENERATE objectFactory.property(type)
                    Class<?> elementType = (Class<?>) ((ParameterizedType) property.getGenericType()).getActualTypeArguments()[0];
                    methodVisitor.visitLdcInsn(Type.getType(elementType));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "property", RETURN_PROPERTY_FROM_CLASS, true);
                } else if (property.getType().equals(ListProperty.class)) {
                    // GENERATE objectFactory.listProperty(type)
                    Class<?> elementType = (Class<?>) ((ParameterizedType) property.getGenericType()).getActualTypeArguments()[0];
                    methodVisitor.visitLdcInsn(Type.getType(elementType));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "listProperty", RETURN_LIST_PROPERTY_FROM_CLASS, true);
                } else if (property.getType().equals(SetProperty.class)) {
                    // GENERATE objectFactory.setProperty(type)
                    Class<?> elementType = (Class<?>) ((ParameterizedType) property.getGenericType()).getActualTypeArguments()[0];
                    methodVisitor.visitLdcInsn(Type.getType(elementType));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "setProperty", RETURN_SET_PROPERTY_FROM_CLASS, true);
                } else if (property.getType().equals(MapProperty.class)) {
                    // GENERATE objectFactory.setProperty(type)
                    Class<?> keyType = (Class<?>) ((ParameterizedType) property.getGenericType()).getActualTypeArguments()[0];
                    Class<?> elementType = (Class<?>) ((ParameterizedType) property.getGenericType()).getActualTypeArguments()[1];
                    methodVisitor.visitLdcInsn(Type.getType(keyType));
                    methodVisitor.visitLdcInsn(Type.getType(elementType));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, OBJECT_FACTORY_TYPE.getInternalName(), "mapProperty", RETURN_MAP_PROPERTY_FROM_CLASS_CLASS, true);
                } else {
                    throw new IllegalArgumentException();
                }
            });
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

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), methodDescriptor, signature(setter), EMPTY_STRINGS);
            methodVisitor.visitCode();

            // this.field = value
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(fieldType.getOpcode(Opcodes.ILOAD), 1);
            methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName, fieldType.getDescriptor());

            // return
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generatePublicTypeMethod() {
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

        @Override
        public void addManagedMethods(List<PropertyMetadata> mutableProperties, List<PropertyMetadata> readOnlyProperties) {
            visitor.visitField(PV_FINAL_STATIC, FACTORY_FIELD, Type.getType(Managed.Factory.class).getDescriptor(), null, null);

            // Generate: <init>(Object[] state) { }
            MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "<init>", Type.getMethodDescriptor(VOID_TYPE, OBJECT_ARRAY_TYPE), null, EMPTY_STRINGS);
            methodVisitor.visitVarInsn(ALOAD, 0);
            if (type.isInterface()) {
                methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, OBJECT_TYPE.getInternalName(), "<init>", RETURN_VOID, false);
            } else {
                methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>", RETURN_VOID, false);
            }
            for (int i = 0; i < mutableProperties.size(); i++) {
                PropertyMetadata propertyMetaData = mutableProperties.get(i);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitLdcInsn(i);
                methodVisitor.visitInsn(AALOAD);
                unboxOrCast(methodVisitor, propertyMetaData.getType(), Type.getType(propertyMetaData.getType()));
                String propFieldName = propFieldName(propertyMetaData);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName, Type.getType(propertyMetaData.getType()).getDescriptor());
            }
            for (int i = 0; i < readOnlyProperties.size(); i++) {
                PropertyMetadata propertyMetaData = readOnlyProperties.get(i);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitLdcInsn(i + mutableProperties.size());
                methodVisitor.visitInsn(AALOAD);
                unboxOrCast(methodVisitor, propertyMetaData.getType(), Type.getType(propertyMetaData.getType()));
                String propFieldName = propFieldName(propertyMetaData);
                methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), propFieldName, Type.getType(propertyMetaData.getType()).getDescriptor());
            }
            methodVisitor.visitInsn(RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: Class immutable() { return <properties.empty> && <read-only-properties.empty> }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "immutable", RETURN_BOOLEAN, null, EMPTY_STRINGS);
            // Could return true if all of the read only properties point to immutable objects, but at this stage there are no such types supported
            methodVisitor.visitLdcInsn(mutableProperties.isEmpty() && readOnlyProperties.isEmpty());
            methodVisitor.visitInsn(IRETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: Object[] unpackState() { state = new Object[<size>]; state[x] = <prop-field>; return state; }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC, "unpackState", RETURN_OBJECT, null, EMPTY_STRINGS);
            methodVisitor.visitLdcInsn(mutableProperties.size() + readOnlyProperties.size());
            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, OBJECT_TYPE.getInternalName());
            // TODO - property order needs to be deterministic across JVM invocations, i.e. sort the properties by name
            for (int i = 0; i < mutableProperties.size(); i++) {
                PropertyMetadata property = mutableProperties.get(i);
                String propFieldName = propFieldName(property);
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitLdcInsn(i);
                methodVisitor.visitVarInsn(ALOAD, 0);
                Type propertyType = Type.getType(property.getType());
                methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), propFieldName, propertyType.getDescriptor());
                maybeBox(methodVisitor, property.getType(), propertyType);
                methodVisitor.visitInsn(Opcodes.AASTORE);
            }
            for (int i = 0; i < readOnlyProperties.size(); i++) {
                PropertyMetadata property = readOnlyProperties.get(i);
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitLdcInsn(i + mutableProperties.size());
                methodVisitor.visitVarInsn(ALOAD, 0);
                MethodMetadata getter = property.getMainGetter();
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), getter.getName(), Type.getMethodDescriptor(Type.getType(getter.getReturnType())), false);
                maybeBox(methodVisitor, property.getType(), Type.getType(property.getType()));
                methodVisitor.visitInsn(Opcodes.AASTORE);
            }
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // Generate: Managed.Factory getFactory() { if (<factory-field> == null) { <factory-field> = new ManagedTypeFactory(this.getClass()); }; return <factory-field> }
            methodVisitor = visitor.visitMethod(ACC_PUBLIC | ACC_SYNCHRONIZED, "managedFactory", Type.getMethodDescriptor(Type.getType(Managed.Factory.class)), null, EMPTY_STRINGS);
            methodVisitor.visitFieldInsn(GETSTATIC, generatedType.getInternalName(), FACTORY_FIELD, Type.getType(Managed.Factory.class).getDescriptor());
            methodVisitor.visitInsn(DUP);
            Label label = new Label();
            methodVisitor.visitJumpInsn(IFNULL, label);
            methodVisitor.visitInsn(ARETURN);
            methodVisitor.visitLabel(label);
            methodVisitor.visitTypeInsn(Opcodes.NEW, Type.getType(ManagedTypeFactory.class).getInternalName());
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OBJECT_TYPE.getInternalName(), "getClass", RETURN_CLASS, false);
            methodVisitor.visitMethodInsn(INVOKESPECIAL, Type.getType(ManagedTypeFactory.class).getInternalName(), "<init>", Type.getMethodDescriptor(VOID_TYPE, CLASS_TYPE), false);
            methodVisitor.visitInsn(DUP);
            methodVisitor.visitFieldInsn(PUTSTATIC, generatedType.getInternalName(), FACTORY_FIELD, Type.getType(Managed.Factory.class).getDescriptor());
            methodVisitor.visitInsn(ARETURN);
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
            visitor.visitField(Opcodes.ACC_PRIVATE, flagName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);
        }

        private String propFieldName(PropertyMetadata property) {
            return "__" + property.getName() + "__";
        }

        @Override
        public void applyConventionMappingToGetter(PropertyMetadata property, Method getter) {
            if (!conventionAware) {
                return;
            }

            // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__); }
            String flagName = propFieldName(property);
            String getterName = getter.getName();

            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getterName, methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            if (hasMappingField) {
                // if (conventionMapping == null) { return super.<getter>; }
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR);
                Label useConvention = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, useConvention);
                methodVisitor.visitVarInsn(ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, type.isInterface());
                methodVisitor.visitInsn(returnType.getOpcode(IRETURN));

                methodVisitor.visitLabel(useConvention);
            }
            // else { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__);  }
            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONVENTION_AWARE_TYPE.getInternalName(), "getConventionMapping", Type.getMethodDescriptor(CONVENTION_MAPPING_TYPE), true);

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, type.isInterface());

            maybeBox(methodVisitor, getter.getReturnType(), returnType);

            methodVisitor.visitLdcInsn(property.getName());

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), flagName,
                    Type.BOOLEAN_TYPE.getDescriptor());

            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONVENTION_MAPPING_TYPE.getInternalName(), "getConventionValue", RETURN_OBJECT_FROM_STRING_OBJECT_BOOLEAN, true);

            unboxOrCast(methodVisitor, getter.getReturnType(), returnType);

            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Unboxes or casts the value at the top of the stack.
         */
        private void unboxOrCast(MethodVisitor methodVisitor, Class<?> targetClass, Type targetType) {
            if (targetClass.isPrimitive()) {
                // Unbox value
                Type boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetClass));
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, boxedType.getInternalName());
                String valueMethodDescriptor = Type.getMethodDescriptor(targetType);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, boxedType.getInternalName(), targetClass.getName() + "Value", valueMethodDescriptor, false);
            } else {
                // Cast to return type
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST,
                        targetClass.isArray() ? "[" + targetType.getElementType().getDescriptor()
                                : targetType.getInternalName());
            }
        }

        /**
         * Boxes the value at the top of the stack, if primitive
         */
        private void maybeBox(MethodVisitor methodVisitor, Class<?> valueClass, Type valueType) {
            if (valueClass.isPrimitive()) {
                // Box value
                Type boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueClass));
                String valueOfMethodDescriptor = Type.getMethodDescriptor(boxedType, valueType);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, boxedType.getInternalName(), "valueOf", valueOfMethodDescriptor, false);
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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), setterDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE super.<setter>(v)

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), setter.getName(), setterDescriptor, false);

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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, property.getName(), setMethodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE <setter>(v)

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), setter.getName(), setterDescriptor, false);

            // END

            methodVisitor.visitInsn(Opcodes.RETURN);
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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE super.<propName>(v)

            methodVisitor.visitVarInsn(ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), method.getName(), methodDescriptor, false);

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

            Type[] originalParameterTypes = CollectionUtils.collectArray(method.getParameterTypes(), Type.class, new Transformer<Type, Class>() {
                @Override
                public Type transform(Class clazz) {
                    return Type.getType(clazz);
                }
            });
            int numParams = originalParameterTypes.length;
            Type[] closurisedParameterTypes = new Type[numParams];
            System.arraycopy(originalParameterTypes, 0, closurisedParameterTypes, 0, numParams);
            closurisedParameterTypes[numParams - 1] = CLOSURE_TYPE;

            String methodDescriptor = Type.getMethodDescriptor(returnType, closurisedParameterTypes);

            // GENERATE public <return type> <method>(Closure v) { return <method>(…, ConfigureUtil.configureUsing(v)); }
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE <method>(…, ConfigureUtil.configureUsing(v));
            methodVisitor.visitVarInsn(ALOAD, 0);

            for (int stackVar = 1; stackVar < numParams; ++stackVar) {
                methodVisitor.visitVarInsn(closurisedParameterTypes[stackVar - 1].getOpcode(Opcodes.ILOAD), stackVar);
            }

            // GENERATE ConfigureUtil.configureUsing(v);
            methodVisitor.visitVarInsn(ALOAD, numParams);
            methodDescriptor = Type.getMethodDescriptor(ACTION_TYPE, CLOSURE_TYPE);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, CONFIGURE_UTIL_TYPE.getInternalName(), "configureUsing", methodDescriptor, false);

            methodDescriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), originalParameterTypes);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), method.getName(), methodDescriptor, false);

            methodVisitor.visitInsn(returnType.getOpcode(IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generateServiceRegistrySupportMethods() {
            generateServicesField();
            generateGetServices();
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
            } catch (IllegalAccessException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } catch (InvocationTargetException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public Class<?> generate() {
            writeGenericReturnTypeFields();
            visitor.visitEnd();

            return classGenerator.define();
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
            mv.visitInsn(Opcodes.ICONST_0);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, CLASS_TYPE.getInternalName());
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLASS_TYPE.getInternalName(), "getDeclaredMethod", GET_DECLARED_METHOD_DESCRIPTOR, false);
            // <method>.getGenericReturnType()
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, METHOD_TYPE.getInternalName(), "getGenericReturnType", Type.getMethodDescriptor(JAVA_LANG_REFLECT_TYPE), false);
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

    private interface MethodCodeBody {
        void add(MethodVisitor visitor);
    }

    private static class ObjectCreationDetails {
        final Instantiator instantiator;
        final ServiceLookup services;

        ObjectCreationDetails(Instantiator instantiator, ServiceLookup services) {
            this.instantiator = instantiator;
            this.services = services;
        }
    }

    private static class NoOpBuilder implements ClassGenerationVisitor {
        private final Class<?> type;

        public NoOpBuilder(Class<?> type) {
            this.type = type;
        }

        @Override
        public void addConstructor(Constructor<?> constructor) {
        }

        @Override
        public void addDefaultConstructor() {
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
        public void applyReadOnlyManagedStateToGetter(PropertyMetadata property, Method getter) {
        }

        @Override
        public void applyManagedStateToGetter(PropertyMetadata property, Method getter) {
        }

        @Override
        public void applyManagedStateToSetter(PropertyMetadata property, Method setter) {
        }

        @Override
        public void addManagedMethods(List<PropertyMetadata> properties, List<PropertyMetadata> readOnlyProperties) {
        }

        @Override
        public void applyConventionMappingToProperty(PropertyMetadata property) {
        }

        @Override
        public void applyConventionMappingToGetter(PropertyMetadata property, Method getter) {
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
        public void addPropertySetters(PropertyMetadata property, Method getter) {
        }

        @Override
        public Class<?> generate() {
            return type;
        }
    }
}
