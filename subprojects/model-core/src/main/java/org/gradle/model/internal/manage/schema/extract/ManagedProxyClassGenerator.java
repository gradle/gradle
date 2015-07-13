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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.reflect.ClassDetails;
import org.gradle.internal.reflect.ClassInspector;
import org.gradle.internal.reflect.PropertyDetails;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class ManagedProxyClassGenerator extends AbstractProxyClassGenerator {
    /*
        Note: there is deliberately no internal synchronizing or caching at this level.

        Class generation should always be performed behind a ModelSchemaCache, by way of DefaultModelSchemaStore.
        The generated class is then attached to the schema object.
        This allows us to avoid yet another weak class based cache, and importantly having to acquire a lock to instantiate an implementation.
     */

    private static final String STATE_FIELD_NAME = "$state";
    private static final String DELEGATE_FIELD_NAME = "$delegate";
    private static final String CAN_CALL_SETTERS_FIELD_NAME = "$canCallSetters";
    private static final String STATE_SET_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Object.class));
    private static final String MANAGED_INSTANCE_TYPE = Type.getInternalName(ManagedInstance.class);
    private static final Type MODEL_ELEMENT_STATE_TYPE = Type.getType(ModelElementState.class);
    private static final String NO_DELEGATE_CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, MODEL_ELEMENT_STATE_TYPE);
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
        return generate(managedTypeClass, null);
    }

    public <T, M extends T, D extends T> Class<? extends M> generate(Class<M> managedTypeClass, Class<D> delegateType) {
        if (delegateType != null && !delegateType.isInterface()) {
            throw new IllegalArgumentException("Delegate type must be null or an interface");
        }
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        String generatedTypeName = managedTypeClass.getName() + "_Impl";
        Type generatedType = Type.getType("L" + generatedTypeName.replaceAll("\\.", "/") + ";");

        Class<?> superclass;
        ImmutableSet.Builder<String> interfaceInternalNames = ImmutableSet.builder();
        interfaceInternalNames.add(MANAGED_INSTANCE_TYPE);
        if (managedTypeClass.isInterface()) {
            superclass = Object.class;
            interfaceInternalNames = interfaceInternalNames.add(Type.getInternalName(managedTypeClass));
        } else {
            superclass = managedTypeClass;
        }
        if (delegateType != null) {
            interfaceInternalNames.add(Type.getInternalName(delegateType));
        }

        generateProxyClass(visitor, managedTypeClass, delegateType, interfaceInternalNames.build(), generatedType, Type.getType(superclass));

        return defineClass(visitor, managedTypeClass.getClassLoader(), generatedTypeName);
    }

    private void generateProxyClass(ClassWriter visitor, Class<?> managedTypeClass, Class<?> delegateTypeClass, Collection<String> interfaceInternalNames, Type generatedType, Type superclassType) {
        declareClass(visitor, interfaceInternalNames, generatedType, superclassType);
        declareStateField(visitor);
        declareCanCallSettersField(visitor);
        writeConstructor(visitor, generatedType, superclassType, delegateTypeClass);
        writeToString(visitor, generatedType, managedTypeClass);
        writeManagedInstanceMethods(visitor, generatedType);
        if (delegateTypeClass != null) {
            declareDelegateField(visitor, delegateTypeClass);
            writeDelegateMethods(visitor, generatedType, delegateTypeClass);
        }
        writeGroovyMethods(visitor, managedTypeClass);
        writeMutationMethods(visitor, generatedType, managedTypeClass, delegateTypeClass);
        visitor.visitEnd();
    }

    private void declareClass(ClassVisitor visitor, Collection<String> interfaceInternalNames, Type generatedType, Type superclassType) {
        visitor.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null,
            superclassType.getInternalName(), Iterables.toArray(interfaceInternalNames, String.class));
    }

    private void declareStateField(ClassVisitor visitor) {
        declareField(visitor, STATE_FIELD_NAME, ModelElementState.class);
    }

    private void declareDelegateField(ClassVisitor visitor, Class<?> delegateTypeClass) {
        declareField(visitor, DELEGATE_FIELD_NAME, delegateTypeClass);
    }

    private void declareCanCallSettersField(ClassVisitor visitor) {
        declareField(visitor, CAN_CALL_SETTERS_FIELD_NAME, Boolean.TYPE);
    }

    private void declareField(ClassVisitor visitor, String name, Class<?> fieldClass) {
        visitor.visitField(Opcodes.ACC_PRIVATE, name, Type.getDescriptor(fieldClass), null, null);
    }

    private void writeConstructor(ClassVisitor visitor, Type generatedType, Type superclassType, Class<?> delegateTypeClass) {
        String constructorDescriptor;
        Type delegateType;
        if (delegateTypeClass == null) {
            delegateType = null;
            constructorDescriptor = NO_DELEGATE_CONSTRUCTOR_DESCRIPTOR;
        } else {
            delegateType = Type.getType(delegateTypeClass);
            constructorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, MODEL_ELEMENT_STATE_TYPE, delegateType);
        }
        MethodVisitor constructorVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, CONSTRUCTOR_NAME, constructorDescriptor, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        constructorVisitor.visitCode();

        invokeSuperConstructor(constructorVisitor, superclassType);
        assignStateField(constructorVisitor, generatedType);
        if (delegateType != null) {
            assignDelegateField(constructorVisitor, generatedType, delegateType);
        }
        setCanCallSettersField(constructorVisitor, generatedType, true);
        finishVisitingMethod(constructorVisitor);
    }

    private void invokeSuperConstructor(MethodVisitor constructorVisitor, Type superclassType) {
        putThisOnStack(constructorVisitor);
        constructorVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), CONSTRUCTOR_NAME, Type.getMethodDescriptor(Type.VOID_TYPE), false);
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

    private void assignDelegateField(MethodVisitor constructorVisitor, Type generatedType, Type delegateType) {
        putThisOnStack(constructorVisitor);
        putSecondMethodArgumentOnStack(constructorVisitor);
        constructorVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), DELEGATE_FIELD_NAME, delegateType.getDescriptor());
    }

    private void setCanCallSettersField(MethodVisitor methodVisitor, Type generatedType, boolean canCallSetters) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitLdcInsn(canCallSetters);
        methodVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), CAN_CALL_SETTERS_FIELD_NAME, Type.BOOLEAN_TYPE.getDescriptor());
    }

    private void writeMutationMethods(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass, Class<?> delegateTypeClass) {
        ClassDetails classDetails = ClassInspector.inspect(managedTypeClass);
        Set<String> delegatePropertyNames;
        if (delegateTypeClass != null) {
            delegatePropertyNames = ClassInspector.inspect(delegateTypeClass).getPropertyNames();
        } else {
            delegatePropertyNames = Collections.emptySet();
        }
        for (PropertyDetails property : classDetails.getProperties()) {
            if (delegatePropertyNames.contains(property.getName())) {
                continue;
            }
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

    private void writeDelegateMethods(ClassVisitor visitor, Type generatedType, Class<?> delegateTypeClass) {
        for (Method delegateMethod : delegateTypeClass.getMethods()) {
            writeDelegatedMethod(visitor, generatedType, delegateTypeClass, delegateMethod);
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

    private void putSecondMethodArgumentOnStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 2);
    }

    private void putMethodArgumentOnStack(MethodVisitor methodVisitor, int index) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, index);
    }

    private void putBooleanMethodArgumentOnStack(MethodVisitor methodVisitor, int index) {
        methodVisitor.visitVarInsn(Opcodes.ILOAD, index);
    }

    private void putStateFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putFieldValueOnStack(methodVisitor, generatedType, STATE_FIELD_NAME, ModelElementState.class);
    }

    private void putDelegateFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType, Class<?> delegateTypeClass) {
        putFieldValueOnStack(methodVisitor, generatedType, DELEGATE_FIELD_NAME, delegateTypeClass);
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

    private void writeDelegatedMethod(ClassVisitor visitor, Type generatedType, Class<?> delegateTypeClass, Method method) {
        MethodVisitor methodVisitor = declareMethod(visitor, method);
        invokeDelegateMethod(methodVisitor, generatedType, delegateTypeClass, method);
        final Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            finishVisitingMethod(methodVisitor);
        } else if (returnType == Boolean.TYPE) {
            finishVisitingMethod(methodVisitor, Opcodes.IRETURN);
        } else {
            finishVisitingMethod(methodVisitor, Opcodes.ARETURN);
        }
    }

    private void invokeDelegateMethod(MethodVisitor methodVisitor, Type generatedType, Class<?> delegateTypeClass, Method method) {
        putDelegateFieldValueOnStack(methodVisitor, generatedType, delegateTypeClass);
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int paramNo = 0; paramNo < parameterTypes.length; paramNo++) {
            if (parameterTypes[paramNo] == Boolean.TYPE) {
                putBooleanMethodArgumentOnStack(methodVisitor, paramNo + 1);
            } else {
                putMethodArgumentOnStack(methodVisitor, paramNo + 1);
            }
        }
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(delegateTypeClass), method.getName(), Type.getMethodDescriptor(method), true);
    }

    private void invokeSuperMethod(MethodVisitor methodVisitor, Class<?> superClass, Method method) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(superClass), method.getName(), Type.getMethodDescriptor(method), false);
    }
}
