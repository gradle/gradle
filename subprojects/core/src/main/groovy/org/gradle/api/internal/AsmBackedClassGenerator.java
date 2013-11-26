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
import org.gradle.api.NonExtensible;
import org.gradle.api.Transformer;
import org.gradle.api.plugins.Convention;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.CollectionUtils;
import org.gradle.internal.reflect.JavaMethod;
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
    protected <T> ClassBuilder<T> start(Class<T> type) {
        return new ClassBuilderImpl<T>(type);
    }

    private static class ClassBuilderImpl<T> implements ClassBuilder<T> {
        private final ClassWriter visitor;
        private final Class<T> type;
        private final String typeName;
        private final Type generatedType;
        private final Type superclassType;
        private MethodCodeBody initDynamicObjectHelper;
        private MethodCodeBody initConventionAwareHelper;
        private MethodCodeBody initMetaClass;
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

        private final boolean extensible;

        private ClassBuilderImpl(Class<T> type) {
            this.type = type;

            visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            typeName = type.getName() + "_Decorated";
            generatedType = Type.getType("L" + typeName.replaceAll("\\.", "/") + ";");
            superclassType = Type.getType(type);

            extensible = JavaReflectionUtil.getAnnotation(type, NonExtensible.class) == null;
        }

        public void startClass(boolean isConventionAware) {
            List<String> interfaceTypes = new ArrayList<String>();
            if (isConventionAware && extensible) {
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

            String signature = signature(constructor);

            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", methodDescriptor, signature,
                    new String[0]);

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

            if (initDynamicObjectHelper != null) {
                initDynamicObjectHelper.add(methodVisitor);
            }
            if (initConventionAwareHelper != null) {
                initConventionAwareHelper.add(methodVisitor);
            }
            if (initMetaClass != null) {
                initMetaClass.add(methodVisitor);
            }

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        /**
         * Generates the signature for the given constructor
         */
        private String signature(Constructor<?> constructor) {
            StringBuilder builder = new StringBuilder();
            if (constructor.getTypeParameters().length > 0) {
                builder.append('<');
                for (TypeVariable<?> typeVariable : constructor.getTypeParameters()) {
                    builder.append(typeVariable.getName());
                    for (java.lang.reflect.Type bound : typeVariable.getBounds()) {
                        builder.append(':');
                        visitType(bound, builder);
                    }
                }
                builder.append('>');
            }
            builder.append('(');
            for (java.lang.reflect.Type paramType : constructor.getGenericParameterTypes()) {
                visitType(paramType, builder);
            }
            builder.append(")V");
            for (java.lang.reflect.Type exceptionType : constructor.getGenericExceptionTypes()) {
                builder.append('^');
                visitType(exceptionType, builder);
            }
            return builder.toString();
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

            // GENERATE private MixInExtensibleDynamicObject dynamicObjectHelper = new MixInExtensibleDynamicObject(this, super.getAsDynamicObject())

            Class<?> extensibleObjectFieldType = extensible ? MixInExtensibleDynamicObject.class : BeanDynamicObject.class;
            final String fieldSignature = Type.getDescriptor(extensibleObjectFieldType);
            visitor.visitField(Opcodes.ACC_PRIVATE, "dynamicObjectHelper", fieldSignature, null, null);
            initDynamicObjectHelper = new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    generateCreateDynamicObject(visitor);
                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "dynamicObjectHelper",
                            fieldSignature);
                    // END
                }
            };

            // END

            if (extensible) {
                // GENERATE public Convention getConvention() { return dynamicObjectHelper.getConvention(); }

                addGetter(HasConvention.class.getDeclaredMethod("getConvention"), new MethodCodeBody() {
                    public void add(MethodVisitor visitor) throws Exception {

                        // GENERATE dynamicObjectHelper.getConvention()

                        visitor.visitVarInsn(Opcodes.ALOAD, 0);
                        visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "dynamicObjectHelper",
                                fieldSignature);
                        String getterDescriptor = Type.getMethodDescriptor(ExtensibleDynamicObject.class.getDeclaredMethod(
                                "getConvention"));
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
                        String getterDescriptor = Type.getMethodDescriptor(ExtensibleDynamicObject.class.getDeclaredMethod(
                                "getConvention"));
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention",
                                getterDescriptor);
                    }
                });
            }

            // END

            // GENERATE public DynamicObject.getAsDynamicObject() { return dynamicObjectHelper; }

            addGetter(DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "dynamicObjectHelper",
                            fieldSignature);
                }
            });

            // END
        }

        private void generateCreateDynamicObject(MethodVisitor visitor) {
            if (extensible) {

                String helperTypeConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{
                        Type.getType(Object.class), dynamicObjectType
                });

                // GENERATE dynamicObjectHelper = new MixInExtensibleDynamicObject(this, super.getAsDynamicObject())

                visitor.visitVarInsn(Opcodes.ALOAD, 0);

                // GENERATE new MixInExtensibleDynamicObject(this, super.getAsDynamicObject())
                visitor.visitTypeInsn(Opcodes.NEW, extensibleDynamicObjectHelperType.getInternalName());
                visitor.visitInsn(Opcodes.DUP);

                visitor.visitVarInsn(Opcodes.ALOAD, 0);

                boolean useInheritedDynamicObject = GroovySystem.getMetaClassRegistry().getMetaClass(type).pickMethod("getAsDynamicObject", new Class[0]) != null;

                if (useInheritedDynamicObject) {
                    // GENERATE super.getAsDynamicObject()
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getType(type).getInternalName(),
                            "getAsDynamicObject", Type.getMethodDescriptor(dynamicObjectType, new Type[0]));
                } else {
                    // GENERATE null
                    visitor.visitInsn(Opcodes.ACONST_NULL);
                }

                visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, extensibleDynamicObjectHelperType.getInternalName(), "<init>",
                        helperTypeConstructorDesc);
                // END
            } else {
                String helperTypeConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{
                        Type.getType(Object.class)
                });

                // GENERATE new BeanDynamicObject(this)

                visitor.visitVarInsn(Opcodes.ALOAD, 0);

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
            final String getConventionDesc = Type.getMethodDescriptor(conventionType, new Type[0]);

            visitor.visitField(Opcodes.ACC_PRIVATE, "mapping", mappingFieldSignature, null, null);

            initConventionAwareHelper = new MethodCodeBody() {
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

                    String constructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{
                            conventionAwareType, conventionType
                    });
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, helperType.getInternalName(), "<init>",
                            constructorDesc);

                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "mapping",
                            mappingFieldSignature);

                    // END
                }
            };

            // END

            // GENERATE public ConventionMapping getConventionMapping() { if (mapping != null) { return mapping; } else { return new ConventionAwareHelper(this); } }
            // the null check is for when getConventionMapping() is called by a superclass constructor (eg when the constructor calls a getter method)

            addGetter(IConventionAware.class.getDeclaredMethod("getConventionMapping"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) {
                    // GENERATE if (mapping != null) {... }
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "mapping",
                            mappingFieldSignature);
                    visitor.visitInsn(Opcodes.DUP);
                    Label nullBranch = new Label();
                    visitor.visitJumpInsn(Opcodes.IFNULL, nullBranch);
                    visitor.visitInsn(Opcodes.ARETURN);
                    // GENERATE else { return new ConventionAwareHelper(this); }
                    visitor.visitLabel(nullBranch);
                    Type conventionAwareHelperType = Type.getType(ConventionAwareHelper.class);
                    String constructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{Type.getType(IConventionAware.class)});
                    visitor.visitTypeInsn(Opcodes.NEW, conventionAwareHelperType.getInternalName());
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, conventionAwareHelperType.getInternalName(), "<init>", constructorDesc);
                    visitor.visitInsn(Opcodes.ARETURN);
                }
            });
        }

        public void mixInGroovyObject() throws Exception {

            // GENERATE private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass())

            final String metaClassFieldSignature = Type.getDescriptor(MetaClass.class);
            visitor.visitField(Opcodes.ACC_PRIVATE, "metaClass", metaClassFieldSignature, null, null);

            initMetaClass = new MethodCodeBody() {
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

                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "metaClass",
                            metaClassFieldSignature);
                }
            };

            // GENERATE public MetaClass getMetaClass() { return metaClass }

            addGetter(GroovyObject.class.getDeclaredMethod("getMetaClass"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "metaClass",
                            metaClassFieldSignature);
                }
            });

            // GENERATE public void setMetaClass(MetaClass class) { this.metaClass = class; }

            addSetter(GroovyObject.class.getDeclaredMethod("setMetaClass", MetaClass.class), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitVarInsn(Opcodes.ALOAD, 1);
                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "metaClass",
                            metaClassFieldSignature);
                }
            });
        }

        private void addSetter(Method method, MethodCodeBody body) throws Exception {
            String methodDescriptor = Type.getMethodDescriptor(method);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor,
                    null, new String[0]);
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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, methodName, methodDescriptor,
                    null, new String[0]);
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

            String methodDescriptor = Type.getMethodDescriptor(Type.getType(Boolean.TYPE), new Type[]{Type.getType(String.class)});
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
                            String invokeMethodDesc = Type.getMethodDescriptor(Type.getType(Object.class), new Type[]{
                                    Type.getType(String.class), Type.getType(Object[].class)
                            });
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
//                            methodVisitor.visitInsn(Opcodes.ACONST_NULL);
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

        public void addGetter(MetaBeanProperty property) throws Exception {
            if (!extensible) {
                return;
            }
            MetaMethod getter = property.getGetter();

            // GENERATE private boolean <prop>Set;

            String flagName = String.format("%sSet", property.getName());
            visitor.visitField(Opcodes.ACC_PRIVATE, flagName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);

            addConventionGetter(getter.getName(), flagName, property);

            String getterName = getter.getName();
            Class<?> returnType = getter.getReturnType();

            // If it's a boolean property, there can be get or is type variants.
            // If this class has both, decorate both.
            if (returnType.equals(Boolean.TYPE)) {
                boolean getterIsIsMethod = getterName.startsWith("is");
                String propertyNameComponent = getterName.substring(getterIsIsMethod ? 2 : 3);
                String alternativeGetterName = String.format("%s%s", getterIsIsMethod ? "get" : "is", propertyNameComponent);

                try {
                    type.getMethod(alternativeGetterName);
                    addConventionGetter(alternativeGetterName, flagName, property);
                } catch (NoSuchMethodException e) {
                    // ignore, no method to override
                }
            }
        }

        private void addConventionGetter(String getterName, String flagName, MetaBeanProperty property) throws Exception {
            // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', <prop>Set); }
            MetaMethod getter = property.getGetter();

            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType, new Type[0]);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getterName, methodDescriptor,
                    null, new String[0]);
            methodVisitor.visitCode();

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionAwareType.getInternalName(),
                    "getConventionMapping", Type.getMethodDescriptor(conventionMappingType, new Type[0]));

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getterName,
                    methodDescriptor);

            Type boxedType = null;
            if (getter.getReturnType().isPrimitive()) {
                // Box value
                boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(getter.getReturnType()));
                String valueOfMethodDescriptor = Type.getMethodDescriptor(boxedType, new Type[]{returnType});
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
                String valueMethodDescriptor = Type.getMethodDescriptor(returnType, new Type[0]);
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

        public void addSetter(MetaBeanProperty property) throws Exception {
            if (!extensible) {
                return;
            }
            MetaMethod setter = property.getSetter();

            // GENERATE public <return-type> <setter>(<type> v) { <return-type> v = super.<setter>(v); <prop>Set = true; return v; }

            Type paramType = Type.getType(setter.getParameterTypes()[0].getTheClass());
            Type returnType = Type.getType(setter.getReturnType());
            String setterDescriptor = Type.getMethodDescriptor(returnType, new Type[]{paramType});
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), setterDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE super.<setter>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), setter.getName(), setterDescriptor);

            // END

            // GENERATE <prop>Set = true

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitLdcInsn(true);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), String.format("%sSet",
                    property.getName()), Type.BOOLEAN_TYPE.getDescriptor());

            // END

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addSetMethod(MetaBeanProperty property) throws Exception {
            MetaMethod setter = property.getSetter();
            Type paramType = Type.getType(setter.getParameterTypes()[0].getTheClass());
            Type returnType = Type.getType(setter.getReturnType());
            String setterDescriptor = Type.getMethodDescriptor(returnType, new Type[]{paramType});

            // GENERATE public void <propName>(<type> v) { <setter>(v) }
            String setMethodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{paramType});
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

        public void overrideSetMethod(MetaBeanProperty property, MetaMethod metaMethod) throws Exception {
            if (metaMethod.getParameterTypes().length != 1) {
                throw new IllegalArgumentException("Can only override set methods that take one argument: " + metaMethod.toString());
            } else if (!extensible) {
                return;
            }
            Type paramType = Type.getType(metaMethod.getParameterTypes()[0].getTheClass());
            Type returnType = Type.getType(metaMethod.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType, new Type[]{paramType});

            // GENERATE public <returnType> <propName>(<type> v) { val = super.<propName>(v); <prop>Set = true; return val; }
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, metaMethod.getName(), methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE super.<propName>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(paramType.getOpcode(Opcodes.ILOAD), 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), metaMethod.getName(), methodDescriptor);

            // GENERATE <prop>Set = true

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitLdcInsn(true);
            methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), String.format("%sSet",
                    property.getName()), Type.BOOLEAN_TYPE.getDescriptor());

            // END

            methodVisitor.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addActionMethod(MetaMethod method) throws Exception {
            Type actionImplType = Type.getType(ClosureBackedAction.class);
            Type closureType = Type.getType(Closure.class);

            Type[] originalParameterTypes = CollectionUtils.collectArray(method.getNativeParameterTypes(), Type.class, new Transformer<Type, Class>() {
                public Type transform(Class clazz) {
                    return Type.getType(clazz);
                }
            });
            int numParams = originalParameterTypes.length;
            Type[] closurisedParameterTypes = new Type[numParams];
            System.arraycopy(originalParameterTypes, 0, closurisedParameterTypes, 0, numParams);
            closurisedParameterTypes[numParams - 1] = closureType;

            String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, closurisedParameterTypes);

            // GENERATE public void <method>(Closure v) { <method>(…, new ClosureBackedAction(v)); }
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
            String constuctorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{closureType});
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, actionImplType.getInternalName(), "<init>", constuctorDescriptor);


            methodDescriptor = Type.getMethodDescriptor(Type.getType(method.getReturnType()), originalParameterTypes);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), method.getName(), methodDescriptor);

            methodVisitor.visitInsn(Opcodes.RETURN);
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
