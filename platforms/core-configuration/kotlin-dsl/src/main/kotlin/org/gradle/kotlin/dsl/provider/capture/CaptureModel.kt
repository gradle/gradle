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
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/** The synthetic field, present on every SAM lambda class, that holds the captured enclosing instance. */
internal const val THIS0 = "this\$0"

/** Prefix for the synthesized captured-value fields (`cm$<field>`) on lambdas and on the script. */
internal const val CM_PREFIX = "cm\$"

/**
 * What a compiled script class exposes to its lambdas: its top-level `val`s (final fields, reached
 * through a `getX()` getter) and `var`s (non-final fields, reached through `getX()`/`setX()`).
 *
 * Built by scanning the script's own fields; [convertibleVars] is filled in later, once the script's
 * accessors are proven to have the trivial shape that lets a `var` be rerouted through a shared cell.
 */
internal class ScriptModel(val internalName: String) {

    /** getter method name -> val field it reads. */
    val valGetterToField = mutableMapOf<String, String>()

    /** val field -> its descriptor. */
    val valFieldDesc = mutableMapOf<String, String>()

    /** var field names. */
    val varFields = mutableSetOf<String>()

    /** var field -> its descriptor. */
    val varFieldDesc = mutableMapOf<String, String>()

    /** getter/setter method name -> var field it accesses. */
    val varMethodToField = mutableMapOf<String, String>()

    /** vars proven safe to turn into shared cells (trivial accessor + `<init>` shape). */
    val convertibleVars = mutableSetOf<String>()

    /** The var field a getter/setter method name accesses, or `null` if the name is not a var accessor. */
    fun varField(methodName: String): String? = varMethodToField[methodName]

    fun isVar(field: String): Boolean = field in varFields

    /** Descriptor of a captured top-level field (val or var). */
    fun fieldDesc(field: String): String = valFieldDesc[field] ?: varFieldDesc.getValue(field)
}

/**
 * A nested SAM lambda class that directly captures a compiled script as its [THIS0] field.
 *
 * The analysis fills in what the lambda's body does with that script reference; [order] is the final,
 * canonical list of captured items (vals + vars) it will carry once [rewritable].
 */
internal class Lambda(
    val classNode: ClassNode,
    val scriptType: String,
    /** The lambda's other (non-`this$0`, non-static) captured fields, in declaration order. */
    val otherFields: List<FieldNode>,
) {
    val name: String get() = classNode.name

    /** The body and constructor are sane, independent of inner/creator rewritability. */
    var localOk = true

    /** Every place that constructs this lambda pushes the script as a clean, removable suffix. */
    var creationSitesOk = true

    val valReads = linkedSetOf<String>()
    val varReads = linkedSetOf<String>()
    val varWrites = linkedSetOf<String>()

    /** Names of inner lambda classes this one constructs, threading the script into them. */
    val threaded = linkedSetOf<String>()

    /** Per-method classified uses of the script reference, used to rewrite the body precisely. */
    val accessesByMethod = linkedMapOf<MethodNode, List<Access>>()

    var rewritable = false

    /** Captured items (vals + vars, this lambda's own plus its threaded inners'), in canonical order. */
    var order: List<String> = emptyList()
}

/** A place where `new X; ...; invokespecial X.<init>(..., LScript;)` constructs a lambda [X]. */
internal class Site(
    val ownerClass: ClassNode,
    val method: MethodNode,
    val init: MethodInsnNode,
    /** True when the enclosing owner is the script itself (so `this` is the script). */
    val scriptContext: Boolean,
)

// ---- shared ASM / naming helpers ----

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

internal operator fun Int.contains(flag: Int): Boolean = this and flag == flag
internal val Int.isStatic: Boolean get() = Opcodes.ACC_STATIC in this
internal val Int.isFinal: Boolean get() = Opcodes.ACC_FINAL in this

/** `foo` -> `Foo`, matching Kotlin's accessor naming; empty stays empty. */
internal fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

/**
 * Kotlin's `is`-prefix accessor rule (JvmAbi): a property named `isX`, where the character after `is`
 * is not a lowercase letter, keeps its own name for the getter and uses `setX` for the setter,
 * regardless of type. Every other property uses the standard `getX`/`setX`.
 */
private fun String.hasIsPrefix(): Boolean = length > 2 && startsWith("is") && !this[2].isLowerCase()

internal fun String.getterName(): String = if (hasIsPrefix()) this else "get" + capitalized()

internal fun String.setterName(): String = if (hasIsPrefix()) "set" + substring(2) else "set" + capitalized()

/**
 * A Kotlin function type (`kotlin.jvm.functions.Function0`, `Function1`, …). A `val` of this type holds
 * a lambda that may itself capture the script, so lifting it neither reduces over-capture (the script
 * rides inside the function) nor serializes cleanly (it breaks the configuration-cache lambda codec).
 * Such vals are left behind the script for a graceful execution-time failure.
 */
internal fun isKotlinFunctionType(descriptor: String): Boolean =
    descriptor.startsWith("Lkotlin/jvm/functions/Function")

/** True if [scriptType] is the last argument of [descriptor] and appears nowhere else in it. */
internal fun scriptIsLastAndUniqueArg(descriptor: String, scriptType: String): Boolean {
    val args = Type.getArgumentTypes(descriptor)
    if (args.isEmpty() || args.last().internalName != scriptType) {
        return false
    }
    return args.dropLast(1).none { it.internalName == scriptType }
}
