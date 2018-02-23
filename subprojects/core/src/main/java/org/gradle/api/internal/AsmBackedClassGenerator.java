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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.PropertyInternal;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.internal.asm.AsmClassGenerator;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.signature;
import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.VOID_TYPE;

public class AsmBackedClassGenerator extends AbstractClassGenerator {
    @Override
    protected <T> ClassBuilder<T> start(Class<T> type, ClassMetaData classMetaData) {
        return new ClassBuilderImpl<T>(type, classMetaData);
    }

    private static class ClassBuilderImpl<T> implements ClassBuilder<T> {
        public static final int PV_FINAL_STATIC = Opcodes.ACC_PRIVATE | ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
        private static final Set<? extends Class<?>> PRIMITIVE_TYPES = ImmutableSet.of(Byte.TYPE, Boolean.TYPE, Character.TYPE, Short.TYPE, Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE);
        private static final String DYNAMIC_OBJECT_HELPER_FIELD = "__dyn_obj__";
        private static final String MAPPING_FIELD = "__mapping__";
        private static final String META_CLASS_FIELD = "__meta_class__";
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
        private final static Type ABSTRACT_DYNAMIC_OBJECT_TYPE = Type.getType(AbstractDynamicObject.class);
        private final static Type EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE = Type.getType(MixInExtensibleDynamicObject.class);
        private final static Type NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE = Type.getType(BeanDynamicObject.class);
        private static final String JAVA_REFLECT_TYPE_DESCRIPTOR = Type.getDescriptor(java.lang.reflect.Type.class);
        private static final Type CONFIGURE_UTIL_TYPE = Type.getType(ConfigureUtil.class);
        private static final Type CLOSURE_TYPE = Type.getType(Closure.class);
        private static final Type SERVICE_REGISTRY_TYPE = Type.getType(ServiceRegistry.class);
        private static final String SERVICE_REGISTRY_METHOD_DESCRIPTOR = Type.getMethodDescriptor(SERVICE_REGISTRY_TYPE);
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
        private static final Type WITH_SERVICE_REGISTRY = Type.getType(DependencyInjectingInstantiator.WithServiceRegistry.class);
        private static final Type PROPERTY_INTERNAL_TYPE = Type.getType(PropertyInternal.class);

