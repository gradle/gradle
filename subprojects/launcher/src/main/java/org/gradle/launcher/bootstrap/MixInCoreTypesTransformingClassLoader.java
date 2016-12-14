/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.bootstrap;

import org.gradle.internal.classloader.TransformingClassLoader;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.util.internal.Java9ClassReader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ILOAD;

/**
 * Mixes in internal methods to core Gradle types.
 */
public class MixInCoreTypesTransformingClassLoader extends TransformingClassLoader {
    private static final Map<String, VisitorFactory> TRANSFORMED_TYPES;

    static {
        Map<String, VisitorFactory> transformedTypes = new HashMap<String, VisitorFactory>();
        transformedTypes.put("org.gradle.api.internal.AbstractTask", new VisitorFactory() {
            @Override
            public ClassVisitor createVisitor(ClassVisitor parent) {
                return new AbstractTaskTransformer(parent);
            }
        });
        transformedTypes.put("org.gradle.api.internal.DefaultTaskInputs", new VisitorFactory() {
            @Override
            public ClassVisitor createVisitor(ClassVisitor parent) {
                return new DefaultTaskInputsTransformer(parent);
            }
        });
        TRANSFORMED_TYPES = transformedTypes;
    }

    public MixInCoreTypesTransformingClassLoader(ClassLoader parent, ClassPath classPath) {
        super(parent, classPath);
    }

    @Override
    protected boolean shouldTransform(String className) {
        return TRANSFORMED_TYPES.containsKey(className);
    }

    @Override
    protected byte[] transform(String className, byte[] bytes) {
        ClassReader classReader = new Java9ClassReader(bytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        ClassVisitor visitor = TRANSFORMED_TYPES.get(className).createVisitor(classWriter);
        classReader.accept(visitor, 0);
        return classWriter.toByteArray();
    }

    /**
     * Adds overrides with internal return types for {@link org.gradle.api.Task#getInputs()} and
     * {@link org.gradle.api.Task#getOutputs()} to {@link org.gradle.api.internal.AbstractTask}.
     */
    private static class AbstractTaskTransformer extends ClassVisitor {
        public AbstractTaskTransformer(ClassVisitor cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public void visitEnd() {
            addBridgeMethod(cv,
                getType("org.gradle.api.internal.TaskInputsInternal"),
                "getInputs",
                getType("org.gradle.api.Task"),
                getType("org.gradle.api.tasks.TaskInputs")
            );
            addBridgeMethod(cv,
                getType("org.gradle.api.internal.TaskOutputsInternal"),
                "getOutputs",
                getType("org.gradle.api.Task"),
                getType("org.gradle.api.tasks.TaskOutputs")
            );
            super.visitEnd();
        }
    }

    /**
     * Adds overrides with internal return types for {@link org.gradle.api.tasks.TaskInputs#file()} and
     * {@code dir()} and {@code files()} to {@link org.gradle.api.internal.tasks.DefaultTaskInputs}.
     */
    private static class DefaultTaskInputsTransformer extends ClassVisitor {
        public DefaultTaskInputsTransformer(ClassVisitor cv) {
            super(Opcodes.ASM5, cv);
        }

        @Override
        public void visitEnd() {
            addBridgeMethod(cv,
                getType("org.gradle.api.internal.TaskInputFilePropertyBuilderInternal"),
                "file",
                getType("org.gradle.api.tasks.TaskInputs"),
                getType("org.gradle.api.tasks.TaskInputFilePropertyBuilder"),
                Type.getType(Object.class)
            );
            addBridgeMethod(cv,
                getType("org.gradle.api.internal.TaskInputFilePropertyBuilderInternal"),
                "dir",
                getType("org.gradle.api.tasks.TaskInputs"),
                getType("org.gradle.api.tasks.TaskInputFilePropertyBuilder"),
                Type.getType(Object.class));
            addBridgeMethod(cv,
                getType("org.gradle.api.internal.TaskInputFilePropertyBuilderInternal"),
                "files",
                getType("org.gradle.api.tasks.TaskInputs"),
                getType("org.gradle.api.tasks.TaskInputFilePropertyBuilder"),
                Type.getType(Object[].class));
            super.visitEnd();
        }
    }

    private static void addBridgeMethod(ClassVisitor cv, Type internalType, String method, Type publicInterface, Type publicType, Type... argumentTypes) {
        MethodVisitor methodVisitor = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE, method, Type.getMethodDescriptor(internalType, argumentTypes), null, null);
        methodVisitor.visitCode();
        putThisOnStack(methodVisitor);
        for (int index = 0, len = argumentTypes.length; index < len; index++) {
            putMethodArgumentOnStack(methodVisitor, argumentTypes[index], index);
        }
        methodVisitor.visitMethodInsn(Opcodes.INVOKEINTERFACE, publicInterface.getInternalName(), method, Type.getMethodDescriptor(publicType, argumentTypes), true);
        methodVisitor.visitInsn(Opcodes.ARETURN);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    private static void putThisOnStack(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
    }

    private static void putMethodArgumentOnStack(MethodVisitor methodVisitor, Type type, int index) {
        methodVisitor.visitVarInsn(type.getOpcode(ILOAD), index + 1);
    }

    private static Type getType(String name) {
        return Type.getType("L" + name.replace('.', '/') + ";");
    }

    private interface VisitorFactory {
        ClassVisitor createVisitor(ClassVisitor parent);
    }
}
