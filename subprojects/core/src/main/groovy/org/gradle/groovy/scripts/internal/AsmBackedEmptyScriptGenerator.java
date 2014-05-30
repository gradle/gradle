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
package org.gradle.groovy.scripts.internal;

import groovy.lang.Script;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.JavaMethod;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Map;

public class AsmBackedEmptyScriptGenerator implements EmptyScriptGenerator {
    private static final Map<Class<?>, Class<?>> CACHED_CLASSES = new HashMap<Class<?>, Class<?>>();

    public <T extends Script> Class<? extends T> generate(Class<T> type) {
        Class<?> subclass = CACHED_CLASSES.get(type);
        if (subclass == null) {
            subclass = generateEmptyScriptClass(type);
            CACHED_CLASSES.put(type, subclass);
        }
        return subclass.asSubclass(type);
    }

    private <T extends Script> Class<? extends T> generateEmptyScriptClass(Class<T> type) {
        ClassWriter visitor = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String typeName = type.getName() + "_Decorated";
        Type generatedType = Type.getType("L" + typeName.replaceAll("\\.", "/") + ";");
        Type superclassType = Type.getType(type);
        visitor.visit(Opcodes.V1_5, Opcodes.ACC_PUBLIC, generatedType.getInternalName(), null,
                superclassType.getInternalName(), new String[0]);

        // Constructor

        String constructorDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, new Type[0]);
        MethodVisitor methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor, null,
                new String[0]);
        methodVisitor.visitCode();

        // super()
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, superclassType.getInternalName(), "<init>",
                constructorDescriptor);

        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        // run() method

        String runDesciptor = Type.getMethodDescriptor(Type.getType(Object.class), new Type[0]);
        methodVisitor = visitor.visitMethod(Opcodes.ACC_PUBLIC, "run", runDesciptor, null, new String[0]);
        methodVisitor.visitCode();

        // return null
        methodVisitor.visitInsn(Opcodes.ACONST_NULL);

        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();

        visitor.visitEnd();

        byte[] bytecode = visitor.toByteArray();
        JavaMethod<ClassLoader, Class> method = JavaReflectionUtil.method(ClassLoader.class, Class.class, "defineClass", String.class, byte[].class, int.class, int.class);
        @SuppressWarnings("unchecked")
        Class<T> clazz = method.invoke(type.getClassLoader(), typeName, bytecode, 0, bytecode.length);
        return clazz;
    }
}
