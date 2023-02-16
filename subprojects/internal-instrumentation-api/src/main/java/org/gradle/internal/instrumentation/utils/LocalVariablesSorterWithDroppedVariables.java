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

package org.gradle.internal.instrumentation.utils;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.LocalVariablesSorter;

import java.util.Arrays;

/**
 * This is a workaround for ASM {@link LocalVariablesSorter}'s issue with the scope of the variables introduced with {@link LocalVariablesSorter#newLocal(Type)}.
 * Those variables are added to all the method stack map frames following the creation of the variables. It may happen that there is a jump from an instructions
 * that is located earlier in the method to a frame that is visited after the variable has been created. In that case, the former frame will not have a variable
 * that will be assignable to the latter frame's variable, and that will result in a verification error.
 * <p>
 * This can be fixed by also tracking the variables that are out of scope and setting their type to {@link Opcodes#TOP} (meaning a variable that cannot be
 * used) in all frames that get visited after {@link LocalVariablesSorterWithDroppedVariables#dropLocal(int)} has been called.
 * <p>
 * A user of this class needs to also hold a reference to the original {@link MethodVisitor} and use that instead to visit the instructions
 * that operate with variables introduced with {@link LocalVariablesSorter#newLocal}.
 */
// TODO: it should be possible to map the variables in a way that will not require using the original MethodVisitor to work with the introduced variables
//       (e.g. use negatives for new variables);
//       this could greatly simplify the use-site code but would require re-implementing LocalVariablesSorter from scratch.
public class LocalVariablesSorterWithDroppedVariables extends MethodVisitor {
    public static LocalVariablesSorterWithDroppedVariables create(int access, String descriptor, MethodVisitor methodVisitor) {
        FrameVariableDroppingVisitor droppingVisitor = new FrameVariableDroppingVisitor(methodVisitor);
        LocalVariablesSorter localVariablesSorter = new LocalVariablesSorter(access, descriptor, droppingVisitor);
        return new LocalVariablesSorterWithDroppedVariables(localVariablesSorter, droppingVisitor);
    }

    private LocalVariablesSorterWithDroppedVariables(LocalVariablesSorter localVariablesSorter, FrameVariableDroppingVisitor droppingVisitor) {
        super(Opcodes.ASM9, localVariablesSorter);
        this.localVariablesSorter = localVariablesSorter;
        this.droppingVisitor = droppingVisitor;
    }

    private final LocalVariablesSorter localVariablesSorter;
    private final FrameVariableDroppingVisitor droppingVisitor;

    public int newLocal(Type type) {
        return localVariablesSorter.newLocal(type);
    }

    public void dropLocal(int index) {
        if (index > droppingVisitor.isVariableDropped.length) {
            droppingVisitor.isVariableDropped = Arrays.copyOf(droppingVisitor.isVariableDropped, Integer.max(index + 1, droppingVisitor.isVariableDropped.length * 2));
        }
        droppingVisitor.isVariableDropped[index] = true;
    }

    @Override
    public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
        super.visitFrame(type, numLocal, local, numStack, stack);
    }

    private static class FrameVariableDroppingVisitor extends MethodVisitor {
        boolean[] isVariableDropped = new boolean[20];

        protected FrameVariableDroppingVisitor(MethodVisitor methodVisitor) {
            super(Opcodes.ASM6, methodVisitor);
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            boolean anyDropped = false;
            for (boolean variableDropped : isVariableDropped) {
                if (variableDropped) {
                    anyDropped = true;
                    break;
                }
            }
            if (anyDropped) {
                Object[] localWithDropped = Arrays.copyOf(local, local.length);
                for (int i = 0; i < isVariableDropped.length && i < localWithDropped.length; i++) {
                    if (isVariableDropped[i]) {
                        localWithDropped[i] = Opcodes.TOP;
                    }
                }
                super.visitFrame(type, numLocal, localWithDropped, numStack, stack);
            } else {
                super.visitFrame(type, numLocal, local, numStack, stack);
            }
        }
    }
}
