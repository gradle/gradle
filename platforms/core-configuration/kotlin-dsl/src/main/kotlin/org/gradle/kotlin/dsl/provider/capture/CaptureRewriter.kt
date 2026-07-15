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

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Files
import java.util.SortedSet

/**
 * Applies a [RewritePlan] to the bytecode: reshapes each rewritable lambda to carry shared cells instead
 * of the script, fixes every site that constructs such a lambda, gives each captured top-level field a
 * shared cell on its script, and writes the mutated classes back. The set of classes it touches is kept
 * here rather than in [ClassesScope]; the only result it produces is the number of lambdas de-captured.
 */
internal class CaptureRewriter(
    private val classesScope: ClassesScope,
    private val plan: RewritePlan,
) {
    /** Classes this rewrite mutates, collected here and written out at the end. */
    private val changed = mutableSetOf<InternalClassName>()

    /** Rewrites every planned lambda and its script in place, returning the number of lambdas de-captured. */
    fun rewriteAndWrite(): Int {
        plan.rewritableLambdas.forEach { rewritable ->
            rewriteLambdaShape(rewritable)
            changed += rewritable.lambda.internalClassName
        }
        plan.rewritableLambdas.forEach { rewritable ->
            rewritable.sites.forEach { site ->
                rewriteCreationSite(rewritable, site)
                changed += site.inOwnerClass.internalName
            }
        }
        rewriteScripts()
        changed.forEach { writeClass(classesScope.classes.getValue(it)) }
        return plan.rewritableLambdas.size
    }

    private fun scriptModelOf(rewritable: RewritableLambda): ScriptModel =
        classesScope.scripts.getValue(rewritable.lambda.receiverType)

    // ---- lambda side: replace the captured script with one shared cell per captured item ----

    private fun rewriteLambdaShape(rewritable: RewritableLambda) {
        val model = scriptModelOf(rewritable)
        val lambdaClass = rewritable.lambda.classNode
        lambdaClass.fields.removeAll { it.name == THIS0 }
        for (item in rewritable.capturedOrder) {
            lambdaClass.fields.add(
                FieldNode(Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC, CM_PREFIX + item, model.cellDescriptorFor(item), null, null)
            )
        }
        regenerateConstructor(rewritable, model)
        rewriteBodyAccesses(rewritable, model)
    }

    private fun regenerateConstructor(rewritable: RewritableLambda, model: ScriptModel) {
        val lambdaClass = rewritable.lambda.classNode
        val params = constructorParams(rewritable, model)
        val ctor = MethodNode(Opcodes.ACC_PUBLIC, "<init>", params.toDescriptor(), null, null)
        ctor.instructions.apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(Opcodes.INVOKESPECIAL, lambdaClass.superName, "<init>", "()V", false))
            var slot = 1
            for ((name, desc) in params) {
                val type = Type.getType(desc)
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot))
                add(FieldInsnNode(Opcodes.PUTFIELD, lambdaClass.name, name, desc))
                slot += type.size
            }
            add(InsnNode(Opcodes.RETURN))
        }
        val methods = lambdaClass.methods
        methods[methods.indexOfFirst { it.name == "<init>" }] = ctor
    }

    /**
     * Rewrites each classified access so the captured shared cell is used instead of the script: the
     * `getfield this$0` that produced the receiver loads the cell, a read becomes `cell.element`
     * (plus a checkcast for object cells), and a write becomes `cell.element = ...`.
     */
    private fun rewriteBodyAccesses(rewritable: RewritableLambda, model: ScriptModel) {
        val lambdaName = rewritable.lambda.classNode.name
        for ((method, accesses) in rewritable.accessesByMethod) {
            val instructions = method.instructions
            for (access in accesses) {
                val cell = cellFor(model.fieldDesc(access.field))
                for (source in access.receiverSources) {
                    instructions.set(source, FieldInsnNode(Opcodes.GETFIELD, lambdaName, CM_PREFIX + access.field, cell.descriptor))
                }
                when (access.kind) {
                    Access.Kind.VAR_WRITE ->
                        instructions.set(access.call, FieldInsnNode(Opcodes.PUTFIELD, cell.internalName, "element", cell.elementDesc))
                    Access.Kind.VAL_READ, Access.Kind.VAR_READ ->
                        instructions.replaceWithCellRead(access.call, cell, Type.getType(model.fieldDesc(access.field)).internalName)
                }
            }
        }
    }

    // ---- creation sites: drop the script push, push the shared cells, fix the constructor descriptor ----

    private fun rewriteCreationSite(rewritable: RewritableLambda, site: LambdaInstantiationSite) {
        val model = scriptModelOf(rewritable)
        val instructions = site.inMethod.instructions
        removeScriptPush(instructions, site)
        // Each item's shared cell: the script's own cell (script context) or the enclosing lambda's
        // captured cell (lambda context) — both named cm$<item>.
        val cellOwner = if (site.isInImmediateScriptClass) rewritable.lambda.receiverType.className else site.inOwnerClass.name
        val push = InsnList().apply {
            for (item in rewritable.capturedOrder) {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(FieldInsnNode(Opcodes.GETFIELD, cellOwner, CM_PREFIX + item, model.cellDescriptorFor(item)))
            }
        }
        instructions.insertBefore(site.initInsn, push)
        site.initInsn.desc = constructorParams(rewritable, model).toDescriptor()
    }

    private fun removeScriptPush(instructions: InsnList, site: LambdaInstantiationSite) {
        // Undo the clean script-push suffix validated by isCleanScriptPushSuffix.
        val scriptPush = site.initInsn.requirePreviousReal()
        if (site.isInImmediateScriptClass) {
            instructions.remove(scriptPush) // aload_0 (the script `this`)
        } else {
            val aload0 = scriptPush.requirePreviousReal()
            instructions.remove(scriptPush) // getfield this$0
            instructions.remove(aload0) // aload_0
        }
    }

    // ---- script side: give each captured top-level field a shared cell ----

    private fun rewriteScripts() {
        for ((scriptName, fields) in fieldsToConvertByScript()) {
            val scriptClass = classesScope.classes.getValue(scriptName)
            val model = classesScope.scripts.getValue(scriptName)
            fields.forEach { convertFieldToCell(scriptClass, model, it) }
            changed += scriptName
        }
    }

    private fun fieldsToConvertByScript(): Map<InternalClassName, SortedSet<String>> {
        val result = mutableMapOf<InternalClassName, SortedSet<String>>()
        for (rewritable in plan.rewritableLambdas) {
            for (item in rewritable.capturedOrder) {
                result.getOrPut(rewritable.lambda.receiverType) { sortedSetOf() }.add(item)
            }
        }
        return result
    }

    /**
     * Adds a shared cell `cm$field` to the script, seeded from the field's assigned value in `<init>`
     * (before any capturing lambda is created). A var also routes its getter/setter through the cell so
     * writes are observed; a val's getter is left reading the original (immutable) field, whose value
     * equals the cell's. The original field is retained either way.
     */
    private fun convertFieldToCell(scriptClass: ClassNode, model: ScriptModel, field: String) {
        val descriptor = model.fieldDesc(field)
        val cell = cellFor(descriptor)
        val cellField = CM_PREFIX + field
        scriptClass.fields.add(FieldNode(Opcodes.ACC_PRIVATE or Opcodes.ACC_SYNTHETIC, cellField, cell.descriptor, null, null))
        seedCellInInit(scriptClass, cell, field, cellField, descriptor)
        if (model.isVar(field)) {
            routeVarGetterThroughCell(scriptClass, cell, field, cellField, descriptor)
            routeVarSetterThroughCell(scriptClass, cell, field, cellField, descriptor)
        }
    }

    /** In `<init>`, right after the field's own assignment (or after super() if it is JVM-defaulted),
     *  insert `this.cm$field = new Cell().apply { element = this.field }`. */
    private fun seedCellInInit(scriptClass: ClassNode, cell: Cell, field: String, cellField: String, descriptor: String) {
        val init = scriptClass.requireMethod("<init>")
        val anchor = fieldAssignmentIn(init, scriptClass, field, descriptor) ?: superConstructorCall(init, scriptClass.superName)
        val seed = InsnList().apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(TypeInsnNode(Opcodes.NEW, cell.internalName))
            add(InsnNode(Opcodes.DUP))
            add(MethodInsnNode(Opcodes.INVOKESPECIAL, cell.internalName, "<init>", "()V", false))
            add(InsnNode(Opcodes.DUP))
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(FieldInsnNode(Opcodes.GETFIELD, scriptClass.name, field, descriptor))
            add(FieldInsnNode(Opcodes.PUTFIELD, cell.internalName, "element", cell.elementDesc))
            add(FieldInsnNode(Opcodes.PUTFIELD, scriptClass.name, cellField, cell.descriptor))
        }
        init.instructions.insert(anchor, seed)
    }

    /** Routes the var getter through the cell: `getfield field` -> `getfield cm$field; getfield element[; checkcast]`. */
    private fun routeVarGetterThroughCell(scriptClass: ClassNode, cell: Cell, field: String, cellField: String, descriptor: String) {
        val getter = scriptClass.requireMethod(field.getterName())
        val fieldRead = getter.instructions.requireFieldAccess(Opcodes.GETFIELD, scriptClass.name, field, descriptor)
        fieldRead.name = cellField
        fieldRead.desc = cell.descriptor
        val cellRead = InsnList().apply {
            add(FieldInsnNode(Opcodes.GETFIELD, cell.internalName, "element", cell.elementDesc))
            if (cell.isObject) add(TypeInsnNode(Opcodes.CHECKCAST, Type.getType(descriptor).internalName))
        }
        getter.instructions.insert(fieldRead, cellRead)
    }

    /** Routes the var setter through the cell: `aload_0; <value>; putfield field`
     *  -> `aload_0; getfield cm$field; <value>; putfield cell.element`. */
    private fun routeVarSetterThroughCell(scriptClass: ClassNode, cell: Cell, field: String, cellField: String, descriptor: String) {
        val setter = scriptClass.requireMethod(field.setterName())
        val fieldWrite = setter.instructions.requireFieldAccess(Opcodes.PUTFIELD, scriptClass.name, field, descriptor)
        val value = fieldWrite.requirePreviousReal()
        val receiver = value.requirePreviousReal()
        setter.instructions.insert(receiver, FieldInsnNode(Opcodes.GETFIELD, scriptClass.name, cellField, cell.descriptor))
        setter.instructions.set(fieldWrite, FieldInsnNode(Opcodes.PUTFIELD, cell.internalName, "element", cell.elementDesc))
    }

    private fun writeClass(classNode: ClassNode) {
        // COMPUTE_MAXS only: the frames are the compiler's, preserved through our frame-neutral edits,
        // so ASM neither recomputes them nor needs to load any class to find common supertypes.
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        classNode.accept(writer)
        val file = classesScope.dir.resolve(classNode.name + ".class")
        Files.createDirectories(file.parent)
        Files.write(file, writer.toByteArray())
    }

    private fun constructorParams(rewritable: RewritableLambda, model: ScriptModel): List<Pair<String, String>> =
        buildList {
            rewritable.lambda.otherFields.forEach { add(it.name to it.desc) }
            rewritable.capturedOrder.forEach { add(CM_PREFIX + it to model.cellDescriptorFor(it)) }
        }
}

