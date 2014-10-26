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

import groovy.lang.*;
import org.gradle.api.Transformer;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.CollectionUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

public class AsmBackedClassGenerator extends AbstractClassGenerator {
    private static final JavaMethod<ClassLoader, Class> DEFINE_CLASS_METHOD = JavaReflectionUtil.method(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);

    @Override
    protected <T> ClassBuilder<T> start(Class<T> type, ClassMetaData classMetaData) {
        return new ClassBuilderImpl<T>(type, classMetaData);
    }

    private static class ClassBuilderImpl<T> implements ClassBuilder<T> {
        private static final String DYNAMIC_OBJECT_HELPER_FIELD = "__dyn_obj__";
        private static final String MAPPING_FIELD = "__mapping__";
        private static final String META_CLASS_FIELD = "__meta_class__";
        private final ClassWriter visitor;
        private final Class<T> type;
        private final String typeName;
        private final Type generatedType;
        private final Type superclassType;
        private final Type conventionAwareType = Type.getType(IConventionAware.class);
        private final Type dynamicObjectAwareType = Type.getType(DynamicObjectAware.class);
        private final Type extensionAwareType = Type.getType(ExtensionAware.class);
        private final Type hasConventionType = Type.getType(HasConvention.class);
        private final Type dynamicObjectType = Type.getType(DynamicObject.class);
        private final Type conventionMappingType = Type.getType(ConventionMapping.class);
        private final Type groovyObjectType = Type.getType(GroovyObject.class);
        private final Type conventionType = Type.getType(Convention.class);
        private final Type extensibleDynamicObjectHelperType = Type.getType(MixInExtensibleDynamicObject.class);
        private final Type nonExtensibleDynamicObjectHelperType = Type.getType(BeanDynamicObject.class);

        private final boolean conventionAware;
        private final boolean extensible;
        private final boolean providesOwnDynamicObject;

        private ClassBuilderImpl(Class<T> type, ClassMetaData classMetaData) {
            this.type = type;

            visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            typeName = type.getName() + "_Decorated";
            generatedType = Type.getType("L" + typeName.replaceAll("\\.", "/") + ";");
            superclassType = Type.getType(type);
            extensible = classMetaData.isExtensible();
            conventionAware = classMetaData.isConventionAware();
            providesOwnDynamicObject = classMetaData.providesDynamicObjectImplementation();
        }

        public void startClass() {
            List<String> interfaceTypes = new ArrayList<String>();
            if (conventionAware && extensible) {
                interfaceTypes.add(conventionAwareType.getInternalName());
            }

            if (extensible) {
                interfaceTypes.add(extensionAwareType.getInternalName());
                interfaceTypes.add(hasConventionType.getInternalName());
            }

            interfaceTypes.add(dynamicObjectAwareType.getInternalName());
            interfaceTypes.add(groovyObjectType.getInternalName());

            visitor.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null,
                    superclassType.getInternalName(), interfaceTypes.toArray(new String[interfaceTypes.size()]));
        }

