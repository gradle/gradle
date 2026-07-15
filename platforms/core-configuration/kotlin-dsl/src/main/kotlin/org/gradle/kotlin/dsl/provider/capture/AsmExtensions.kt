/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.kotlin.dsl.provider.capture

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode

// Generic ASM tree-API vocabulary shared across the capture-minimization phases.

internal fun ClassNode.findMethod(name: String): MethodNode? = methods.firstOrNull { it.name == name }

/** Method-level invariant: the named method must exist (proven by an earlier analysis phase). */
internal fun ClassNode.requireMethod(name: String): MethodNode =
    findMethod(name) ?: error("$name not found on ${this.name}")

/** All `invokespecial <init>` instructions in this list — i.e. every constructor call. */
internal fun InsnList.constructorCalls(): List<MethodInsnNode> =
    filterIsInstance<MethodInsnNode>().filter { it.opcode == Opcodes.INVOKESPECIAL && it.name == "<init>" }

/** The nearest preceding real (non-pseudo) instruction, skipping labels/line-numbers/frames. */
internal fun AbstractInsnNode.previousReal(): AbstractInsnNode? =
    generateSequence(previous) { it.previous }.firstOrNull { it.opcode >= 0 }

/** Instruction-level invariant: a preceding real instruction must exist (proven by an earlier phase). */
internal fun AbstractInsnNode.requirePreviousReal(): AbstractInsnNode =
    previousReal() ?: error("expected a preceding instruction")

private operator fun Int.contains(flag: Int): Boolean = this and flag == flag
internal val Int.isStatic: Boolean get() = Opcodes.ACC_STATIC in this
internal val Int.isFinal: Boolean get() = Opcodes.ACC_FINAL in this

/** True if the instruction writes to `this` (i.e. it is preceded by `aload_0; <single value push>`). */
internal fun FieldInsnNode.writesToThis(): Boolean {
    val value = previousReal() ?: return false
    return value.previousReal().isAload0()
}

internal fun AbstractInsnNode?.isAload0(): Boolean =
    this != null && opcode == Opcodes.ALOAD && (this as VarInsnNode).`var` == 0
