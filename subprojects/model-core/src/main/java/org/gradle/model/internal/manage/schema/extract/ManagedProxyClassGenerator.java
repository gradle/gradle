/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.*;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.objectweb.asm.*;

//CHECKSTYLE:OFF This import is needed to override wildcard import of of org.gradle.internal.reflect.NoSuchMethodException
import java.lang.NoSuchMethodException;
//CHECKSTYLE:ON
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

public class ManagedProxyClassGenerator {
    /*
        Note: there is deliberately no internal synchronizing or caching at this level.

        Class generation should always be performed behind a ModelSchemaCache, by way of DefaultModelSchemaStore.
        The generated class is then attached to the schema object.
        This allows us to avoid yet another weak class based cache, and importantly having to acquire a lock to instantiate an implementation.
     */

    private static final JavaMethod<ClassLoader, ?> DEFINE_CLASS_METHOD = JavaReflectionUtil.method(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);

    private static final String STATE_FIELD_NAME = "$state";
    private static final String CAN_CALL_SETTERS_FIELD_NAME = "$canCallSetters";
    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final String CONCRETE_SIGNATURE = null;
    private static final String[] NO_EXCEPTIONS = new String[0];
    private static final String STATE_SET_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Object.class));
    private static final String MANAGED_INSTANCE_TYPE = Type.getInternalName(ManagedInstance.class);
    private static final Type MODEL_ELEMENT_STATE_TYPE = Type.getType(ModelElementState.class);
    private static final String CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, MODEL_ELEMENT_STATE_TYPE);
    private static final String TO_STRING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(String.class));
    private static final String GET_BACKING_NODE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(MutableModelNode.class));
    private static final String GET_PROPERTY_MISSING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class));
    private static final String MISSING_PROPERTY_EXCEPTION_TYPE = Type.getInternalName(MissingPropertyException.class);
    private static final String CLASS_TYPE = Type.getInternalName(Class.class);
    private static final String FOR_NAME_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Class.class), Type.getType(String.class));
    private static final String OBJECT_ARRAY_TYPE = Type.getInternalName(Object[].class);
    private static final String MISSING_METHOD_EXCEPTION_TYPE = Type.getInternalName(MissingMethodException.class);
    private static final String MISSING_PROPERTY_CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Class.class));
    private static final String METHOD_MISSING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class), Type.getType(Object.class));
    private static final String SET_PROPERTY_MISSING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class), Type.getType(Object.class));
    private static final String MISSING_METHOD_EXCEPTION_CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Class.class), Type.getType(Object[].class));

    /**
     * Generates an implementation of the given managed type.
     * <p>
     * The generated class will implement/extend the managed type and will:
     * <ul>
     *     <li>provide implementations for abstract getters and setters</li>
     *     <li>provide a `toString()` implementation</li>
     *     <li>mix-in implementation of {@link ManagedInstance}</li>
     *     <li>provide a constructor that accepts a {@link ModelElementState}, which will be used to implement the above.</li>
     * </ul>
     */
    public <T> Class<? extends T> generate(Class<T> managedTypeClass) {
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        String generatedTypeName = managedTypeClass.getName() + "_Impl";
        Type generatedType = Type.getType("L" + generatedTypeName.replaceAll("\\.", "/") + ";");

        Class<?> superclass;
        List<String> interfaceInternalNames;
        if (managedTypeClass.isInterface()) {
            superclass = Object.class;
            interfaceInternalNames = ImmutableList.of(Type.getInternalName(managedTypeClass), MANAGED_INSTANCE_TYPE);
        } else {
            superclass = managedTypeClass;
            interfaceInternalNames = ImmutableList.of(MANAGED_INSTANCE_TYPE);
        }

        generateProxyClass(visitor, managedTypeClass, interfaceInternalNames, generatedType, Type.getType(superclass));

        return defineClass(visitor, managedTypeClass.getClassLoader(), generatedTypeName);
    }

    private <T> Class<? extends T> defineClass(ClassWriter visitor, ClassLoader classLoader, String generatedTypeName) {
        byte[] bytecode = visitor.toByteArray();
        return Cast.uncheckedCast(DEFINE_CLASS_METHOD.invoke(classLoader, generatedTypeName, bytecode, 0, bytecode.length));
    }

    private void generateProxyClass(ClassWriter visitor, Class<?> managedTypeClass, List<String> interfaceInternalNames, Type generatedType, Type superclassType) {
        declareClass(visitor, interfaceInternalNames, generatedType, superclassType);
        declareStateField(visitor);
        declareCanCallSettersField(visitor);
        writeConstructor(visitor, generatedType, superclassType);
        writeToString(visitor, generatedType, managedTypeClass);
        writeManagedInstanceMethods(visitor, generatedType);
        writeGroovyMethods(visitor, managedTypeClass);
        writeMutationMethods(visitor, generatedType, managedTypeClass);
        visitor.visitEnd();
    }

    private void declareClass(ClassVisitor visitor, List<String> interfaceInternalNames, Type generatedType, Type superclassType) {
        visitor.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null,
                superclassType.getInternalName(), Iterables.toArray(interfaceInternalNames, String.class));
    }

    private void declareStateField(ClassVisitor visitor) {
        declareField(visitor, STATE_FIELD_NAME, ModelElementState.class);
    }

    private void declareCanCallSettersField(ClassVisitor visitor) {
        declareField(visitor, CAN_CALL_SETTERS_FIELD_NAME, Boolean.TYPE);
    }

    private void declareField(ClassVisitor visitor, String name, Class<?> fieldClass) {
        visitor.visitField(Opcodes.ACC_PRIVATE, name, Type.getDescriptor(fieldClass), null, null);
    }

    private void writeConstructor(ClassVisitor visitor, Type generatedType, Type superclassType) {
        MethodVisitor constructorVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", CONSTRUCTOR_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        constructorVisitor.visitCode();

        invokeSuperConstructor(constructorVisitor, superclassType);
        assignStateField(constructorVisitor, generatedType);
        setCanCallSettersField(constructorVisitor, generatedType, true);
        finishVisitingMethod(constructorVisitor);
    }

    private void invokeSuperConstructor(MethodVisitor constructorVisitor, Type superclassType) {
        putThisOnStack(constructorVisitor);
        constructorVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
    }

    private void writeToString(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass) {
        Method toStringMethod = getToStringMethod(managedTypeClass);

        if (toStringMethod == null || toStringMethod.getDeclaringClass().equals(Object.class)) {
            writeDefaultToString(visitor, generatedType);
        } else {
            writeNonAbstractMethodWrapper(visitor, generatedType, managedTypeClass, toStringMethod);
        }
    }

    private void writeDefaultToString(ClassVisitor visitor, Type generatedType) {
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "toString", TO_STRING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();
        putStateFieldValueOnStack(methodVisitor, generatedType);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "getDisplayName", TO_STRING_METHOD_DESCRIPTOR, true);
        finishVisitingMethod(methodVisitor, Opcodes.ARETURN);
    }

    private Method getToStringMethod(Class<?> managedTypeClass) {
        try {
            return managedTypeClass.getMethod("toString");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private void writeGroovyMethods(ClassVisitor visitor, Class<?> managedTypeClass) {
        // Object propertyMissing(String name)
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "propertyMissing", GET_PROPERTY_MISSING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();

        // throw new MissingPropertyException(name, <managed-type>.class)
        methodVisitor.visitTypeInsn(Opcodes.NEW, MISSING_PROPERTY_EXCEPTION_TYPE);
        methodVisitor.visitInsn(Opcodes.DUP);
        putFirstMethodArgumentOnStack(methodVisitor);
        putClassOnStack(methodVisitor, managedTypeClass);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, MISSING_PROPERTY_EXCEPTION_TYPE, "<init>", MISSING_PROPERTY_CONSTRUCTOR_DESCRIPTOR, false);
        finishVisitingMethod(methodVisitor, Opcodes.ATHROW);

        // Object propertyMissing(String name, Object value)

        methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "propertyMissing", SET_PROPERTY_MISSING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();

        // throw new MissingPropertyException(name, <managed-type>.class)
        methodVisitor.visitTypeInsn(Opcodes.NEW, MISSING_PROPERTY_EXCEPTION_TYPE);
        methodVisitor.visitInsn(Opcodes.DUP);
        putFirstMethodArgumentOnStack(methodVisitor);
        putClassOnStack(methodVisitor, managedTypeClass);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, MISSING_PROPERTY_EXCEPTION_TYPE, "<init>", MISSING_PROPERTY_CONSTRUCTOR_DESCRIPTOR, false);
        finishVisitingMethod(methodVisitor, Opcodes.ATHROW);

        // Object methodMissing(String name, Object args)
        methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "methodMissing", METHOD_MISSING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();

        // throw new MissingMethodException(name, <managed-type>.class, args)
        methodVisitor.visitTypeInsn(Opcodes.NEW, MISSING_METHOD_EXCEPTION_TYPE);
        methodVisitor.visitInsn(Opcodes.DUP);
        putMethodArgumentOnStack(methodVisitor, 1);
        putClassOnStack(methodVisitor, managedTypeClass);
        putMethodArgumentOnStack(methodVisitor, 2);
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, OBJECT_ARRAY_TYPE);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, MISSING_METHOD_EXCEPTION_TYPE, "<init>", MISSING_METHOD_EXCEPTION_CONSTRUCTOR_DESCRIPTOR, false);
        finishVisitingMethod(methodVisitor, Opcodes.ATHROW);
    }

    private void putClassOnStack(MethodVisitor methodVisitor, Class<?> managedTypeClass) {
        putConstantOnStack(methodVisitor, managedTypeClass.getName());
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_TYPE, "forName", FOR_NAME_METHOD_DESCRIPTOR, false);
    }

    private void writeManagedInstanceMethods(ClassVisitor visitor, Type generatedType) {
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();
        putStateFieldValueOnStack(methodVisitor, generatedType);
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, true);
        finishVisitingMethod(methodVisitor, Opcodes.ARETURN);
    }

    private void assignStateField(MethodVisitor constructorVisitor, Type generatedType) {
        putThisOnStack(constructorVisitor);
        putFirstMethodArgumentOnStack(constructorVisitor);
        constructorVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), STATE_FIELD_NAME, MODEL_ELEMENT_STATE_TYPE.getDescriptor());
    }

    private void setCanCallSettersField(MethodVisitor methodVisitor, Type generatedType, boolean canCallSetters) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitLdcInsn(canCallSetters);
        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), CAN_CALL_SETTERS_FIELD_NAME, Type.BOOLEAN_TYPE.getDescriptor());
    }

    private void putThisOnStack(MethodVisitor constructorVisitor) {
        constructorVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    }

    private void finishVisitingMethod(MethodVisitor methodVisitor) {
        finishVisitingMethod(methodVisitor, Opcodes.RETURN);
    }

    private void finishVisitingMethod(MethodVisitor methodVisitor, int returnOpcode) {
        methodVisitor.visitInsn(returnOpcode);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private void writeMutationMethods(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass) {
        ClassDetails classDetails = ClassInspector.inspect(managedTypeClass);
        for (PropertyDetails property : classDetails.getProperties()) {
            for (Method method : property.getGetters()) {
                if (Modifier.isAbstract(method.getModifiers())) {
                    writeGetter(visitor, generatedType, method);
                } else if (!Modifier.isFinal(method.getModifiers()) && !property.getName().equals("metaClass")) {
                    writeNonAbstractMethodWrapper(visitor, generatedType, managedTypeClass, method);
                }
            }
            for (Method method : property.getSetters()) {
                writeSetter(visitor, generatedType, method);
            }
        }
    }

    private void writeSetter(ClassVisitor visitor, Type generatedType, Method method) {
        String propertyName = getPropertyName(method);
        Label calledOutsideOfConstructor = new Label();

        MethodVisitor methodVisitor = declareMethod(visitor, method);

        putCanCallSettersFieldValueOnStack(methodVisitor, generatedType);
        jumpToLabelIfStackEvaluatesToTrue(methodVisitor, calledOutsideOfConstructor);
        throwExceptionBecauseCalledOnItself(methodVisitor);

        writeLabel(methodVisitor, calledOutsideOfConstructor);
        putStateFieldValueOnStack(methodVisitor, generatedType);
        putConstantOnStack(methodVisitor, propertyName);
        putFirstMethodArgumentOnStack(methodVisitor);
        invokeStateSetMethod(methodVisitor);

        finishVisitingMethod(methodVisitor);
    }

    private void writeLabel(MethodVisitor methodVisitor, Label label) {
        methodVisitor.visitLabel(label);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    }

    private void throwExceptionBecauseCalledOnItself(MethodVisitor methodVisitor) {
        String exceptionInternalName = Type.getInternalName(UnsupportedOperationException.class);
        methodVisitor.visitTypeInsn(Opcodes.NEW, exceptionInternalName);
        methodVisitor.visitInsn(Opcodes.DUP);
        putConstantOnStack(methodVisitor, "Calling setters of a managed type on itself is not allowed");

        String constructorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class));
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, exceptionInternalName, CONSTRUCTOR_NAME, constructorDescriptor, false);
        methodVisitor.visitInsn(Opcodes.ATHROW);
    }

    private void jumpToLabelIfStackEvaluatesToTrue(MethodVisitor methodVisitor, Label label) {
        methodVisitor.visitJumpInsn(Opcodes.IFNE, label);
    }

    private void invokeStateSetMethod(MethodVisitor methodVisitor) {
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "set", STATE_SET_METHOD_DESCRIPTOR, true);
    }

    private void putConstantOnStack(MethodVisitor methodVisitor, Object value) {
        methodVisitor.visitLdcInsn(value);
    }

    private MethodVisitor declareMethod(ClassVisitor visitor, Method method) {
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();
        return methodVisitor;
    }

    private void putFirstMethodArgumentOnStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
    }

    private void putMethodArgumentOnStack(MethodVisitor methodVisitor, int index) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, index);
    }

    private void putStateFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putFieldValueOnStack(methodVisitor, generatedType, STATE_FIELD_NAME, ModelElementState.class);
    }

    private void putCanCallSettersFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putFieldValueOnStack(methodVisitor, generatedType, CAN_CALL_SETTERS_FIELD_NAME, Boolean.TYPE);
    }

    private void putFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType, String name, Class<?> fieldClass) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), name, Type.getDescriptor(fieldClass));
    }

    private void writeGetter(ClassVisitor visitor, Type generatedType, Method method) {
        String propertyName = getPropertyName(method);

        MethodVisitor methodVisitor = declareMethod(visitor, method);

        putStateFieldValueOnStack(methodVisitor, generatedType);
        putConstantOnStack(methodVisitor, propertyName);
        invokeStateGetMethod(methodVisitor);
        castFirstStackElement(methodVisitor, method.getReturnType());
        finishVisitingMethod(methodVisitor, Opcodes.ARETURN);
    }

    private void castFirstStackElement(MethodVisitor methodVisitor, Class<?> returnType) {
        methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returnType));
    }

    private String getPropertyName(Method method) {
        return StringUtils.uncapitalize(method.getName().substring(3));
    }

    private void invokeStateGetMethod(MethodVisitor methodVisitor) {
        String methodDescriptor = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class));
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "get", methodDescriptor, true);
    }

    private void writeNonAbstractMethodWrapper(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass, Method method) {
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();

        MethodVisitor methodVisitor = declareMethod(visitor, method);

        methodVisitor.visitTryCatchBlock(start, end, handler, null);

        setCanCallSettersField(methodVisitor, generatedType, false);

        writeLabel(methodVisitor, start);
        invokeSuperMethod(methodVisitor, managedTypeClass, method);
        writeLabel(methodVisitor, end);

        setCanCallSettersField(methodVisitor, generatedType, true);
        methodVisitor.visitInsn(Opcodes.ARETURN);

        writeLabel(methodVisitor, handler);
        setCanCallSettersField(methodVisitor, generatedType, true);
        methodVisitor.visitInsn(Opcodes.ATHROW);

        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private void invokeSuperMethod(MethodVisitor methodVisitor, Class<?> superClass, Method method) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(superClass), method.getName(), Type.getMethodDescriptor(method), false);
    }
}
