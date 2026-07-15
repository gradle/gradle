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
 * Applies the decisions made in [World] to the bytecode: reshapes each rewritable lambda to carry
 * shared cells instead of the script, fixes every site that constructs such a lambda, gives each
 * captured top-level field a shared cell on the script, and writes the mutated classes back.
 */
internal class CaptureRewriter(private val world: World) {

    /** Rewrites all decided lambdas and their scripts in place, returning the number of lambdas de-captured. */
    fun rewriteAndWrite(): Int {
        val rewritten = world.lambdas.values.filter { it.rewritable }
        rewritten.forEach { lambda ->
            rewriteLambdaShape(lambda)
            world.changed.add(lambda.name)
        }
        rewritten.forEach { lambda ->
            world.sitesFor(lambda).forEach { site ->
                rewriteCreationSite(lambda, site)
                world.changed.add(site.ownerClass.name)
            }
        }
        rewriteScripts()
        world.changed.forEach { writeClass(world.classes.getValue(it)) }
        return rewritten.size
    }

    // ---- lambda side: replace the captured script with one shared cell per captured item ----

    private fun rewriteLambdaShape(lambda: Lambda) {
        val model = world.scripts.getValue(lambda.scriptType)
        lambda.classNode.fields.removeAll { it.name == THIS0 }
        for (item in lambda.order) {
            lambda.classNode.fields.add(
                FieldNode(Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC, CM_PREFIX + item, model.cellDescriptorFor(item), null, null)
            )
        }
        regenerateConstructor(lambda, model)
        rewriteBodyAccesses(lambda, model)
    }

    private fun regenerateConstructor(lambda: Lambda, model: ScriptModel) {
        val params = constructorParams(lambda, model)
        val ctor = MethodNode(Opcodes.ACC_PUBLIC, "<init>", params.toDescriptor(), null, null)
        ctor.instructions.apply {
            add(VarInsnNode(Opcodes.ALOAD, 0))
            add(MethodInsnNode(Opcodes.INVOKESPECIAL, lambda.classNode.superName, "<init>", "()V", false))
            var slot = 1
            for ((name, desc) in params) {
                val type = Type.getType(desc)
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot))
                add(FieldInsnNode(Opcodes.PUTFIELD, lambda.name, name, desc))
                slot += type.size
            }
            add(InsnNode(Opcodes.RETURN))
        }
        val methods = lambda.classNode.methods
        methods[methods.indexOfFirst { it.name == "<init>" }] = ctor
    }

    /**
     * Rewrites each classified access so the captured shared cell is used instead of the script: the
     * `getfield this$0` that produced the receiver loads the cell, a read becomes `cell.element`
     * (plus a checkcast for object cells), and a write becomes `cell.element = ...`.
     */
    private fun rewriteBodyAccesses(lambda: Lambda, model: ScriptModel) {
        for ((method, accesses) in lambda.accessesByMethod) {
            val instructions = method.instructions
            for (access in accesses) {
                val cell = cellFor(model.fieldDesc(access.field))
                for (source in access.receiverSources) {
                    instructions.set(source, FieldInsnNode(Opcodes.GETFIELD, lambda.name, CM_PREFIX + access.field, cell.descriptor))
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

    private fun rewriteCreationSite(lambda: Lambda, site: Site) {
        val model = world.scripts.getValue(lambda.scriptType)
        val instructions = site.method.instructions
        removeScriptPush(instructions, site)
        // Each item's shared cell: the script's own cell (script context) or the enclosing lambda's
        // captured cell (lambda context) — both named cm$<item>.
        val cellOwner = if (site.scriptContext) lambda.scriptType else site.ownerClass.name
        val push = InsnList().apply {
            for (item in lambda.order) {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(FieldInsnNode(Opcodes.GETFIELD, cellOwner, CM_PREFIX + item, model.cellDescriptorFor(item)))
            }
        }
        instructions.insertBefore(site.init, push)
        site.init.desc = constructorParams(lambda, model).toDescriptor()
    }

    private fun removeScriptPush(instructions: InsnList, site: Site) {
        // Undo the clean script-push suffix validated by isCleanScriptPushSuffix.
        val scriptPush = site.init.requirePreviousReal()
        if (site.scriptContext) {
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
            val scriptClass = world.classes.getValue(scriptName)
            val model = world.scripts.getValue(scriptName)
            fields.forEach { convertFieldToCell(scriptClass, model, it) }
            world.changed.add(scriptClass.name)
        }
    }

    private fun fieldsToConvertByScript(): Map<String, SortedSet<String>> {
        val result = mutableMapOf<String, SortedSet<String>>()
        for (lambda in world.lambdas.values) {
            if (!lambda.rewritable) continue
            for (item in lambda.order) {
                result.getOrPut(lambda.scriptType) { sortedSetOf() }.add(item)
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
        val file = world.dir.resolve(classNode.name + ".class")
        Files.createDirectories(file.parent)
        Files.write(file, writer.toByteArray())
    }

    private fun constructorParams(lambda: Lambda, model: ScriptModel): List<Pair<String, String>> =
        buildList {
            lambda.otherFields.forEach { add(it.name to it.desc) }
            lambda.order.forEach { add(CM_PREFIX + it to model.cellDescriptorFor(it)) }
        }
}

/** A `kotlin.jvm.internal.Ref$XxxRef` mutable cell holding a captured field's value in its `element`. */
internal data class Cell(val internalName: String, val elementDesc: String, val isObject: Boolean) {
    val descriptor: String get() = "L$internalName;"
}

internal fun cellFor(fieldDesc: String): Cell = when (fieldDesc) {
    "I" -> Cell("kotlin/jvm/internal/Ref\$IntRef", "I", false)
    "J" -> Cell("kotlin/jvm/internal/Ref\$LongRef", "J", false)
    "Z" -> Cell("kotlin/jvm/internal/Ref\$BooleanRef", "Z", false)
    "B" -> Cell("kotlin/jvm/internal/Ref\$ByteRef", "B", false)
    "C" -> Cell("kotlin/jvm/internal/Ref\$CharRef", "C", false)
    "S" -> Cell("kotlin/jvm/internal/Ref\$ShortRef", "S", false)
    "F" -> Cell("kotlin/jvm/internal/Ref\$FloatRef", "F", false)
    "D" -> Cell("kotlin/jvm/internal/Ref\$DoubleRef", "D", false)
    else -> Cell("kotlin/jvm/internal/Ref\$ObjectRef", "Ljava/lang/Object;", true)
}

/** The cell descriptor for a captured item — the shared-cell type is always what a lambda carries. */
private fun ScriptModel.cellDescriptorFor(item: String): String = cellFor(fieldDesc(item)).descriptor

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
