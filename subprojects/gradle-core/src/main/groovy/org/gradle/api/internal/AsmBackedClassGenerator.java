/*
 * Copyright 2009 the original author or authors.
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

import groovy.lang.MetaBeanProperty;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.util.ReflectionUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AsmBackedClassGenerator extends AbstractClassGenerator {
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
        private final Type conventionAwareType = Type.getType(IConventionAware.class);
        private final Type dynamicObjectAwareType = Type.getType(DynamicObjectAware.class);
        private final Type conventionMappingType = Type.getType(ConventionMapping.class);
        private boolean dynamicAware;
        private final Type defaultConventionType = Type.getType(DefaultConvention.class);
        private final Type conventionType = Type.getType(Convention.class);

        private ClassBuilderImpl(Class<T> type) {
            this.type = type;

            visitor = new ClassWriter(true);
            typeName = type.getName() + "_Decorated";
            generatedType = Type.getType("L" + typeName.replaceAll("\\.", "/") + ";");
            superclassType = Type.getType(type);
        }

        public void startClass(boolean isConventionAware, boolean isDynamicAware) {
            dynamicAware = isDynamicAware;
            List<String> interfaceTypes = new ArrayList<String>();
            if (isConventionAware) {
                interfaceTypes.add(conventionAwareType.getInternalName());
            }
            if (isDynamicAware) {
                interfaceTypes.add(dynamicObjectAwareType.getInternalName());
            }

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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", methodDescriptor, null,
                    new String[0]);
            methodVisitor.visitCode();

            // super(p0 .. pn)
            for (int i = 0; i <= constructor.getParameterTypes().length; i++) {
                methodVisitor.visitVarInsn(Opcodes.ALOAD, i);
            }
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>",
                    methodDescriptor);

            if (initDynamicObjectHelper != null) {
                initDynamicObjectHelper.add(methodVisitor);
            }
            if (initConventionAwareHelper != null) {
                initConventionAwareHelper.add(methodVisitor);
            }

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void mixInDynamicAware() throws Exception {
            final Type helperType = Type.getType(DynamicObjectHelper.class);

            // GENERATE private DynamicObjectHelper dynamicObjectHelper = new DynamicObjectHelper(this, new DefaultConvention())

            final String fieldSignature = "L" + DynamicObjectHelper.class.getName().replaceAll("\\.", "/") + ";";
            visitor.visitField(Opcodes.ACC_PRIVATE, "dynamicObjectHelper", fieldSignature, null, null);
            initDynamicObjectHelper = new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    String conventionConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[0]);
                    String helperTypeConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{
                            Type.getType(Object.class), conventionType
                    });

                    // GENERATE dynamicObjectHelper = new DynamicObjectHelper(this, new DefaultConvention())

                    visitor.visitVarInsn(Opcodes.ALOAD, 0);

                    // GENERATE new DynamicObjectHelper(this, new DefaultConvention())
                    visitor.visitTypeInsn(Opcodes.NEW, helperType.getInternalName());
                    visitor.visitInsn(Opcodes.DUP);

                    // GENERATE this
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    // END

                    // GENERATE new DefaultConvention()
                    visitor.visitTypeInsn(Opcodes.NEW, defaultConventionType.getInternalName());
                    visitor.visitInsn(Opcodes.DUP);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, defaultConventionType.getInternalName(), "<init>",
                            conventionConstructorDesc);
                    // END

                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, helperType.getInternalName(), "<init>",
                            helperTypeConstructorDesc);
                    // END

                    visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "dynamicObjectHelper",
                            fieldSignature);
                    // END
                }
            };

            // END

            // GENERATE public Convention getConvention() { return dynamicObjectHelper.getConvention(); }

            addGetter(DynamicObjectAware.class.getDeclaredMethod("getConvention"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {

                    // GENERATE dynamicObjectHelper.getConvention()

                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "dynamicObjectHelper",
                            fieldSignature);
                    String getterDescriptor = Type.getMethodDescriptor(DynamicObjectHelper.class.getDeclaredMethod(
                            "getConvention"));
                    visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, helperType.getInternalName(), "getConvention",
                            getterDescriptor);
                }
            });

            // END

            // GENERATE public DynamicObject.getAsDynamicObject() { return dynamicObjectHelper; }

            addGetter(DynamicObjectAware.class.getDeclaredMethod("getAsDynamicObject"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) {

                    // GENERATE dynamicObjectHelper

                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "dynamicObjectHelper",
                            fieldSignature);
                }
            });

            // END

            // GENERATE public void setConvention(Convention c) { dynamicObjectHelper.setConvention(c); getConventionMapping().setConvention(c); }

            addSetter(DynamicObjectAware.class.getDeclaredMethod("setConvention", Convention.class),
                    new MethodCodeBody() {
                        public void add(MethodVisitor visitor) {
                            String setConventionDesc = Type.getMethodDescriptor(Type.VOID_TYPE,
                                    new Type[]{conventionType});

                            visitor.visitVarInsn(Opcodes.ALOAD, 0);
                            visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(),
                                    "dynamicObjectHelper", fieldSignature);

                            visitor.visitVarInsn(Opcodes.ALOAD, 1);
                            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, helperType.getInternalName(),
                                    "setConvention", setConventionDesc);

                            visitor.visitVarInsn(Opcodes.ALOAD, 0);
                            visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(),
                                    "getConventionMapping", Type.getMethodDescriptor(conventionMappingType,
                                            new Type[0]));

                            visitor.visitVarInsn(Opcodes.ALOAD, 1);
                            visitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionMappingType.getInternalName(),
                                    "setConvention", setConventionDesc);
                        }
                    });

            // END
        }

        public void mixInConventionAware() throws Exception {
            // GENERATE private ConventionMapping mapping = new ConventionAwareHelper(this, getConvention())

            final String mappingFieldSignature = "L" + ConventionMapping.class.getName().replaceAll("\\.", "/") + ";";
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

                    if (dynamicAware) {
                        // GENERATE getConvention()

                        visitor.visitVarInsn(Opcodes.ALOAD, 0);
                        visitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getConvention",
                                getConventionDesc);

                        // END
                    } else {
                        // GENERATE new DefaultConvention()

                        visitor.visitTypeInsn(Opcodes.NEW, defaultConventionType.getInternalName());
                        visitor.visitInsn(Opcodes.DUP);
                        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, defaultConventionType.getInternalName(),
                                "<init>", "()V");

                        // END
                    }

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

            // GENERATE public ConventionMapping getConventionMapping() { return mapping; }

            addGetter(IConventionAware.class.getDeclaredMethod("getConventionMapping"), new MethodCodeBody() {
                public void add(MethodVisitor visitor) {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), "mapping",
                            mappingFieldSignature);
                }
            });

            // GENERATE public void setConventionMapping(ConventionMapping m) { mapping = m; }

            addSetter(IConventionAware.class.getDeclaredMethod("setConventionMapping", ConventionMapping.class),
                    new MethodCodeBody() {
                        public void add(MethodVisitor visitor) {
                            visitor.visitVarInsn(Opcodes.ALOAD, 0);
                            visitor.visitVarInsn(Opcodes.ALOAD, 1);
                            visitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), "mapping",
                                    mappingFieldSignature);
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
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), methodDescriptor,
                    null, new String[0]);
            methodVisitor.visitCode();
            body.add(methodVisitor);
            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addDynamicMethods() throws Exception {
            Type dynamicObjectType = Type.getType(DynamicObject.class);

            // GENERATE public void propertyMissing(String name, Object value) { getAsDynamicObject().setProperty(name, value); }

            String propertyMissingDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{
                    Type.getType(String.class), Type.getType(Object.class)
            });
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "propertyMissing",
                    propertyMissingDesc, null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().setProperty(name, value)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            String getAsDynamicObjectDesc = Type.getMethodDescriptor(DynamicObjectAware.class.getDeclaredMethod(
                    "getAsDynamicObject"));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject",
                    getAsDynamicObjectDesc);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(), "setProperty",
                    propertyMissingDesc);

            // END

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // END

            // GENERATE public void propertyMissing(String name) { return getAsDynamicObject().getProperty(name); }

            propertyMissingDesc = Type.getMethodDescriptor(Type.getType(Object.class), new Type[]{
                    Type.getType(String.class)
            });

            methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "propertyMissing", propertyMissingDesc, null,
                    new String[0]);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().getProperty(name);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject",
                    getAsDynamicObjectDesc);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(), "getProperty",
                    propertyMissingDesc);

            // END

            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // END

            // GENERATE public Object methodMissing(String name, Object args) { getAsDynamicObject().invokeMethod(name, (Object[])args); }

            String methodMissingDesc = Type.getMethodDescriptor(Type.getType(Object.class), new Type[]{
                    Type.getType(String.class), Type.getType(Object.class)
            });
            String invokeMethodDesc = Type.getMethodDescriptor(Type.getType(Object.class), new Type[]{
                    Type.getType(String.class), Type.getType(Object[].class)
            });
            methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "methodMissing", methodMissingDesc, null,
                    new String[0]);
            methodVisitor.visitCode();

            // GENERATE getAsDynamicObject().invokeMethod(name, (Object[])args)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedType.getInternalName(), "getAsDynamicObject",
                    getAsDynamicObjectDesc);

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getType(Object[].class).getDescriptor());
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, dynamicObjectType.getInternalName(), "invokeMethod",
                    invokeMethodDesc);

            // END

            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();

            // END
        }

        public void addGetter(MetaBeanProperty property) throws Exception {
            // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>'); }

            Type returnType = Type.getType(property.getGetter().getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType, new Type[0]);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, property.getGetter().getName(),
                    methodDescriptor, null, new String[0]);
            methodVisitor.visitCode();

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionAwareType.getInternalName(),
                    "getConventionMapping", Type.getMethodDescriptor(conventionMappingType, new Type[0]));

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(),
                    property.getGetter().getName(), methodDescriptor);

            methodVisitor.visitLdcInsn(property.getName());

            String getConventionValueDesc = Type.getMethodDescriptor(ConventionMapping.class.getMethod(
                    "getConventionValue", Object.class, String.class));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionMappingType.getInternalName(),
                    "getConventionValue", getConventionValueDesc);

            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST,
                    property.getGetter().getReturnType().isArray() ? "[" + returnType.getElementType().getDescriptor()
                            : returnType.getInternalName());

            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public Class<? extends T> generate() {
            visitor.visitEnd();

            byte[] bytecode = visitor.toByteArray();
            return (Class<T>) ReflectionUtil.invoke(type.getClassLoader(), "defineClass", new Object[]{
                    typeName, bytecode, 0, bytecode.length
            });
        }
    }

    private interface MethodCodeBody {
        void add(MethodVisitor visitor) throws Exception;
    }
}