private fun List<Pair<String, String>>.toDescriptor(): String =
    joinToString(separator = "", prefix = "(", postfix = ")V") { it.second }

/** Replaces a getter call with a read of the cell's `element` (plus a checkcast for object cells). */
private fun InsnList.replaceWithCellRead(call: AbstractInsnNode, cell: Cell, castTypeInternalName: String) {
    val read = InsnList().apply {
        add(FieldInsnNode(Opcodes.GETFIELD, cell.internalName, "element", cell.elementDesc))
        if (cell.isObject) add(TypeInsnNode(Opcodes.CHECKCAST, castTypeInternalName))
    }
    insertBefore(call, read)
    remove(call)
}

/** The first field instruction in this list with the given opcode/owner/name/descriptor, or null. */
private fun InsnList.fieldAccess(opcode: Int, owner: String, name: String, descriptor: String): FieldInsnNode? =
    firstOrNull { it is FieldInsnNode && it.opcode == opcode && it.owner == owner && it.name == name && it.desc == descriptor } as FieldInsnNode?

/** Instruction-level invariant: the accessor's field access, proven present by an earlier phase, must exist. */
private fun InsnList.requireFieldAccess(opcode: Int, owner: String, name: String, descriptor: String): FieldInsnNode =
    fieldAccess(opcode, owner, name, descriptor)
        ?: error("expected a '$name' field access in its accessor (proven convertible by an earlier phase)")

private fun fieldAssignmentIn(init: MethodNode, scriptClass: ClassNode, field: String, descriptor: String): AbstractInsnNode? =
    init.instructions.fieldAccess(Opcodes.PUTFIELD, scriptClass.name, field, descriptor)

private fun superConstructorCall(init: MethodNode, superName: String): AbstractInsnNode =
    init.instructions.firstOrNull {
        it is MethodInsnNode && it.opcode == Opcodes.INVOKESPECIAL && it.name == "<init>" && it.owner == superName
    } ?: init.instructions.first
