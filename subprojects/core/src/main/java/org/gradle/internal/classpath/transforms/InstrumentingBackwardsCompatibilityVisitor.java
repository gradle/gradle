/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.internal.Pair;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;

class InstrumentingBackwardsCompatibilityVisitor extends ClassVisitor {

    private static final List<Pair<String, String>> RENAMED_TYPE_INTERNAL_NAMES = asList(
        Pair.of("org/gradle/logging/LoggingManagerInternal", "org/gradle/api/logging/LoggingManager"),
        Pair.of("org/gradle/logging/StandardOutputCapture", "org/gradle/internal/logging/StandardOutputCapture")
    );

    private static final List<Pair<String, String>> RENAMED_TYPE_DESCRIPTORS = RENAMED_TYPE_INTERNAL_NAMES.stream().map(
        p -> Pair.of("L" + p.left + ";", "L" + p.right + ";")
    ).collect(toList());

    InstrumentingBackwardsCompatibilityVisitor(ClassVisitor classVisitor) {
        super(ASM_LEVEL, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        String newSuperName = fixInternalNameForBackwardCompatibility(superName);
        String[] newInterfaces = fixInternalNamesForBackwardsCompatibility(interfaces);
        super.visit(version, access, name, signature, newSuperName, newInterfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
        return super.visitField(access, name, newDescriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
        MethodVisitor methodVisitor = super.visitMethod(access, name, newDescriptor, signature, exceptions);
        return methodVisitor != null
            ? new BackwardCompatibilityMethodVisitor(methodVisitor)
            : null;
    }

    private static class BackwardCompatibilityMethodVisitor extends MethodVisitor {

        public BackwardCompatibilityMethodVisitor(MethodVisitor methodVisitor) {
            super(ASM_LEVEL, methodVisitor);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            String newType = fixInternalNameForBackwardCompatibility(type);
            super.visitTypeInsn(opcode, newType);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            final String newOwner = fixInternalNameForBackwardCompatibility(owner);
            final String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
            super.visitFieldInsn(opcode, newOwner, name, newDescriptor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            final String newOwner = fixInternalNameForBackwardCompatibility(owner);
            final String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
            super.visitMethodInsn(opcode, newOwner, name, newDescriptor, isInterface);
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
            super.visitMultiANewArrayInsn(newDescriptor, numDimensions);
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            final String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
            super.visitLocalVariable(name, newDescriptor, signature, start, end, index);
        }

        @Override
        public void visitLdcInsn(Object value) {
            Object newValue = fixAsmObjectForBackwardsCompatibility(value);
            super.visitLdcInsn(newValue);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
            Handle newBootstrapMethodHandle = fixHandleForBackwardsCompatibility(bootstrapMethodHandle);
            Object[] newBootstrapMethodArguments = fixAsmObjectsForBackwardsCompatibility(bootstrapMethodArguments);
            super.visitInvokeDynamicInsn(name, newDescriptor, newBootstrapMethodHandle, newBootstrapMethodArguments);
        }
    }

    private static Object[] fixAsmObjectsForBackwardsCompatibility(Object[] values) {
        Object[] newValues = new Object[values.length];
        for (int idx = 0; idx < values.length; idx++) {
            newValues[idx] = fixAsmObjectForBackwardsCompatibility(values[idx]);
        }
        return newValues;
    }

    private static Object fixAsmObjectForBackwardsCompatibility(Object value) {
        if (value instanceof Type) {
            Type type = (Type) value;
            String newDescriptor = fixDescriptorForBackwardCompatibility(type.getDescriptor());
            return Type.getType(newDescriptor);
        } else if (value instanceof Handle) {
            Handle handle = (Handle) value;
            return fixHandleForBackwardsCompatibility(handle);
        } else {
            return value;
        }
    }

    private static Handle fixHandleForBackwardsCompatibility(Handle handle) {
        String newHandleOwner = fixInternalNameForBackwardCompatibility(handle.getOwner());
        String newHandleDescriptor = fixDescriptorForBackwardCompatibility(handle.getDesc());
        return new Handle(handle.getTag(), newHandleOwner, handle.getName(), newHandleDescriptor, handle.isInterface());
    }

    private static String[] fixInternalNamesForBackwardsCompatibility(String[] internalNames) {
        String[] newInternalNames = new String[internalNames.length];
        for (int idx = 0; idx < internalNames.length; idx++) {
            newInternalNames[idx] = fixInternalNameForBackwardCompatibility(internalNames[idx]);
        }
        return newInternalNames;
    }

    private static String fixInternalNameForBackwardCompatibility(String internalName) {
        // Fix renamed type references
        for (Pair<String, String> renamedInterface : RENAMED_TYPE_INTERNAL_NAMES) {
            if (renamedInterface.left.equals(internalName)) {
                return renamedInterface.right;
            }
        }
        return internalName;
    }

    private static String fixDescriptorForBackwardCompatibility(String descriptor) {
        // Fix method signatures involving renamed types
        for (Pair<String, String> renamedDescriptor : RENAMED_TYPE_DESCRIPTORS) {
            descriptor = descriptor.replace(renamedDescriptor.left, renamedDescriptor.right);
        }
        return descriptor;
    }
}