        public void addConstructor(Constructor<?> constructor) throws Exception {
            List<Type> paramTypes = new ArrayList<Type>();
            for (Class<?> paramType : constructor.getParameterTypes()) {
                paramTypes.add(Type.getType(paramType));
            }
            String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, paramTypes.toArray(
                    new Type[paramTypes.size()]));

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", methodDescriptor, signature(constructor), new String[0]);

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
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>",
                    methodDescriptor);

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Generates the signature for the given constructor
         */
        private String signature(Constructor<?> constructor) {
            StringBuilder builder = new StringBuilder();
            visitFormalTypeParameters(builder, constructor.getTypeParameters());
            visitParameters(builder, constructor.getGenericParameterTypes());
            builder.append("V");
            visitExceptions(builder, constructor.getGenericExceptionTypes());
            return builder.toString();
        }

        /**
         * Generates the signature for the given method
         */
        private String signature(Method method) {
            StringBuilder builder = new StringBuilder();
            visitFormalTypeParameters(builder, method.getTypeParameters());
            visitParameters(builder, method.getGenericParameterTypes());
            visitType(method.getGenericReturnType(), builder);
            visitExceptions(builder, method.getGenericExceptionTypes());
            return builder.toString();
        }

        private void visitExceptions(StringBuilder builder, java.lang.reflect.Type[] exceptionTypes) {
            for (java.lang.reflect.Type exceptionType : exceptionTypes) {
                builder.append('^');
                visitType(exceptionType, builder);
            }
        }

        private void visitParameters(StringBuilder builder, java.lang.reflect.Type[] parameterTypes) {
            builder.append('(');
            for (java.lang.reflect.Type paramType : parameterTypes) {
                visitType(paramType, builder);
            }
            builder.append(")");
        }

        private void visitFormalTypeParameters(StringBuilder builder, TypeVariable<?>[] typeParameters) {
            if (typeParameters.length > 0) {
                builder.append('<');
                for (TypeVariable<?> typeVariable : typeParameters) {
                    builder.append(typeVariable.getName());
                    for (java.lang.reflect.Type bound : typeVariable.getBounds()) {
                        builder.append(':');
                        visitType(bound, builder);
                    }
                }
                builder.append('>');
            }
        }

        private void visitType(java.lang.reflect.Type type, StringBuilder builder) {
            if (type instanceof Class) {
                Class<?> cl = (Class<?>) type;
                if (cl.isPrimitive()) {
                    builder.append(Type.getType(cl).getDescriptor());
                } else {
                    if (cl.isArray()) {
                        builder.append(cl.getName().replace('.', '/'));
                    } else {
                        builder.append('L');
                        builder.append(cl.getName().replace('.', '/'));
                        builder.append(';');
                    }
                }
            } else if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                visitNested(parameterizedType.getRawType(), builder);
                builder.append('<');
                for (java.lang.reflect.Type param : parameterizedType.getActualTypeArguments()) {
                    visitType(param, builder);
                }
                builder.append(">;");
            } else if (type instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) type;
                if (wildcardType.getUpperBounds().length == 1 && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                    if (wildcardType.getLowerBounds().length == 0) {
                        builder.append('*');
                        return;
                    }
                } else {
                    for (java.lang.reflect.Type upperType : wildcardType.getUpperBounds()) {
                        builder.append('+');
                        visitType(upperType, builder);
                    }
                }
                for (java.lang.reflect.Type lowerType : wildcardType.getLowerBounds()) {
                    builder.append('-');
                    visitType(lowerType, builder);
                }
            } else if (type instanceof TypeVariable) {
                TypeVariable<?> typeVar = (TypeVariable) type;
                builder.append('T');
                builder.append(typeVar.getName());
                builder.append(';');
            } else if (type instanceof GenericArrayType) {
                GenericArrayType arrayType = (GenericArrayType) type;
                builder.append('[');
                visitType(arrayType.getGenericComponentType(), builder);
            } else {
                throw new IllegalArgumentException(String.format("Cannot generate signature for %s.", type));
            }
        }

        private void visitNested(java.lang.reflect.Type type, StringBuilder builder) {
            if (type instanceof Class) {
                Class<?> cl = (Class<?>) type;
                if (cl.isPrimitive()) {
                    builder.append(Type.getType(cl).getDescriptor());
                } else {
                    builder.append('L');
                    builder.append(cl.getName().replace('.', '/'));
                }
            } else {
                visitType(type, builder);
            }
        }

        public void mixInDynamicAware() throws Exception {

            // GENERATE private DynamicObject dynamicObjectHelper

            Type extensibleObjectFieldType = extensible ? extensibleDynamicObjectHelperType : nonExtensibleDynamicObjectHelperType;
            final String fieldSignature = extensibleObjectFieldType.getDescriptor();
            visitor.visitField(Opcodes.ACC_PRIVATE, DYNAMIC_OBJECT_HELPER_FIELD, fieldSignature, null, null);

            // END

            final Method getAsDynamicObject = DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject");
            if (extensible) {
                // GENERATE public Convention getConvention() { return getAsDynamicObject().getConvention(); }

                addGetter(HasConvention.class.getDeclaredMethod("getConvention"), new MethodCodeBody() {
                    public void add(MethodVisitor visitor) throws Exception {

                        // GENERATE ((MixInExtensibleDynamicObject)getAsDynamicObject()).getConvention()

                        visitor.visitVarInsn(Opcodes.ALOAD, 0);
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject",
                                Type.getMethodDescriptor(getAsDynamicObject));
                        visitor.visitTypeInsn(Opcodes.CHECKCAST, extensibleDynamicObjectHelperType.getInternalName());
                        String getterDescriptor = Type.getMethodDescriptor(ExtensibleDynamicObject.class.getDeclaredMethod("getConvention"));
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, extensibleDynamicObjectHelperType.getInternalName(), "getConvention",
                                getterDescriptor);
                    }
                });

                // END

                // GENERATE public ExtensionContainer getExtensions() { return getConvention(); }

                addGetter(ExtensionAware.class.getDeclaredMethod("getExtensions"), new MethodCodeBody() {
                    public void add(MethodVisitor visitor) throws Exception {

                        // GENERATE getConvention()

                        visitor.visitVarInsn(Opcodes.ALOAD, 0);
                        String getterDescriptor = Type.getMethodDescriptor(ExtensibleDynamicObject.class.getDeclaredMethod("getConvention"));
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention",
                                getterDescriptor);
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
            addGetter(getAsDynamicObject, new MethodCodeBody() {
                public void add(MethodVisitor visitor) {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), DYNAMIC_OBJECT_HELPER_FIELD, fieldSignature);
                    Label returnValue = new Label();
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitJumpInsn(Opcodes.IFNONNULL, returnValue);
                    visitor.visitInsn(Opcodes.POP);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    generateCreateDynamicObject(visitor);
                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), DYNAMIC_OBJECT_HELPER_FIELD, fieldSignature);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), DYNAMIC_OBJECT_HELPER_FIELD, fieldSignature);
                    visitor.visitLabel(returnValue);
                }
            });

            // END
        }

        private void generateCreateDynamicObject(MethodVisitor visitor) {
            if (extensible) {

                String helperTypeConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class), dynamicObjectType);

                // GENERATE new MixInExtensibleDynamicObject(this, super.getAsDynamicObject())

                visitor.visitTypeInsn(Opcodes.NEW, extensibleDynamicObjectHelperType.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(Opcodes.ALOAD, 0);

                if (providesOwnDynamicObject) {
                    // GENERATE super.getAsDynamicObject()
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getType(type).getInternalName(),
                            "getAsDynamicObject", Type.getMethodDescriptor(dynamicObjectType));
                } else {
                    // GENERATE null
                    visitor.visitInsn(Opcodes.ACONST_NULL);
                }

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, extensibleDynamicObjectHelperType.getInternalName(), "<init>",
                        helperTypeConstructorDesc);
                // END
            } else {
                String helperTypeConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Object.class));

                // GENERATE new BeanDynamicObject(this)

                visitor.visitTypeInsn(Opcodes.NEW, nonExtensibleDynamicObjectHelperType.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(Opcodes.ALOAD, 0);

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, nonExtensibleDynamicObjectHelperType.getInternalName(), "<init>",
                        helperTypeConstructorDesc);
                // END
            }
        }

        public void mixInConventionAware() throws Exception {
            if (!extensible) {
                return;
            }

            // GENERATE private ConventionMapping mapping = new ConventionAwareHelper(this, getConvention())

            final String mappingFieldSignature = Type.getDescriptor(ConventionMapping.class);
            final String getConventionDesc = Type.getMethodDescriptor(conventionType);

            visitor.visitField(Opcodes.ACC_PRIVATE, MAPPING_FIELD, mappingFieldSignature, null, null);

            final MethodCodeBody initConventionAwareHelper = new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    Type helperType = Type.getType(ConventionAwareHelper.class);

                    // GENERATE mapping = new ConventionAwareHelper(this, getConvention())
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);

                    visitor.visitTypeInsn(Opcodes.NEW, helperType.getInternalName());
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);

                    // GENERATE getConvention()

                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention",
                            getConventionDesc);

                    // END

                    String constructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, conventionAwareType, conventionType);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, helperType.getInternalName(), "<init>",
                            constructorDesc);

                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), MAPPING_FIELD,
                            mappingFieldSignature);

                    // END
                }
            };

            // END

            // GENERATE public ConventionMapping getConventionMapping() {
            //     if (mapping == null) {
            //         mapping = new ConventionAwareHelper(this, getConvention);
            //     }
            //     return mapping;
            // }

            addGetter(IConventionAware.class.getDeclaredMethod("getConventionMapping"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), MAPPING_FIELD, mappingFieldSignature);
                    visitor.visitInsn(Opcodes.DUP);
                    Label returnValue = new Label();
                    visitor.visitJumpInsn(Opcodes.IFNONNULL, returnValue);
                    visitor.visitInsn(Opcodes.POP);
                    initConventionAwareHelper.add(visitor);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), MAPPING_FIELD, mappingFieldSignature);
                    visitor.visitLabel(returnValue);
                }
            });
        }

        public void mixInGroovyObject() throws Exception {

            // GENERATE private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass())

            final String metaClassFieldSignature = Type.getDescriptor(MetaClass.class);
            visitor.visitField(Opcodes.ACC_PRIVATE, META_CLASS_FIELD, metaClassFieldSignature, null, null);

            final MethodCodeBody initMetaClass = new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);

                    // GroovySystem.getMetaClassRegistry()
                    String getMetaClassRegistryDesc = Type.getMethodDescriptor(GroovySystem.class.getDeclaredMethod(
                            "getMetaClassRegistry"));
                    visitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getType(GroovySystem.class).getInternalName(),
                            "getMetaClassRegistry", getMetaClassRegistryDesc);

                    // this.getClass()
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    String getClassDesc = Type.getMethodDescriptor(Object.class.getDeclaredMethod("getClass"));
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getType(Object.class).getInternalName(),
                            "getClass", getClassDesc);

                    // getMetaClass(..)
                    String getMetaClassDesc = Type.getMethodDescriptor(MetaClassRegistry.class.getDeclaredMethod(
                            "getMetaClass", Class.class));
                    visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getType(
                            MetaClassRegistry.class).getInternalName(), "getMetaClass", getMetaClassDesc);

                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), META_CLASS_FIELD,
                            metaClassFieldSignature);
                }
            };

            // GENERATE public MetaClass getMetaClass() {
            //     if (metaClass == null) {
            //         metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass());
            //     }
            //     return metaClass;
            // }

            addGetter(GroovyObject.class.getDeclaredMethod("getMetaClass"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), META_CLASS_FIELD, metaClassFieldSignature);
                    visitor.visitInsn(Opcodes.DUP);
                    Label returnValue = new Label();
                    visitor.visitJumpInsn(Opcodes.IFNONNULL, returnValue);
                    visitor.visitInsn(Opcodes.POP);
                    initMetaClass.add(visitor);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), META_CLASS_FIELD, metaClassFieldSignature);
                    visitor.visitLabel(returnValue);
                }
            });

            // GENERATE public void setMetaClass(MetaClass class) { this.metaClass = class; }

            addSetter(GroovyObject.class.getDeclaredMethod("setMetaClass", MetaClass.class), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitVarInsn(Opcodes.ALOAD, 1);
                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), META_CLASS_FIELD,
                            metaClassFieldSignature);
                }
            });
        }

        private void addSetter(Method method, MethodCodeBody body) throws Exception {
            String methodDescriptor = Type.getMethodDescriptor(method);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void addGetter(Method method, MethodCodeBody body) throws Exception {
            String methodDescriptor = Type.getMethodDescriptor(method);
            String methodName = method.getName();
            addGetter(methodName, methodDescriptor, body);
        }

        private void addGetter(String methodName, String methodDescriptor, MethodCodeBody body) throws Exception {
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDescriptor, null, new String[0]);
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
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(),
                            "getAsDynamicObject", getAsDynamicObjectDesc);

                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
                    String getPropertyDesc = Type.getMethodDescriptor(DynamicObject.class.getDeclaredMethod(
                            "getProperty", String.class));
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(),
                            "getProperty", getPropertyDesc);

                    // END
                }
            });

            // GENERATE public boolean hasProperty(String name) { return getAsDynamicObject().hasProperty(name) }

            String methodDescriptor = Type.getMethodDescriptor(Type.getType(Boolean.TYPE), Type.getType(String.class));
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "hasProperty", methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().hasProperty(name);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            String getAsDynamicObjectDesc = Type.getMethodDescriptor(DynamicObjectAware.class.getDeclaredMethod(
                    "getAsDynamicObject"));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(),
                    "getAsDynamicObject", getAsDynamicObjectDesc);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            String getPropertyDesc = Type.getMethodDescriptor(DynamicObject.class.getDeclaredMethod(
                    "hasProperty", String.class));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(),
                    "hasProperty", getPropertyDesc);

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
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(),
                                    "getAsDynamicObject", getAsDynamicObjectDesc);

                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                            String setPropertyDesc = Type.getMethodDescriptor(DynamicObject.class.getDeclaredMethod(
                                    "setProperty", String.class, Object.class));
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(),
                                    "setProperty", setPropertyDesc);

                            // END
                        }
                    });

            // GENERATE public Object invokeMethod(String name, Object params) { return getAsDynamicObject().invokeMethod(name, (Object[])params); }

            addGetter(GroovyObject.class.getDeclaredMethod("invokeMethod", String.class, Object.class),
                    new MethodCodeBody() {
                        public void add(MethodVisitor methodVisitor) throws Exception {
                            String invokeMethodDesc = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class), Type.getType(Object[].class));
                            String objArrayDesc = Type.getType(Object[].class).getDescriptor();

                            // GENERATE getAsDynamicObject().invokeMethod(name, (args instanceof Object[]) ? args : new Object[] { args })

                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                            String getAsDynamicObjectDesc = Type.getMethodDescriptor(
                                    DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject"));
                            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(),
                                    "getAsDynamicObject", getAsDynamicObjectDesc);

                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);

                            // GENERATE (args instanceof Object[]) ? args : new Object[] { args }
                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                            methodVisitor.visitTypeInsn(Opcodes.INSTANCEOF, objArrayDesc);
                            Label end = new Label();
                            Label notArray = new Label();
                            methodVisitor.visitJumpInsn(Opcodes.IFEQ, notArray);

                            // Generate args
                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, objArrayDesc);
                            methodVisitor.visitJumpInsn(Opcodes.GOTO, end);

                            // Generate new Object[] { args }
                            methodVisitor.visitLabel(notArray);
                            methodVisitor.visitInsn(Opcodes.ICONST_1);
                            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, Type.getType(Object.class).getInternalName());
                            methodVisitor.visitInsn(Opcodes.DUP);
                            methodVisitor.visitInsn(Opcodes.ICONST_0);
                            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
                            methodVisitor.visitInsn(Opcodes.AASTORE);

                            methodVisitor.visitLabel(end);

                            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(),
                                    "invokeMethod", invokeMethodDesc);
                        }
                    });
        }

        public void addInjectorProperty(PropertyMetaData property) {
            // GENERATE private <type> <property-field-name>;
            String flagName = propFieldName(property);
            visitor.visitField(Opcodes.ACC_PRIVATE, flagName, Type.getDescriptor(property.getType()), null, null);
        }

        public void applyServiceInjectionToGetter(PropertyMetaData property, Method getter) throws Exception {
            // GENERATE public <type> <getter>() { if (<field> == null) { <field> = getServices().get(getClass().getDeclaredMethod(<getter-name>).getGenericReturnType()); } return <field> }

            Type serviceRegistryType = Type.getType(ServiceRegistry.class);
            Type classType = Type.getType(Class.class);
            Type methodType = Type.getType(Method.class);
            Type typeType = Type.getType(java.lang.reflect.Type.class);

            String getterName = getter.getName();
            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            Type serviceType = Type.getType(property.getType());
            String propFieldName = propFieldName(property);

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getterName, methodDescriptor, signature(getter), new String[0]);
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
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getServices", Type.getMethodDescriptor(serviceRegistryType));

            // this.getClass()
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getClass", Type.getMethodDescriptor(classType));

            // <class>.getDeclaredMethod(<getter-name>)
            methodVisitor.visitLdcInsn(getterName);
            methodVisitor.visitInsn(Opcodes.ICONST_0);
            methodVisitor.visitTypeInsn(Opcodes.ANEWARRAY, classType.getInternalName());
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classType.getInternalName(), "getDeclaredMethod", Type.getMethodDescriptor(methodType, Type.getType(String.class), Type.getType(Class[].class)));

            // <method>.getGenericReturnType()
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, methodType.getInternalName(), "getGenericReturnType", Type.getMethodDescriptor(typeType));

            // get(<type>)
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, serviceRegistryType.getInternalName(), "get", Type.getMethodDescriptor(Type.getType(Object.class), typeType));

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

        public void applyServiceInjectionToSetter(PropertyMetaData property, Method setter) throws Exception {
            // GENERATE public void <setter>(<type> value) { <field> == value }
            String methodDescriptor = Type.getMethodDescriptor(setter);
            Type serviceType = Type.getType(property.getType());
            String propFieldName = propFieldName(property);

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), methodDescriptor, signature(setter), new String[0]);
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
            return String.format("__%s__", property.getName());
        }

        public void applyConventionMappingToGetter(PropertyMetaData property, Method getter) throws Exception {
            // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', __<prop>__); }
            String flagName = propFieldName(property);
            String getterName = getter.getName();

            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getterName, methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionAwareType.getInternalName(),
                    "getConventionMapping", Type.getMethodDescriptor(conventionMappingType));

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getterName,
                    methodDescriptor);

            Type boxedType = null;
            if (getter.getReturnType().isPrimitive()) {
                // Box value
                boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(getter.getReturnType()));
                String valueOfMethodDescriptor = Type.getMethodDescriptor(boxedType, returnType);
                methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, boxedType.getInternalName(), "valueOf", valueOfMethodDescriptor);
            }

            methodVisitor.visitLdcInsn(property.getName());

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), flagName,
                    Type.BOOLEAN_TYPE.getDescriptor());

            String getConventionValueDesc = Type.getMethodDescriptor(ConventionMapping.class.getMethod(
                    "getConventionValue", Object.class, String.class, Boolean.TYPE));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionMappingType.getInternalName(),
                    "getConventionValue", getConventionValueDesc);

            if (getter.getReturnType().isPrimitive()) {
                // Unbox value
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, boxedType.getInternalName());
                String valueMethodDescriptor = Type.getMethodDescriptor(returnType);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, boxedType.getInternalName(), getter.getReturnType().getName() + "Value", valueMethodDescriptor);
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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), setterDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE super.<setter>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), setter.getName(), setterDescriptor);

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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, property.getName(), setMethodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE <setter>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), setter.getName(), setterDescriptor);

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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE super.<propName>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), method.getName(), methodDescriptor);

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
            Type actionImplType = Type.getType(ClosureBackedAction.class);
            Type closureType = Type.getType(Closure.class);
            Type returnType = Type.getType(method.getReturnType());

            Type[] originalParameterTypes = CollectionUtils.collectArray(method.getParameterTypes(), Type.class, new Transformer<Type, Class>() {
                public Type transform(Class clazz) {
                    return Type.getType(clazz);
                }
            });
            int numParams = originalParameterTypes.length;
            Type[] closurisedParameterTypes = new Type[numParams];
            System.arraycopy(originalParameterTypes, 0, closurisedParameterTypes, 0, numParams);
            closurisedParameterTypes[numParams - 1] = closureType;

            String methodDescriptor = Type.getMethodDescriptor(returnType, closurisedParameterTypes);

            // GENERATE public <return type> <method>(Closure v) { return <method>(…, new ClosureBackedAction(v)); }
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE <method>(…, new ClosureBackedAction(v));
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);

            for (int stackVar = 1; stackVar < numParams; ++stackVar) {
                methodVisitor.visitVarInsn(closurisedParameterTypes[stackVar - 1].getOpcode(Opcodes.ILOAD), stackVar);
            }

            // GENERATE new ClosureBackedAction(v);
            methodVisitor.visitTypeInsn(Opcodes.NEW, actionImplType.getInternalName());
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, numParams);
            String constuctorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, closureType);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, actionImplType.getInternalName(), "<init>", constuctorDescriptor);


            methodDescriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), originalParameterTypes);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), method.getName(), methodDescriptor);

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public Class<? extends T> generate() {
            visitor.visitEnd();

            byte[] bytecode = visitor.toByteArray();
            return DEFINE_CLASS_METHOD.invoke(type.getClassLoader(), typeName, bytecode, 0, bytecode.length);
        }
    }

    private interface MethodCodeBody {
        void add(MethodVisitor visitor) throws Exception;
    }

    public static class MixInExtensibleDynamicObject extends ExtensibleDynamicObject {

        public MixInExtensibleDynamicObject(Object delegateObject, DynamicObject dynamicObject) {
            super(delegateObject, wrap(delegateObject, dynamicObject), ThreadGlobalInstantiator.getOrCreate());
        }

        private static AbstractDynamicObject wrap(Object delegateObject, DynamicObject dynamicObject) {
            if (dynamicObject != null) {
                return (AbstractDynamicObject) dynamicObject;
            }
            return new BeanDynamicObject(delegateObject);
        }
    }

}
