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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.apache.commons.lang.StringUtils;
import org.gradle.model.internal.asm.AsmClassGeneratorUtils;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ModelElementState;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.type.ModelType;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

import static org.objectweb.asm.Opcodes.*;

public class ManagedProxyClassGenerator extends AbstractProxyClassGenerator {
    /*
        Note: there is deliberately no internal synchronizing or caching at this level.

        Class generation should always be performed behind a ModelSchemaCache, by way of DefaultModelSchemaStore.
        The generated class is then attached to the schema object.
        This allows us to avoid yet another weak class based cache, and importantly having to acquire a lock to instantiate an implementation.
     */

    private static final String STATE_FIELD_NAME = "$state";
    private static final String MANAGED_TYPE_FIELD_NAME = "$managedType";
    private static final String DELEGATE_FIELD_NAME = "$delegate";
    private static final String CAN_CALL_SETTERS_FIELD_NAME = "$canCallSetters";
    private static final String STATE_SET_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Object.class));
    private static final String MANAGED_INSTANCE_TYPE = Type.getInternalName(ManagedInstance.class);
    private static final Type MODEL_ELEMENT_STATE_TYPE = Type.getType(ModelElementState.class);
    private static final String NO_DELEGATE_CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, MODEL_ELEMENT_STATE_TYPE);
    private static final String TO_STRING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(String.class));
    private static final String MUTABLE_MODEL_NODE_TYPE = Type.getInternalName(MutableModelNode.class);
    private static final String GET_BACKING_NODE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(MutableModelNode.class));
    private static final Type MODELTYPE_TYPE = Type.getType(ModelType.class);
    private static final String MODELTYPE_INTERNAL_NAME = MODELTYPE_TYPE.getInternalName();
    private static final String MODELTYPE_OF_METHOD_DESCRIPTOR = Type.getMethodDescriptor(MODELTYPE_TYPE, Type.getType(Class.class));
    private static final String GET_MANAGED_TYPE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(MODELTYPE_TYPE);
    private static final String GET_PROPERTY_MISSING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class));
    private static final String MISSING_PROPERTY_EXCEPTION_TYPE = Type.getInternalName(MissingPropertyException.class);
    private static final String CLASS_TYPE = Type.getInternalName(Class.class);
    private static final String FOR_NAME_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Class.class), Type.getType(String.class));
    private static final String HASH_CODE_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(int.class));
    private static final String EQUALS_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(boolean.class), Type.getType(Object.class));
    private static final String OBJECT_ARRAY_TYPE = Type.getInternalName(Object[].class);
    private static final String MISSING_METHOD_EXCEPTION_TYPE = Type.getInternalName(MissingMethodException.class);
    private static final String MISSING_PROPERTY_CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Class.class));
    private static final String METHOD_MISSING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class), Type.getType(Object.class));
    private static final String SET_PROPERTY_MISSING_METHOD_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class), Type.getType(Object.class));
    private static final String MISSING_METHOD_EXCEPTION_CONSTRUCTOR_DESCRIPTOR = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class), Type.getType(Class.class), Type.getType(Object[].class));
    private static final Map<Class<?>, Class<?>> BOXED_TYPES = ImmutableMap.<Class<?>, Class<?>>builder()
        .put(byte.class, Byte.class)
        .put(short.class, Short.class)
        .put(int.class, Integer.class)
        .put(boolean.class, Boolean.class)
        .put(float.class, Float.class)
        .put(char.class, Character.class)
        .put(double.class, Double.class)
        .put(long.class, Long.class)
        .build();


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
    public <T, M extends T, D extends T> Class<? extends M> generate(ModelType<M> managedType, Class<D> delegateType, Iterable<ModelPropertyExtractionResult<?>> propertyResults) {
        if (delegateType != null && !delegateType.isInterface()) {
            throw new IllegalArgumentException("Delegate type must be null or an interface");
        }
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        Class<M> managedTypeClass = managedType.getConcreteClass();
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

        generateProxyClass(visitor, managedType, delegateType, interfaceInternalNames.build(), generatedType, Type.getType(superclass), propertyResults);

        Class<? extends M> generatedClass = defineClass(visitor, managedTypeClass.getClassLoader(), generatedTypeName);
        return generatedClass;
    }

    private void generateProxyClass(ClassWriter visitor, ModelType<?> managedType, Class<?> delegateTypeClass, Collection<String> interfaceInternalNames,
                                    Type generatedType, Type superclassType, Iterable<ModelPropertyExtractionResult<?>> propertyResults) {
        Class<?> managedTypeClass = managedType.getConcreteClass();
        declareClass(visitor, interfaceInternalNames, generatedType, superclassType);
        declareStateField(visitor);
        declareManagedTypeField(visitor);
        declareCanCallSettersField(visitor);
        writeStaticConstructor(visitor, generatedType, managedTypeClass);
        writeConstructor(visitor, generatedType, superclassType, delegateTypeClass);
        writeToString(visitor, generatedType, managedTypeClass);
        writeManagedInstanceMethods(visitor, generatedType);
        if (delegateTypeClass != null) {
            declareDelegateField(visitor, delegateTypeClass);
            writeDelegateMethods(visitor, generatedType, delegateTypeClass);
        }
        writeGroovyMethods(visitor, managedTypeClass);
        writeMutationMethods(visitor, generatedType, managedTypeClass, propertyResults);
        writeHashCodeMethod(visitor, generatedType);
        writeEqualsMethod(visitor, generatedType);
        visitor.visitEnd();
    }

    private void declareClass(ClassVisitor visitor, Collection<String> interfaceInternalNames, Type generatedType, Type superclassType) {
        visitor.visit(V1_6, ACC_PUBLIC, generatedType.getInternalName(), null,
            superclassType.getInternalName(), Iterables.toArray(interfaceInternalNames, String.class));
    }

    private void declareStateField(ClassVisitor visitor) {
        declareField(visitor, STATE_FIELD_NAME, ModelElementState.class);
    }

    private void declareManagedTypeField(ClassVisitor visitor) {
        declareStaticField(visitor, MANAGED_TYPE_FIELD_NAME, ModelType.class);
    }

    private void declareDelegateField(ClassVisitor visitor, Class<?> delegateTypeClass) {
        declareField(visitor, DELEGATE_FIELD_NAME, delegateTypeClass);
    }

    private void declareCanCallSettersField(ClassVisitor visitor) {
        declareField(visitor, CAN_CALL_SETTERS_FIELD_NAME, Boolean.TYPE);
    }

    private void declareField(ClassVisitor visitor, String name, Class<?> fieldClass) {
        visitor.visitField(ACC_PRIVATE, name, Type.getDescriptor(fieldClass), null, null);
    }

    private FieldVisitor declareStaticField(ClassVisitor visitor, String name, Class<?> fieldClass) {
        return visitor.visitField(ACC_PRIVATE | ACC_STATIC, name, Type.getDescriptor(fieldClass), null, null);
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
        MethodVisitor constructorVisitor = visitor.visitMethod(ACC_PUBLIC, CONSTRUCTOR_NAME, constructorDescriptor, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        constructorVisitor.visitCode();

        invokeSuperConstructor(constructorVisitor, superclassType);
        assignStateField(constructorVisitor, generatedType);
        if (delegateType != null) {
            assignDelegateField(constructorVisitor, generatedType, delegateType);
        }
        setCanCallSettersField(constructorVisitor, generatedType, true);
        finishVisitingMethod(constructorVisitor);
    }

    private void writeStaticConstructor(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass) {
        MethodVisitor constructorVisitor = visitor.visitMethod(ACC_STATIC, STATIC_CONSTRUCTOR_NAME, "()V", CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        constructorVisitor.visitCode();
        writeManagedTypeStaticField(generatedType, managedTypeClass, constructorVisitor);
        finishVisitingMethod(constructorVisitor);
    }

    private void writeManagedTypeStaticField(Type generatedType, Class<?> managedTypeClass, MethodVisitor constructorVisitor) {
        constructorVisitor.visitLdcInsn(Type.getType(managedTypeClass));
        constructorVisitor.visitMethodInsn(INVOKESTATIC, MODELTYPE_INTERNAL_NAME, "of", MODELTYPE_OF_METHOD_DESCRIPTOR, false);
        constructorVisitor.visitFieldInsn(PUTSTATIC, generatedType.getInternalName(), MANAGED_TYPE_FIELD_NAME, Type.getDescriptor(ModelType.class));
    }

    private void invokeSuperConstructor(MethodVisitor constructorVisitor, Type superclassType) {
        putThisOnStack(constructorVisitor);
        constructorVisitor.visitMethodInsn(INVOKESPECIAL, superclassType.getInternalName(), CONSTRUCTOR_NAME, Type.getMethodDescriptor(Type.VOID_TYPE), false);
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
        MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "toString", TO_STRING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();
        putStateFieldValueOnStack(methodVisitor, generatedType);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "getDisplayName", TO_STRING_METHOD_DESCRIPTOR, true);
        finishVisitingMethod(methodVisitor, ARETURN);
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
        MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "propertyMissing", GET_PROPERTY_MISSING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();

        // throw new MissingPropertyException(name, <managed-type>.class)
        methodVisitor.visitTypeInsn(NEW, MISSING_PROPERTY_EXCEPTION_TYPE);
        methodVisitor.visitInsn(DUP);
        putFirstMethodArgumentOnStack(methodVisitor);
        putClassOnStack(methodVisitor, managedTypeClass);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, MISSING_PROPERTY_EXCEPTION_TYPE, "<init>", MISSING_PROPERTY_CONSTRUCTOR_DESCRIPTOR, false);
        finishVisitingMethod(methodVisitor, ATHROW);

        // Object propertyMissing(String name, Object value)

        methodVisitor = visitor.visitMethod(ACC_PUBLIC, "propertyMissing", SET_PROPERTY_MISSING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();

        // throw new MissingPropertyException(name, <managed-type>.class)
        methodVisitor.visitTypeInsn(NEW, MISSING_PROPERTY_EXCEPTION_TYPE);
        methodVisitor.visitInsn(DUP);
        putFirstMethodArgumentOnStack(methodVisitor);
        putClassOnStack(methodVisitor, managedTypeClass);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, MISSING_PROPERTY_EXCEPTION_TYPE, "<init>", MISSING_PROPERTY_CONSTRUCTOR_DESCRIPTOR, false);
        finishVisitingMethod(methodVisitor, ATHROW);

        // Object methodMissing(String name, Object args)
        methodVisitor = visitor.visitMethod(ACC_PUBLIC, "methodMissing", METHOD_MISSING_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();

        // throw new MissingMethodException(name, <managed-type>.class, args)
        methodVisitor.visitTypeInsn(NEW, MISSING_METHOD_EXCEPTION_TYPE);
        methodVisitor.visitInsn(DUP);
        putMethodArgumentOnStack(methodVisitor, 1);
        putClassOnStack(methodVisitor, managedTypeClass);
        putMethodArgumentOnStack(methodVisitor, 2);
        methodVisitor.visitTypeInsn(CHECKCAST, OBJECT_ARRAY_TYPE);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, MISSING_METHOD_EXCEPTION_TYPE, "<init>", MISSING_METHOD_EXCEPTION_CONSTRUCTOR_DESCRIPTOR, false);
        finishVisitingMethod(methodVisitor, ATHROW);
    }

    private void putClassOnStack(MethodVisitor methodVisitor, Class<?> managedTypeClass) {
        putConstantOnStack(methodVisitor, managedTypeClass.getName());
        methodVisitor.visitMethodInsn(INVOKESTATIC, CLASS_TYPE, "forName", FOR_NAME_METHOD_DESCRIPTOR, false);
    }

    private void writeManagedInstanceMethods(ClassVisitor visitor, Type generatedType) {
        writeManagedInstanceGetBackingNodeMethod(visitor, generatedType);
        writeManagedInstanceGetManagedTypeMethod(visitor, generatedType);
    }

    private void writeManagedInstanceGetBackingNodeMethod(ClassVisitor visitor, Type generatedType) {
        MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        methodVisitor.visitCode();
        putStateFieldValueOnStack(methodVisitor, generatedType);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, true);
        finishVisitingMethod(methodVisitor, ARETURN);
    }

    private void writeManagedInstanceGetManagedTypeMethod(ClassVisitor visitor, Type generatedType) {
        MethodVisitor managedTypeVisitor = visitor.visitMethod(ACC_PUBLIC, "getManagedType", GET_MANAGED_TYPE_METHOD_DESCRIPTOR, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
        managedTypeVisitor.visitCode();
        putManagedTypeFieldValueOnStack(managedTypeVisitor, generatedType);
        finishVisitingMethod(managedTypeVisitor, ARETURN);
    }

    private void assignStateField(MethodVisitor constructorVisitor, Type generatedType) {
        putThisOnStack(constructorVisitor);
        putFirstMethodArgumentOnStack(constructorVisitor);
        constructorVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), STATE_FIELD_NAME, MODEL_ELEMENT_STATE_TYPE.getDescriptor());
    }

    private void assignDelegateField(MethodVisitor constructorVisitor, Type generatedType, Type delegateType) {
        putThisOnStack(constructorVisitor);
        putSecondMethodArgumentOnStack(constructorVisitor);
        constructorVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), DELEGATE_FIELD_NAME, delegateType.getDescriptor());
    }

    private void setCanCallSettersField(MethodVisitor methodVisitor, Type generatedType, boolean canCallSetters) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitLdcInsn(canCallSetters);
        methodVisitor.visitFieldInsn(PUTFIELD, generatedType.getInternalName(), CAN_CALL_SETTERS_FIELD_NAME, Type.BOOLEAN_TYPE.getDescriptor());
    }

    private void writeMutationMethods(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass, Iterable<ModelPropertyExtractionResult<?>> propertyResults) {
        for (ModelPropertyExtractionResult<?> propertyResult : propertyResults) {
            ModelProperty<?> property = propertyResult.getProperty();
            String propertyName = property.getName();
            switch (property.getStateManagementType()) {
                case MANAGED:
                    Class<?> propertyTypeClass = property.getType().getConcreteClass();
                    writeGetter(visitor, generatedType, propertyName, propertyTypeClass, propertyResult.getGetter());
                    if (propertyResult.getSetter() != null) {
                        writeSetter(visitor, generatedType, propertyName, propertyTypeClass, propertyResult.getSetter().getMostSpecificSignature());
                    }
                    break;

                case UNMANAGED:
                    String getterName = getGetterName(propertyName);
                    Method getterMethod;
                    try {
                        getterMethod = managedTypeClass.getMethod(getterName);
                    } catch (NoSuchMethodException e) {
                        throw new IllegalStateException("Cannot find getter '" + getterName + "' on type " + managedTypeClass.getName(), e);
                    }
                    if (!Modifier.isFinal(getterMethod.getModifiers()) && !propertyName.equals("metaClass")) {
                        writeNonAbstractMethodWrapper(visitor, generatedType, managedTypeClass, getterMethod);
                    }
                    break;

                case DELEGATED:
                    // We'll handle all delegated methods (not just properties) separately
                    break;
            }
        }
    }

    private void writeDelegateMethods(ClassVisitor visitor, Type generatedType, Class<?> delegateTypeClass) {
        for (Method delegateMethod : delegateTypeClass.getMethods()) {
            writeDelegatedMethod(visitor, generatedType, delegateTypeClass, delegateMethod);
        }
    }

    private void writeSetter(ClassVisitor visitor, Type generatedType, String propertyName, Class<?> propertyTypeClass, String signature) {
        Label calledOutsideOfConstructor = new Label();

        MethodVisitor methodVisitor = declareMethod(visitor, getSetterName(propertyName), Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(propertyTypeClass)), signature);

        putCanCallSettersFieldValueOnStack(methodVisitor, generatedType);
        jumpToLabelIfStackEvaluatesToTrue(methodVisitor, calledOutsideOfConstructor);
        throwExceptionBecauseCalledOnItself(methodVisitor);

        methodVisitor.visitLabel(calledOutsideOfConstructor);
        putStateFieldValueOnStack(methodVisitor, generatedType);
        putConstantOnStack(methodVisitor, propertyName);
        putFirstMethodArgumentOnStack(methodVisitor, propertyTypeClass);
        if (propertyTypeClass.isPrimitive()) {
            boxType(methodVisitor, propertyTypeClass);
        }
        invokeStateSetMethod(methodVisitor);

        finishVisitingMethod(methodVisitor);
    }

    private void writeHashCodeMethod(ClassVisitor visitor, Type generatedType) {
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "hashCode", HASH_CODE_METHOD_DESCRIPTOR, null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, false);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MUTABLE_MODEL_NODE_TYPE, "hashCode", HASH_CODE_METHOD_DESCRIPTOR, true);
        methodVisitor.visitInsn(IRETURN);
        finishVisitingMethod(methodVisitor, Opcodes.IRETURN);
    }

    private void writeEqualsMethod(ClassVisitor cw, Type generatedType) {
        MethodVisitor methodVisitor = cw.visitMethod(Opcodes.ACC_PUBLIC, "equals", EQUALS_METHOD_DESCRIPTOR, null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitVarInsn(ALOAD, 1);
        Label notSameLabel = new Label();
        methodVisitor.visitJumpInsn(IF_ACMPNE, notSameLabel);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitLabel(notSameLabel);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitTypeInsn(INSTANCEOF, MANAGED_INSTANCE_TYPE);
        Label notManagedInstanceLabel = new Label();
        methodVisitor.visitJumpInsn(IFNE, notManagedInstanceLabel);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitLabel(notManagedInstanceLabel);
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, generatedType.getInternalName(), "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, false);
        methodVisitor.visitVarInsn(ALOAD, 1);
        methodVisitor.visitTypeInsn(CHECKCAST, MANAGED_INSTANCE_TYPE);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MANAGED_INSTANCE_TYPE, "getBackingNode", GET_BACKING_NODE_METHOD_DESCRIPTOR, true);
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MUTABLE_MODEL_NODE_TYPE, "equals", EQUALS_METHOD_DESCRIPTOR, true);
        finishVisitingMethod(methodVisitor, Opcodes.IRETURN);
    }

    private void throwExceptionBecauseCalledOnItself(MethodVisitor methodVisitor) {
        String exceptionInternalName = Type.getInternalName(UnsupportedOperationException.class);
        methodVisitor.visitTypeInsn(NEW, exceptionInternalName);
        methodVisitor.visitInsn(DUP);
        putConstantOnStack(methodVisitor, "Calling setters of a managed type on itself is not allowed");

        String constructorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(String.class));
        methodVisitor.visitMethodInsn(INVOKESPECIAL, exceptionInternalName, CONSTRUCTOR_NAME, constructorDescriptor, false);
        methodVisitor.visitInsn(ATHROW);
    }

    private void jumpToLabelIfStackEvaluatesToTrue(MethodVisitor methodVisitor, Label label) {
        methodVisitor.visitJumpInsn(IFNE, label);
    }

    private void invokeStateSetMethod(MethodVisitor methodVisitor) {
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "set", STATE_SET_METHOD_DESCRIPTOR, true);
    }

    private void putConstantOnStack(MethodVisitor methodVisitor, Object value) {
        methodVisitor.visitLdcInsn(value);
    }

    private MethodVisitor declareMethod(ClassVisitor visitor, Method method) {
        return declareMethod(visitor, method.getName(), Type.getMethodDescriptor(method));
    }

    private MethodVisitor declareMethod(ClassVisitor visitor, String methodName, String methodDescriptor) {
        return declareMethod(visitor, methodName, methodDescriptor, CONCRETE_SIGNATURE);
    }

    private MethodVisitor declareMethod(ClassVisitor visitor, String methodName, String methodDescriptor, String methodSignature) {
        MethodVisitor methodVisitor = visitor.visitMethod(ACC_PUBLIC, methodName, methodDescriptor, methodSignature, NO_EXCEPTIONS);
        methodVisitor.visitCode();
        return methodVisitor;
    }

    private void putFirstMethodArgumentOnStack(MethodVisitor methodVisitor, Class<?> argType) {
        int loadCode = selectOpcode(argType, ALOAD, ILOAD, LLOAD, FLOAD, DLOAD);
        methodVisitor.visitVarInsn(loadCode, 1);
    }

    private int selectOpcode(Class<?> argType, int defaultValue, int intCategoryValue, int longValue, int floatValue, int doubleValue) {
        int code = defaultValue;
        if (argType.isPrimitive()) {
            if (byte.class == argType || short.class == argType || int.class == argType || char.class == argType || boolean.class == argType) {
                code = intCategoryValue;
            } else if (long.class == argType) {
                code = longValue;
            } else if (float.class == argType) {
                code = floatValue;
            } else if (double.class == argType) {
                code = doubleValue;
            }
        }
        return code;
    }

    private void putFirstMethodArgumentOnStack(MethodVisitor methodVisitor) {
        putFirstMethodArgumentOnStack(methodVisitor, Object.class);
    }

    private void putSecondMethodArgumentOnStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(ALOAD, 2);
    }

    private void putMethodArgumentOnStack(MethodVisitor methodVisitor, int index) {
        methodVisitor.visitVarInsn(ALOAD, index);
    }

    private void putBooleanMethodArgumentOnStack(MethodVisitor methodVisitor, int index) {
        methodVisitor.visitVarInsn(ILOAD, index);
    }

    private void putStateFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putFieldValueOnStack(methodVisitor, generatedType, STATE_FIELD_NAME, ModelElementState.class);
    }

    private void putManagedTypeFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putStaticFieldValueOnStack(methodVisitor, generatedType, MANAGED_TYPE_FIELD_NAME, ModelType.class);
    }

    private void putDelegateFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType, Class<?> delegateTypeClass) {
        putFieldValueOnStack(methodVisitor, generatedType, DELEGATE_FIELD_NAME, delegateTypeClass);
    }

    private void putCanCallSettersFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putFieldValueOnStack(methodVisitor, generatedType, CAN_CALL_SETTERS_FIELD_NAME, Boolean.TYPE);
    }

    private void putFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType, String name, Class<?> fieldClass) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitFieldInsn(GETFIELD, generatedType.getInternalName(), name, Type.getDescriptor(fieldClass));
    }

    private void putStaticFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType, String name, Class<?> fieldClass) {
        methodVisitor.visitFieldInsn(GETSTATIC, generatedType.getInternalName(), name, Type.getDescriptor(fieldClass));
    }

    private void writeGetter(ClassVisitor visitor, Type generatedType, String propertyName, Class<?> propertyTypeClass, PropertyAccessorExtractionContext getterContext) {
        for (Method getter : getterContext.getGetters()) {
            MethodVisitor methodVisitor = declareMethod(
                visitor,
                getter.getName(),
                Type.getMethodDescriptor(Type.getType(propertyTypeClass)),
                AsmClassGeneratorUtils.signature(getter));

            putStateFieldValueOnStack(methodVisitor, generatedType);
            putConstantOnStack(methodVisitor, propertyName);
            invokeStateGetMethod(methodVisitor);
            castFirstStackElement(methodVisitor, propertyTypeClass);
            finishVisitingMethod(methodVisitor, returnCode(propertyTypeClass));
        }

    }

    private int returnCode(Class<?> propertyTypeClass) {
        return selectOpcode(propertyTypeClass, ARETURN, IRETURN, LRETURN, FRETURN, DRETURN);
    }

    private static String getGetterName(String propertyName) {
        return "get" + StringUtils.capitalize(propertyName);
    }

    private static String getSetterName(String propertyName) {
        return "set" + StringUtils.capitalize(propertyName);
    }

    private void castFirstStackElement(MethodVisitor methodVisitor, Class<?> returnType) {
        if (returnType.isPrimitive()) {
            unboxType(methodVisitor, returnType);
        } else {
            methodVisitor.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType));
        }
    }

    private void boxType(MethodVisitor methodVisitor, Class<?> primitiveType) {
        Class<?> boxedType = BOXED_TYPES.get(primitiveType);
        methodVisitor.visitMethodInsn(INVOKESTATIC, Type.getInternalName(boxedType), "valueOf", "(" + Type.getDescriptor(primitiveType) + ")" + Type.getDescriptor(boxedType), false);
    }

    private void unboxType(MethodVisitor methodVisitor, Class<?> primitiveType) {
        // Float f = (Float) tmp
        // f==null?0:f.floatValue()
        Class<?> boxedType = BOXED_TYPES.get(primitiveType);
        methodVisitor.visitTypeInsn(CHECKCAST, Type.getInternalName(boxedType));
        methodVisitor.visitInsn(DUP);
        Label exit = new Label();
        Label elseValue = new Label();
        methodVisitor.visitJumpInsn(IFNONNULL, elseValue);
        methodVisitor.visitInsn(POP);
        pushDefaultValue(methodVisitor, primitiveType);
        methodVisitor.visitJumpInsn(GOTO, exit);
        methodVisitor.visitLabel(elseValue);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(boxedType), primitiveType.getSimpleName() + "Value", "()" + Type.getDescriptor(primitiveType), false);
        methodVisitor.visitLabel(exit);
    }

    private void pushDefaultValue(MethodVisitor methodVisitor, Class<?> primitiveType) {
        int ins = ICONST_0;
        if (long.class == primitiveType) {
            ins = LCONST_0;
        } else if (double.class == primitiveType) {
            ins = DCONST_0;
        } else if (float.class == primitiveType) {
            ins = FCONST_0;
        }
        methodVisitor.visitInsn(ins);
    }

    private void invokeStateGetMethod(MethodVisitor methodVisitor) {
        String methodDescriptor = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(String.class));
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, MODEL_ELEMENT_STATE_TYPE.getInternalName(), "get", methodDescriptor, true);
    }

    private void writeNonAbstractMethodWrapper(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass, Method method) {
        Label start = new Label();
        Label end = new Label();
        Label handler = new Label();

        MethodVisitor methodVisitor = declareMethod(visitor, method);

        methodVisitor.visitTryCatchBlock(start, end, handler, null);

        setCanCallSettersField(methodVisitor, generatedType, false);

        methodVisitor.visitLabel(start);
        invokeSuperMethod(methodVisitor, managedTypeClass, method);
        methodVisitor.visitLabel(end);

        setCanCallSettersField(methodVisitor, generatedType, true);
        methodVisitor.visitInsn(ARETURN);

        methodVisitor.visitLabel(handler);
        setCanCallSettersField(methodVisitor, generatedType, true);
        methodVisitor.visitInsn(ATHROW);

        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private void writeDelegatedMethod(ClassVisitor visitor, Type generatedType, Class<?> delegateTypeClass, Method method) {
        MethodVisitor methodVisitor = declareMethod(visitor, method.getName(), Type.getMethodDescriptor(method), AsmClassGeneratorUtils.signature(method));
        invokeDelegateMethod(methodVisitor, generatedType, delegateTypeClass, method);
        final Class<?> returnType = method.getReturnType();
        if (returnType == Void.TYPE) {
            finishVisitingMethod(methodVisitor);
        } else {
            finishVisitingMethod(methodVisitor, returnCode(returnType));
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
        methodVisitor.visitMethodInsn(INVOKEINTERFACE, Type.getInternalName(delegateTypeClass), method.getName(), Type.getMethodDescriptor(method), true);
    }

    private void invokeSuperMethod(MethodVisitor methodVisitor, Class<?> superClass, Method method) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(superClass), method.getName(), Type.getMethodDescriptor(method), false);
    }
}
