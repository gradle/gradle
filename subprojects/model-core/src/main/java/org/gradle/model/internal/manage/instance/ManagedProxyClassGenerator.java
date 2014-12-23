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

package org.gradle.model.internal.manage.instance;

import org.apache.commons.collections.map.AbstractReferenceMap;
import org.apache.commons.collections.map.ReferenceMap;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ParameterizedTypeImpl;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;

import java.lang.reflect.*;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ManagedProxyClassGenerator {
    private static final Map<Class<?>, Class<?>> GENERATED_CLASSES = Cast.uncheckedCast(new ReferenceMap(AbstractReferenceMap.WEAK, AbstractReferenceMap.WEAK));
    private static final Lock CACHE_LOCK = new ReentrantLock();
    private static final JavaMethod<ClassLoader, ?> DEFINE_CLASS_METHOD = JavaReflectionUtil.method(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE);

    private static final String CONCRETE_SIGNATURE = null;
    private static final String STATE_FIELD_NAME = "state";

    public <T> Class<? extends T> generate(Class<T> type) {
        try {
            CACHE_LOCK.lock();
            return generateUnderLock(type);
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    private <T> Class<? extends T> generateUnderLock(Class<T> type) {
        Class<?> cachedClass = GENERATED_CLASSES.get(type);
        if (cachedClass != null) {
            return cachedClass.asSubclass(type);
        }
        Class<? extends T> generatedClass = generateProxyClass(type);
        GENERATED_CLASSES.put(type, generatedClass);
        return generatedClass;
    }

    private <T> Class<? extends T> generateProxyClass(Class<T> managedTypeClass) {
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        String generatedTypeName = managedTypeClass.getName() + "_Impl";
        Type generatedType = Type.getType("L" + generatedTypeName.replaceAll("\\.", "/") + ";");
        Type superclassType = Type.getType(Object.class);

        generateProxyClass(visitor, managedTypeClass, generatedType, superclassType);

        return defineClass(visitor, managedTypeClass.getClassLoader(), generatedTypeName);
    }

    private <T> Class<? extends T> defineClass(ClassWriter visitor, ClassLoader classLoader, String generatedTypeName) {
        byte[] bytecode = visitor.toByteArray();
        return Cast.uncheckedCast(DEFINE_CLASS_METHOD.invoke(classLoader, generatedTypeName, bytecode, 0, bytecode.length));
    }

    private <T> void generateProxyClass(ClassWriter visitor, Class<T> managedTypeClass, Type generatedType, Type superclassType) {
        declareClass(visitor, managedTypeClass, generatedType, superclassType);
        declareStateField(visitor);
        writeConstructor(visitor, generatedType, superclassType);
        writeMethods(visitor, generatedType, managedTypeClass);
        visitor.visitEnd();
    }

    private void declareClass(ClassVisitor visitor, Class<?> managedTypeClass, Type generatedType, Type superclassType) {
        String[] interfaces = new String[]{Type.getInternalName(managedTypeClass), Type.getInternalName(ManagedInstance.class)};
        visitor.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null,
                superclassType.getInternalName(), interfaces);
    }

    private void declareStateField(ClassVisitor visitor) {
        visitor.visitField(Opcodes.ACC_PRIVATE, STATE_FIELD_NAME, Type.getDescriptor(ModelElementState.class), null, null);
    }

    private void writeConstructor(ClassVisitor visitor, Type generatedType, Type superclassType) {
        String constructorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ModelElementState.class));

        MethodVisitor constructorVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor, CONCRETE_SIGNATURE, new String[0]);
        constructorVisitor.visitCode();

        invokeSuperConstructor(constructorVisitor, superclassType);
        assignStateField(generatedType, constructorVisitor);
        finishVisitingMethod(constructorVisitor);
    }

    private void invokeSuperConstructor(MethodVisitor constructorVisitor, Type superclassType) {
        putThisOnStack(constructorVisitor);
        constructorVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>", Type.getMethodDescriptor(Type.VOID_TYPE), false);
    }

    private void assignStateField(Type generatedType, MethodVisitor constructorVisitor) {
        putThisOnStack(constructorVisitor);
        putFirstMethodArgumentOnStack(constructorVisitor);
        constructorVisitor.visitFieldInsn(Opcodes.PUTFIELD, generatedType.getInternalName(), STATE_FIELD_NAME, Type.getDescriptor(ModelElementState.class));
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

    private void writeMethods(ClassVisitor visitor, Type generatedType, Class<?> managedTypeClass) {
        for (Method method : managedTypeClass.getMethods()) {
            if (method.getName().startsWith("get")) {
                writeGetter(visitor, generatedType, method);
            } else if (method.getName().startsWith("set")) {
                writeSetter(visitor, generatedType, method);
            } else {
                String messageFormat = "Unexpected method encountered when generating implementation class for a managed type '%s': %s";
                throw new RuntimeException(String.format(messageFormat, managedTypeClass.getName(), method.toString()));
            }
        }
    }

    private void writeSetter(ClassVisitor visitor, Type generatedType, Method method) {
        String propertyName = getPropertyName(method);

        MethodVisitor methodVisitor = declareMethod(visitor, method);

        putStateFieldValueOnStack(methodVisitor, generatedType);
        putModelTypeOnStack(methodVisitor, method.getGenericParameterTypes()[0]);
        putConstantOnStack(methodVisitor, propertyName);
        putFirstMethodArgumentOnStack(methodVisitor);
        invokeStateSetMethod(methodVisitor);

        finishVisitingMethod(methodVisitor);
    }

    private void invokeStateSetMethod(MethodVisitor methodVisitor) {
        String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(ModelType.class), Type.getType(String.class), Type.getType(Object.class));
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ModelElementState.class), "set", methodDescriptor, true);
    }

    private void putConstantOnStack(MethodVisitor methodVisitor, Object value) {
        methodVisitor.visitLdcInsn(value);
    }

    private MethodVisitor declareMethod(ClassVisitor visitor, Method method) {
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), Type.getMethodDescriptor(method), CONCRETE_SIGNATURE, new String[0]);
        methodVisitor.visitCode();
        return methodVisitor;
    }

    private void putFirstMethodArgumentOnStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 1);
    }

    private void putStateFieldValueOnStack(MethodVisitor methodVisitor, Type generatedType) {
        putThisOnStack(methodVisitor);
        methodVisitor.visitFieldInsn(Opcodes.GETFIELD, generatedType.getInternalName(), STATE_FIELD_NAME, Type.getDescriptor(ModelElementState.class));
    }

    private void putModelTypeOnStack(MethodVisitor methodVisitor, java.lang.reflect.Type type) {
        putTypeReferenceOnStack(methodVisitor, type);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(ModelType.class), "of", Type.getMethodDescriptor(Type.getType(ModelType.class), Type.getType(java.lang.reflect.Type.class)), false);
    }

    private void putTypeReferenceOnStack(MethodVisitor visitor, java.lang.reflect.Type type) {
        if (type == null) {
            visitor.visitInsn(Opcodes.ACONST_NULL);
        } else if (type instanceof Class) {
            visitor.visitLdcInsn(Type.getType((Class) type));
        } else if(type instanceof ParameterizedType) {
            putParametrizedTypeReferenceOnStack(visitor, (ParameterizedType) type);
        }else {
            throw new IllegalArgumentException(String.format("Generating bytecode for reference to type class '%s' is not supported", type.getClass()));
        }
    }

    private void putParametrizedTypeReferenceOnStack(MethodVisitor visitor, ParameterizedType type) {
        visitor.visitTypeInsn(Opcodes.NEW, Type.getInternalName(ParameterizedTypeImpl.class));
        visitor.visitInsn(Opcodes.DUP);
        putTypeReferenceOnStack(visitor, type.getRawType());
        putTypeReferenceOnStack(visitor, type.getOwnerType());
        putTypeArrayOnStack(visitor, type.getActualTypeArguments());
        invokeParametrizedTypeImplConstructor(visitor);
    }

    private void invokeParametrizedTypeImplConstructor(MethodVisitor visitor) {
        String constructorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(java.lang.reflect.Type.class), Type.getType(java.lang.reflect.Type.class), Type.getType(java.lang.reflect.Type[].class));
        visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(ParameterizedTypeImpl.class), "<init>", constructorDescriptor, false);
    }

    private void putTypeArrayOnStack(MethodVisitor visitor, java.lang.reflect.Type[] types) {
        putConstantOnStack(visitor, types.length);
        visitor.visitTypeInsn(Opcodes.ANEWARRAY, Type.getInternalName(java.lang.reflect.Type.class));
        for (int i = 0; i < types.length; i++) {
            visitor.visitInsn(Opcodes.DUP);
            putConstantOnStack(visitor, i);
            putTypeReferenceOnStack(visitor, types[i]);
            visitor.visitInsn(Opcodes.AASTORE);
        }
    }

    private void writeGetter(ClassVisitor visitor, Type generatedType, Method method) {
        String propertyName = getPropertyName(method);

        MethodVisitor methodVisitor = declareMethod(visitor, method);

        putStateFieldValueOnStack(methodVisitor, generatedType);
        putModelTypeOnStack(methodVisitor, method.getGenericReturnType());
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
        String methodDescriptor = Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(ModelType.class), Type.getType(String.class));
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, Type.getInternalName(ModelElementState.class), "get", methodDescriptor, true);
    }
}
