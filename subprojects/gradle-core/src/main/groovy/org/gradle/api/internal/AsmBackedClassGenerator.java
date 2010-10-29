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
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.util.ReflectionUtil;
import org.objectweb.asm.*;

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
        private MethodCodeBody initMetaClass;
        private final Type conventionAwareType = Type.getType(IConventionAware.class);
        private final Type dynamicObjectAwareType = Type.getType(DynamicObjectAware.class);
        private final Type dynamicObjectType = Type.getType(DynamicObject.class);
        private final Type conventionMappingType = Type.getType(ConventionMapping.class);
        private final Type groovyObjectType = Type.getType(GroovyObject.class);
        private boolean dynamicAware;
        private final Type defaultConventionType = Type.getType(DefaultConvention.class);
        private final Type conventionType = Type.getType(Convention.class);

        private ClassBuilderImpl(Class<T> type) {
            this.type = type;

            visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS);
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
            if (isDynamicAware) {
                interfaceTypes.add(groovyObjectType.getInternalName());
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
            if (initMetaClass != null) {
                initMetaClass.add(methodVisitor);
            }

            methodVisitor.visitInsn(Opcodes.RETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void mixInDynamicAware() throws Exception {
            final Type helperType = Type.getType(MixInDynamicObject.class);

            // GENERATE private MixInDynamicObject dynamicObjectHelper = new MixInDynamicObject(this, super.getAsDynamicObject())

            final String fieldSignature = "L" + MixInDynamicObject.class.getName().replaceAll("\\.", "/") + ";";
            visitor.visitField(Opcodes.ACC_PRIVATE, "dynamicObjectHelper", fieldSignature, null, null);
            initDynamicObjectHelper = new MethodCodeBody() {
                public void add(MethodVisitor visitor) throws Exception {
                    String helperTypeConstructorDesc = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[]{
                            Type.getType(Object.class), dynamicObjectType
                    });

                    // GENERATE dynamicObjectHelper = new MixInDynamicObject(this, super.getAsDynamicObject())

                    visitor.visitVarInsn(Opcodes.ALOAD, 0);

                    // GENERATE new DynamicObjectHelper(this, super.getAsDynamicObject())
                    visitor.visitTypeInsn(Opcodes.NEW, helperType.getInternalName());
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

        public void mixInGroovyObject() throws Exception {

            // GENERATE private MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(getClass())

            final String metaClassFieldSignature = "L" + MetaClass.class.getName().replaceAll("\\.", "/") + ";";
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
                            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST,  objArrayDesc);
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
            MetaMethod getter = property.getGetter();

            // GENERATE private boolean <prop>Set;

            String flagName = String.format("%sSet", property.getName());
            visitor.visitField(Opcodes.ACC_PRIVATE, flagName, Type.BOOLEAN_TYPE.getDescriptor(), null, null);

            // GENERATE public <type> <getter>() { return (<type>)getConventionMapping().getConventionValue(super.<getter>(), '<prop>', <prop>Set); }

            Type returnType = Type.getType(getter.getReturnType());
            String methodDescriptor = Type.getMethodDescriptor(returnType, new Type[0]);
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, getter.getName(), methodDescriptor,
                    null, new String[0]);
            methodVisitor.visitCode();

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionAwareType.getInternalName(),
                    "getConventionMapping", Type.getMethodDescriptor(conventionMappingType, new Type[0]));

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), getter.getName(),
                    methodDescriptor);

            methodVisitor.visitLdcInsn(property.getName());
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), flagName,
                    Type.BOOLEAN_TYPE.getDescriptor());

            String getConventionValueDesc = Type.getMethodDescriptor(ConventionMapping.class.getMethod(
                    "getConventionValue", Object.class, String.class, Boolean.TYPE));
            methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, conventionMappingType.getInternalName(),
                    "getConventionValue", getConventionValueDesc);

            methodVisitor.visitTypeInsn(Opcodes.CHECKCAST,
                    getter.getReturnType().isArray() ? "[" + returnType.getElementType().getDescriptor()
                            : returnType.getInternalName());

            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        public void addSetter(MetaBeanProperty property) throws Exception {
            MetaMethod setter = property.getSetter();

            // GENERATE public <return-type> <setter>(<type> v) { <return-type> v = super.<setter>(v); <prop>Set = true; return v; }

            Type paramType = Type.getType(setter.getParameterTypes()[0].getTheClass());
            Type returnType = Type.getType(setter.getReturnType());
            boolean isVoid = setter.getReturnType().equals(Void.TYPE);
            String methodDescriptor = Type.getMethodDescriptor(returnType, new Type[]{paramType});
            MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, setter.getName(), methodDescriptor,
                    null, new String[0]);
            methodVisitor.visitCode();

            // GENERATE super.<setter>(v)

            methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
            methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);

            methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), setter.getName(),
                    methodDescriptor);

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

    public static class MixInDynamicObject extends DynamicObjectHelper {
        public MixInDynamicObject(Object delegateObject, DynamicObject dynamicObject) {
            super(wrap(delegateObject, dynamicObject), new DefaultConvention());
        }

        private static AbstractDynamicObject wrap(Object delegateObject, DynamicObject dynamicObject) {
            if (dynamicObject != null) {
                return (AbstractDynamicObject) dynamicObject;
            }
            return new BeanDynamicObject(delegateObject);
        }
    }
}
