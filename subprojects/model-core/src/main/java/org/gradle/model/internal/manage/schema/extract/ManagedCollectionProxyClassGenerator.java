/*
 * Copyright 2015 the original author or authors.
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

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;

public class ManagedCollectionProxyClassGenerator extends AbstractProxyClassGenerator {
    /**
     * Generates an implementation of the given managed type.
     *
     * <p>The generated type will:</p>
     *
     * <ul>
     *     <li>extend the given implementation class</li>
     *     <li>implement the given public interface</li>
     *     <li>override each public constructor of the given implementation class</li>
     * </ul>
     */
    public Class<?> generate(Class<?> implClass, Class<?> publicContractType) {
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);

        String generatedTypeName = publicContractType.getName() + "_Impl";
        Type generatedType = Type.getType("L" + generatedTypeName.replaceAll("\\.", "/") + ";");

        Type superclassType = Type.getType(implClass);
        Type publicType = Type.getType(publicContractType);

        generateClass(visitor, generatedType, superclassType, publicType);
        generateConstructors(visitor, implClass, superclassType);
        visitor.visitEnd();

        return defineClass(visitor, publicContractType.getClassLoader(), generatedTypeName);
    }

    private <T> void generateConstructors(ClassWriter visitor, Class<? extends T> implClass, Type superclassType) {
        for (Constructor<?> constructor : implClass.getConstructors()) {
            Type[] paramTypes = new Type[constructor.getParameterTypes().length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramTypes[i] = Type.getType(constructor.getParameterTypes()[i]);
            }
            String methodDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, paramTypes);
            MethodVisitor constructorVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, CONSTRUCTOR_NAME, methodDescriptor, CONCRETE_SIGNATURE, NO_EXCEPTIONS);
            constructorVisitor.visitCode();
            putThisOnStack(constructorVisitor);
            for (int i = 0; i < paramTypes.length; i++) {
                constructorVisitor.visitVarInsn(paramTypes[i].getOpcode(Opcodes.ILOAD), i + 1);
            }
            constructorVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), CONSTRUCTOR_NAME, methodDescriptor, false);
            finishVisitingMethod(constructorVisitor);
        }
    }

    private void generateClass(ClassWriter visitor, Type generatedType, Type superclassType, Type publicType) {
        visitor.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null, superclassType.getInternalName(), new String[]{publicType.getInternalName()});
    }
}