        private static final String RETURN_VOID_FROM_OBJECT = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE);
        private static final String RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT = Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, CLASS_TYPE, DYNAMIC_OBJECT_TYPE);
        private static final String RETURN_CLASS = Type.getMethodDescriptor(CLASS_TYPE);
        private static final String RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION = Type.getMethodDescriptor(Type.VOID_TYPE, CONVENTION_AWARE_TYPE, CONVENTION_TYPE);
        private static final String RETURN_CONVENTION = Type.getMethodDescriptor(CONVENTION_TYPE);
        private static final String RETURN_DYNAMIC_OBJECT = Type.getMethodDescriptor(DYNAMIC_OBJECT_TYPE);
        private static final String RETURN_META_CLASS_FROM_CLASS = Type.getMethodDescriptor(Type.getType(MetaClass.class), CLASS_TYPE);
        private static final String RETURN_BOOLEAN_FROM_STRING = Type.getMethodDescriptor(BOOLEAN_TYPE, STRING_TYPE);
        private static final String RETURN_META_CLASS_REGISTRY = Type.getMethodDescriptor(Type.getType(MetaClassRegistry.class));
        private static final String GET_DECLARED_METHOD_DESCRIPTOR = Type.getMethodDescriptor(METHOD_TYPE, STRING_TYPE, CLASS_ARRAY_TYPE);
        private static final String GET_METHOD_DESCRIPTOR = Type.getMethodDescriptor(OBJECT_TYPE, JAVA_LANG_REFLECT_TYPE);

        private static final String[] EMPTY_STRINGS = new String[0];
        private static final Type[] EMPTY_TYPES = new Type[0];
        private static final String SERVICES_FIELD = "_services";

        private final ClassWriter visitor;
        private final Class<T> type;
        private final Type generatedType;
        private final Type superclassType;
        private final Map<java.lang.reflect.Type, ReturnTypeEntry> genericReturnTypeConstantsIndex = Maps.newHashMap();
        private final AsmClassGenerator classGenerator;
        private boolean hasMappingField;
        private final boolean conventionAware;
        private final boolean extensible;
        private final boolean providesOwnDynamicObject;

        private ClassBuilderImpl(Class<T> type, ClassMetaData classMetaData) {
            this.type = type;

            classGenerator = new AsmClassGenerator(type, "_Decorated");
            visitor = classGenerator.getVisitor();
            generatedType = classGenerator.getGeneratedType();
            superclassType = Type.getType(type);
            extensible = classMetaData.isExtensible();
            conventionAware = classMetaData.isConventionAware();
            providesOwnDynamicObject = classMetaData.providesDynamicObjectImplementation();
        }

        public void startClass(boolean shouldImplementWithServices) {
            List<String> interfaceTypes = new ArrayList<String>();

            interfaceTypes.add(GENERATED_SUBCLASS_TYPE.getInternalName());

            if (conventionAware && extensible) {
                interfaceTypes.add(CONVENTION_AWARE_TYPE.getInternalName());
            }

            if (extensible) {
                interfaceTypes.add(EXTENSION_AWARE_TYPE.getInternalName());
                interfaceTypes.add(HAS_CONVENTION_TYPE.getInternalName());
            }

            if (shouldImplementWithServices) {
                interfaceTypes.add(WITH_SERVICE_REGISTRY.getInternalName());
            }

            interfaceTypes.add(DYNAMIC_OBJECT_AWARE_TYPE.getInternalName());
            interfaceTypes.add(GROOVY_OBJECT_TYPE.getInternalName());

            includeNotInheritedAnnotations();

            visitor.visit(V1_5, ACC_PUBLIC | ACC_SYNTHETIC, generatedType.getInternalName(), null,
                superclassType.getInternalName(), interfaceTypes.toArray(EMPTY_STRINGS));
        }

        public void addConstructor(Constructor<?> constructor) throws Exception {
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
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                methodVisitor.visitVarInsn(Type.getType(constructor.getParameterTypes()[i]).getOpcode(Opcodes.ILOAD), i + 1);
            }
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>", methodDescriptor, false);

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void mixInDynamicAware() throws Exception {

            // GENERATE private DynamicObject dynamicObjectHelper
            final String fieldSignature = ABSTRACT_DYNAMIC_OBJECT_TYPE.getDescriptor();
            visitor.visitField(Opcodes.ACC_PRIVATE, DYNAMIC_OBJECT_HELPER_FIELD, fieldSignature, null, null);

            // END

            final Method getAsDynamicObject = DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject");
            if (extensible) {
                // GENERATE public Convention getConvention() { return getAsDynamicObject().getConvention(); }

                addGetter(HasConvention.class.getDeclaredMethod("getConvention"), new MethodCodeBody() {
                    public void add(MethodVisitor visitor) throws Exception {

                        // GENERATE ((MixInExtensibleDynamicObject)getAsDynamicObject()).getConvention()

                        visitor.visitVarInsn(Opcodes.ALOAD, 0);
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", Type.getMethodDescriptor(getAsDynamicObject), false);
                        visitor.visitTypeInsn(Opcodes.CHECKCAST, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "getConvention", RETURN_CONVENTION, false);
                    }
                });

                // END

                // GENERATE public ExtensionContainer getExtensions() { return getConvention(); }

                addGetter(ExtensionAware.class.getDeclaredMethod("getExtensions"), new MethodCodeBody() {
                    public void add(MethodVisitor visitor) throws Exception {

                        // GENERATE getConvention()

                        visitor.visitVarInsn(Opcodes.ALOAD, 0);
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false);
                    }
                });
            }

            // END

            // GENERATE public DynamicObject getAsDynamicObject() {
            //      if (dynamicObjectHelper == null) {
            //          dynamicObjectHelper = <init>
            //      }
            //      return dynamicObjectHelper;
            // }

            addLazyGetter(getAsDynamicObject, DYNAMIC_OBJECT_HELPER_FIELD, ABSTRACT_DYNAMIC_OBJECT_TYPE, new MethodCodeBody() {
                public void add(MethodVisitor visitor) {
                    generateCreateDynamicObject(visitor);
                }
            });

            // END
        }

        private void generateCreateDynamicObject(MethodVisitor visitor) {
            if (extensible) {

                // GENERATE new MixInExtensibleDynamicObject(this, getClass().getSuperClass(), super.getAsDynamicObject())

                visitor.visitTypeInsn(Opcodes.NEW, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getClass", RETURN_CLASS, false);
                visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CLASS_TYPE.getInternalName(), "getSuperclass", RETURN_CLASS, false);

                if (providesOwnDynamicObject) {
                    // GENERATE super.getAsDynamicObject()
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getType(type).getInternalName(),
                        "getAsDynamicObject", RETURN_DYNAMIC_OBJECT, false);
                } else {
                    // GENERATE null
                    visitor.visitInsn(Opcodes.ACONST_NULL);
                }

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_OBJECT_CLASS_DYNAMIC_OBJECT, false);
                // END
            } else {

                // GENERATE new BeanDynamicObject(this)

                visitor.visitTypeInsn(Opcodes.NEW, NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(Opcodes.ALOAD, 0);

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, NON_EXTENSIBLE_DYNAMIC_OBJECT_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_OBJECT, false);
                // END
            }
        }

        public void mixInConventionAware() throws Exception {
            if (!extensible) {
                return;
            }

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
                public void add(MethodVisitor visitor) throws Exception {
                    // GENERATE new ConventionAwareHelper(this, getConvention())

                    visitor.visitTypeInsn(Opcodes.NEW, CONVENTION_AWARE_HELPER_TYPE.getInternalName());
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);

                    // GENERATE getConvention()

                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention", RETURN_CONVENTION, false);

                    // END

                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, CONVENTION_AWARE_HELPER_TYPE.getInternalName(), "<init>", RETURN_VOID_FROM_CONVENTION_AWARE_CONVENTION, false);

                    // END
                }
            };

            addLazyGetter(IConventionAware.class.getDeclaredMethod("getConventionMapping"), MAPPING_FIELD, CONVENTION_MAPPING_TYPE, initConventionAwareHelper);

            // END
        }

        public void mixInGroovyObject() throws Exception {

            // GENERATE private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass())

            visitor.visitField(Opcodes.ACC_PRIVATE, META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR, null, null);

            // GENERATE public MetaClass getMetaClass() {
            //     if (metaClass == null) {
            //         metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
            //     }
            //     return metaClass;
            // }

            final MethodCodeBody initMetaClass = new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    // GroovySystem.getMetaClassRegistry()
                    visitor.visitMethodInsn(Opcodes.INVOKESTATIC, GROOVY_SYSTEM_TYPE.getInternalName(), "getMetaClassRegistry", RETURN_META_CLASS_REGISTRY, false);

                    // this.getClass()
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OBJECT_TYPE.getInternalName(), "getClass", RETURN_CLASS, false);

                    // getMetaClass(..)
                    visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, META_CLASS_REGISTRY_TYPE.getInternalName(), "getMetaClass", RETURN_META_CLASS_FROM_CLASS, true);
                }
            };

            addLazyGetter(GroovyObject.class.getDeclaredMethod("getMetaClass"), META_CLASS_FIELD, META_CLASS_TYPE, initMetaClass);

            // END

            // GENERATE public void setMetaClass(MetaClass class) { this.metaClass = class; }

            addSetter(GroovyObject.class.getDeclaredMethod("setMetaClass", MetaClass.class), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitVarInsn(Opcodes.ALOAD, 1);
                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), META_CLASS_FIELD, META_CLASS_TYPE_DESCRIPTOR);
                }
            });
        }

        private void addSetter(Method method, MethodCodeBody body) throws Exception {
            String methodDescriptor = Type.getMethodDescriptor(method);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void addPropertySetters(PropertyMetaData property, Method getter) throws Exception {

            // GENERATE public void set<Name>(Object p) {
            //    ((PropertyInternal)<getter>()).setFromAnyValue(p);
            // }

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "set" + StringUtils.capitalize(property.getName()), RETURN_VOID_FROM_OBJECT, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), getter.getName(), Type.getMethodDescriptor(getter), false);
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, PROPERTY_INTERNAL_TYPE.getInternalName());
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, PROPERTY_INTERNAL_TYPE.getInternalName(), "setFromAnyValue", ClassBuilderImpl.RETURN_VOID_FROM_OBJECT, true);
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Adds a getter that returns the value of the given field, initializing it if null.
         */
        private void addLazyGetter(Method method, final String fieldName, final Type fieldType, final MethodCodeBody initializer) throws Exception {
            String methodDescriptor = Type.getMethodDescriptor(method);
            String methodName = method.getName();
            addGetter(methodName, methodDescriptor, new MethodCodeBody() {
                @Override
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), fieldName, fieldType.getDescriptor());
                    visitor.visitVarInsn(Opcodes.ASTORE, 1);
                    visitor.visitVarInsn(Opcodes.ALOAD, 1);
                    Label returnValue = new Label();
                    visitor.visitJumpInsn(Opcodes.IFNONNULL, returnValue);
                    initializer.add(visitor);
                    visitor.visitVarInsn(Opcodes.ASTORE, 1);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitVarInsn(Opcodes.ALOAD, 1);
                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), fieldName, fieldType.getDescriptor());
                    visitor.visitLabel(returnValue);
                    visitor.visitVarInsn(Opcodes.ALOAD, 1);
                }
            });
        }

        private void addGetter(Method method, MethodCodeBody body) throws Exception {
            String methodDescriptor = Type.getMethodDescriptor(method);
            String methodName = method.getName();
            addGetter(methodName, methodDescriptor, body);
        }

        private void addGetter(String methodName, String methodDescriptor, MethodCodeBody body) throws Exception {
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addDynamicMethods() throws Exception {

            // GENERATE public Object getProperty(String name) { return getAsDynamicObject().getProperty(name); }

            addGetter(GroovyObject.class.getDeclaredMethod("getProperty", String.class), new MethodCodeBody() {
                public void add(MethodVisitor methodVisitor) throws Exception {
                    // GENERATE getAsDynamicObject().getProperty(name);

                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    String getAsDynamicObjectDesc = Type.getMethodDescriptor(DynamicObjectAware.class.getDeclaredMethod(
                        "getAsDynamicObject"));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", getAsDynamicObjectDesc, false);

                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
                    String getPropertyDesc = Type.getMethodDescriptor(DynamicObject.class.getDeclaredMethod(
                        "getProperty", String.class));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "getProperty", getPropertyDesc, true);

                    // END
                }
            });

            // GENERATE public boolean hasProperty(String name) { return getAsDynamicObject().hasProperty(name) }

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "hasProperty", RETURN_BOOLEAN_FROM_STRING, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().hasProperty(name);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            String getAsDynamicObjectDesc = Type.getMethodDescriptor(DynamicObjectAware.class.getDeclaredMethod(
                "getAsDynamicObject"));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", getAsDynamicObjectDesc, false);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            String getPropertyDesc = Type.getMethodDescriptor(PropertyAccess.class.getDeclaredMethod("hasProperty", String.class));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "hasProperty", getPropertyDesc, true);

            // END
            methodVisitor.visitInsn(Opcodes.IRETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // GENERATE public void setProperty(String name, Object value) { getAsDynamicObject().setProperty(name, value); }

            addSetter(GroovyObject.class.getDeclaredMethod("setProperty", String.class, Object.class),
                new MethodCodeBody() {
                    public void add(MethodVisitor methodVisitor) throws Exception {
                        // GENERATE getAsDynamicObject().setProperty(name, value)

                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                        String getAsDynamicObjectDesc = Type.getMethodDescriptor(
                            DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject"));
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", getAsDynamicObjectDesc, false);

                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                        String setPropertyDesc = Type.getMethodDescriptor(DynamicObject.class.getDeclaredMethod(
                            "setProperty", String.class, Object.class));
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "setProperty", setPropertyDesc, true);

                        // END
                    }
                });

            // GENERATE public Object invokeMethod(String name, Object params) { return getAsDynamicObject().invokeMethod(name, (Object[])params); }

            addGetter(GroovyObject.class.getDeclaredMethod("invokeMethod", String.class, Object.class),
                new MethodCodeBody() {
                    public void add(MethodVisitor methodVisitor) throws Exception {
                        String invokeMethodDesc = Type.getMethodDescriptor(OBJECT_TYPE, STRING_TYPE, OBJECT_ARRAY_TYPE);

                        // GENERATE getAsDynamicObject().invokeMethod(name, (args instanceof Object[]) ? args : new Object[] { args })

                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                        String getAsDynamicObjectDesc = Type.getMethodDescriptor(
                            DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject"));
                        methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject", getAsDynamicObjectDesc, false);

                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);

                        // GENERATE (args instanceof Object[]) ? args : new Object[] { args }
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                        methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, OBJECT_ARRAY_TYPE.getDescriptor());
                        Label end = new Label();
                        Label notArray = new Label();
                        methodVisitor.visitJumpInsn(Opcodes.IFEQ, notArray);

                        // Generate args
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, OBJECT_ARRAY_TYPE.getDescriptor());
                        methodVisitor.visitJumpInsn(Opcodes.GOTO, end);

                        // Generate new Object[] { args }
                        methodVisitor.visitLabel(notArray);
                        methodVisitor.visitInsn(Opcodes.ICONST_1);
                        methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, OBJECT_TYPE.getInternalName());
                        methodVisitor.visitInsn(Opcodes.DUP);
                        methodVisitor.visitInsn(Opcodes.ICONST_0);
                        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                        methodVisitor.visitInsn(Opcodes.AASTORE);

                        methodVisitor.visitLabel(end);

                        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, DYNAMIC_OBJECT_TYPE.getInternalName(), "invokeMethod", invokeMethodDesc, true);
                    }
                });
        }

        public void addInjectorProperty(PropertyMetaData property) {
            // GENERATE private <type> <property-field-name>;
            String flagName = propFieldName(property);
            visitor.visitField(Opcodes.ACC_PRIVATE, flagName, Type.getDescriptor(property.getType()), null, null);
        }

        private void generateServicesField() {
            visitor.visitField(ACC_PRIVATE | ACC_SYNTHETIC, SERVICES_FIELD, SERVICE_REGISTRY_TYPE.getDescriptor(), null, null);
        }

        private void generateGetServices() {
            MethodVisitor mv = visitor.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "getServices", SERVICE_REGISTRY_METHOD_DESCRIPTOR, null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, generatedType.getInternalName(), SERVICES_FIELD, SERVICE_REGISTRY_TYPE.getDescriptor());
            mv.visitInsn(ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private void generateSetServices() {
            MethodVisitor mv = visitor.visitMethod(ACC_PUBLIC | ACC_SYNTHETIC, "setServices", "(" + SERVICE_REGISTRY_TYPE.getDescriptor() + ")V", null, null);
            mv.visitCode();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1);
            mv.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), SERVICES_FIELD, SERVICE_REGISTRY_TYPE.getDescriptor());
            mv.visitInsn(RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        public void applyServiceInjectionToGetter(PropertyMetaData property, Method getter) throws Exception {
            // GENERATE public <type> <getter>() { if (<field> == null) { <field> = getServices().get(getClass().getDeclaredMethod(<getter-name>).getGenericReturnType()); } return <field> }
            String getterName = getter.getName();
            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            Type serviceType = Type.getType(property.getType());
            String propFieldName = propFieldName(property);

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getterName, methodDescriptor, signature(getter), EMPTY_STRINGS);
            methodVisitor.visitCode();

            // if (this.<field> == null) { ...  }

            // this.field
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), propFieldName, serviceType.getDescriptor());

            Label alreadyLoaded = new Label();
            methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, alreadyLoaded);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);

            // this.getServices()
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getServices", SERVICE_REGISTRY_METHOD_DESCRIPTOR, false);

            java.lang.reflect.Type genericReturnType = getter.getGenericReturnType();
            if (genericReturnType instanceof Class) {
                // if the return type doesn't use generics, then it's faster to just rely on the type name directly
                methodVisitor.visitLdcInsn(Type.getType((Class) genericReturnType));
            } else {
                // load the static type descriptor from class constants
                String constantFieldName = getConstantNameForGenericReturnType(genericReturnType, getterName);
                methodVisitor.visitFieldInsn(GETSTATIC, generatedType.getInternalName(), constantFieldName, JAVA_REFLECT_TYPE_DESCRIPTOR);
            }

            // get(<type>)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, SERVICE_REGISTRY_TYPE.getInternalName(), "get", GET_METHOD_DESCRIPTOR, true);

            // this.field = (<type>)<service>
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, serviceType.getInternalName());
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), propFieldName, serviceType.getDescriptor());

            // this.field
            methodVisitor.visitLabel(alreadyLoaded);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), propFieldName, serviceType.getDescriptor());

            // return
            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
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

        public void applyServiceInjectionToSetter(PropertyMetaData property, Method setter) throws Exception {
            // GENERATE public void <setter>(<type> value) { <field> == value }
            String methodDescriptor = Type.getMethodDescriptor(setter);
            Type serviceType = Type.getType(property.getType());
            String propFieldName = propFieldName(property);

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), methodDescriptor, signature(setter), EMPTY_STRINGS);
            methodVisitor.visitCode();

            // this.field = value
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), propFieldName, serviceType.getDescriptor());

            // return
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addConventionProperty(PropertyMetaData property) throws Exception {
            // GENERATE private boolean <flag-name>;
            String flagName = propFieldName(property);
            visitor.visitField(Opcodes.ACC_PRIVATE, flagName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);
        }

        private String propFieldName(PropertyMetaData property) {
            return "__" + property.getName() + "__";
        }

        public void applyConventionMappingToGetter(PropertyMetaData property, Method getter) throws Exception {
            // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__); }
            String flagName = propFieldName(property);
            String getterName = getter.getName();

            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getterName, methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            if (hasMappingField) {
                // if (conventionMapping == null) { return super.<getter>; }
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), MAPPING_FIELD, CONVENTION_MAPPING_FIELD_DESCRIPTOR);
                Label useConvention = new Label();
                methodVisitor.visitJumpInsn(Opcodes.IFNONNULL, useConvention);
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, false);
                methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));

                methodVisitor.visitLabel(useConvention);
            }
            // else { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__);  }
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONVENTION_AWARE_TYPE.getInternalName(), "getConventionMapping", Type.getMethodDescriptor(CONVENTION_MAPPING_TYPE), true);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getterName, methodDescriptor, false);

            Type boxedType = null;
            if (getter.getReturnType().isPrimitive()) {
                // Box value
                boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(getter.getReturnType()));
                String valueOfMethodDescriptor = Type.getMethodDescriptor(boxedType, returnType);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, boxedType.getInternalName(), "valueOf", valueOfMethodDescriptor, false);
            }

            methodVisitor.visitLdcInsn(property.getName());

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), flagName,
                Type.BOOLEAN_TYPE.getDescriptor());

            String getConventionValueDesc = Type.getMethodDescriptor(ConventionMapping.class.getMethod(
                "getConventionValue", Object.class, String.class, Boolean.TYPE));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONVENTION_MAPPING_TYPE.getInternalName(), "getConventionValue", getConventionValueDesc, true);

            if (getter.getReturnType().isPrimitive()) {
                // Unbox value
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, boxedType.getInternalName());
                String valueMethodDescriptor = Type.getMethodDescriptor(returnType);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, boxedType.getInternalName(), getter.getReturnType().getName() + "Value", valueMethodDescriptor, false);
            } else {
                // Cast to return type
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST,
                    getter.getReturnType().isArray() ? "[" + returnType.getElementType().getDescriptor()
                        : returnType.getInternalName());
            }

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void applyConventionMappingToSetter(PropertyMetaData property, Method setter) throws Exception {
            // GENERATE public <return-type> <setter>(<type> v) { <return-type> v = super.<setter>(v); __<prop>__ = true; return v; }

            Type paramType = Type.getType(setter.getParameterTypes()[0]);
            Type returnType = Type.getType(setter.getReturnType());
            String setterDescriptor = Type.getMethodDescriptor(returnType, paramType);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), setterDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE super.<setter>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), setter.getName(), setterDescriptor, false);

            // END

            // GENERATE __<prop>__ = true

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitLdcInsn(true);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), propFieldName(property), Type.BOOLEAN_TYPE.getDescriptor());

            // END

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addSetMethod(PropertyMetaData property, Method setter) throws Exception {
            Type paramType = Type.getType(setter.getParameterTypes()[0]);
            Type returnType = Type.getType(setter.getReturnType());
            String setterDescriptor = Type.getMethodDescriptor(returnType, paramType);

            // GENERATE public void <propName>(<type> v) { <setter>(v) }
            String setMethodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, paramType);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, property.getName(), setMethodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE <setter>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), setter.getName(), setterDescriptor, false);

            // END

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void applyConventionMappingToSetMethod(PropertyMetaData property, Method method) throws Exception {
            Type paramType = Type.getType(method.getParameterTypes()[0]);
            Type returnType = Type.getType(method.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType, paramType);

            // GENERATE public <returnType> <propName>(<type> v) { val = super.<propName>(v); __<prop>__ = true; return val; }
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, EMPTY_STRINGS);
            methodVisitor.visitCode();

            // GENERATE super.<propName>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), method.getName(), methodDescriptor, false);

            // GENERATE __<prop>__ = true

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitLdcInsn(true);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), propFieldName(property), Type.BOOLEAN_TYPE.getDescriptor());

            // END

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addActionMethod(Method method) throws Exception {
            Type returnType = Type.getType(method.getReturnType());

            Type[] originalParameterTypes = CollectionUtils.collectArray(method.getParameterTypes(), Type.class, new Transformer<Type, Class>() {
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
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);

            for (int stackVar = 1; stackVar < numParams; ++stackVar) {
                methodVisitor.visitVarInsn(closurisedParameterTypes[stackVar - 1].getOpcode(Opcodes.ILOAD), stackVar);
            }

            // GENERATE ConfigureUtil.configureUsing(v);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, numParams);
            methodDescriptor = Type.getMethodDescriptor(ACTION_TYPE, CLOSURE_TYPE);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, CONFIGURE_UTIL_TYPE.getInternalName(), "configureUsing", methodDescriptor, false);

            methodDescriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), originalParameterTypes);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), method.getName(), methodDescriptor, false);

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        @Override
        public void generateServiceRegistrySupportMethods() throws Exception {
            generateServicesField();
            generateGetServices();
            generateSetServices();
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

        public Class<? extends T> generate() {
            writeGenericReturnTypeFields();
            visitor.visitEnd();

            return classGenerator.define().asSubclass(type);
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
        void add(MethodVisitor visitor) throws Exception;
    }

    public static class MixInExtensibleDynamicObject extends ExtensibleDynamicObject {
        public MixInExtensibleDynamicObject(Object decoratedObject, Class<?> publicType, @Nullable DynamicObject selfProvidedDynamicObject) {
            super(decoratedObject, wrap(decoratedObject, publicType, selfProvidedDynamicObject), ThreadGlobalInstantiator.getOrCreate());
        }

        private static AbstractDynamicObject wrap(Object delegateObject, Class<?> publicType, DynamicObject dynamicObject) {
            if (dynamicObject != null) {
                return (AbstractDynamicObject) dynamicObject;
            }
            return new BeanDynamicObject(delegateObject, publicType);
        }
    }

}
