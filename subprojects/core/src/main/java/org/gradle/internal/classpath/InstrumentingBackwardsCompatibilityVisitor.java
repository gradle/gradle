/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.internal.Pair;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ASM7;

class InstrumentingBackwardsCompatibilityVisitor extends ClassVisitor {

    private static final List<Pair<String, String>> RENAMED_TYPE_INTERNAL_NAMES = asList(
        Pair.of("org/gradle/logging/LoggingManagerInternal", "org/gradle/api/logging/LoggingManager"),
        Pair.of("org/gradle/logging/StandardOutputCapture", "org/gradle/internal/logging/StandardOutputCapture")
    );

    private static final List<Pair<String, String>> RENAMED_TYPE_DESCRIPTORS = RENAMED_TYPE_INTERNAL_NAMES.stream().map(
        p -> Pair.of("L" + p.left + ";", "L" + p.right + ";")
    ).collect(toList());

    InstrumentingBackwardsCompatibilityVisitor(ClassVisitor classVisitor) {
        super(ASM7, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        String newDescriptor = fixSyntheticBridgeMethodDescriptor(access, descriptor);
        MethodVisitor methodVisitor = super.visitMethod(access, name, newDescriptor, signature, exceptions);
        return methodVisitor != null
            ? new BackwardCompatibilityMethodVisitor(methodVisitor)
            : null;
    }

    private static class BackwardCompatibilityMethodVisitor extends MethodVisitor {

        public BackwardCompatibilityMethodVisitor(MethodVisitor methodVisitor) {
            super(ASM7, methodVisitor);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            final String newOwner = fixInternalNameForBackwardCompatibility(owner);
            final String newDescriptor = fixDescriptorForBackwardCompatibility(descriptor);
            super.visitMethodInsn(opcode, newOwner, name, newDescriptor, isInterface);
        }
    }

    private String fixSyntheticBridgeMethodDescriptor(int access, String descriptor) {
        // Restore compatibility with plugins compiled with an old Groovy version (like org.samples.greeting:1.0 used by the GE build)
        // in which super class getters are accessed via bridge methods.
        return (access & ACC_SYNTHETIC) != 0 && descriptor.startsWith("()")
            ? fixDescriptorForBackwardCompatibility(descriptor)
            : descriptor;
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
