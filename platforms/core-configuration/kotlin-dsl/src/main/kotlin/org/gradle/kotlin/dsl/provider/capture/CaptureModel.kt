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

import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/** The synthetic field, present on every SAM lambda class, that holds the captured enclosing instance. */
internal const val THIS0 = "this\$0"

/** Prefix for the synthesized captured-value fields (`cm$<field>`) on lambdas and on the script. */
internal const val CM_PREFIX = "cm\$"

/** A JVM internal class name (`a/b/C`), wrapped for type safety so it is never confused with a raw string. */
data class InternalClassName(val className: String)

val FieldInsnNode.ownerInternalClassName: InternalClassName get() = InternalClassName(owner)
val MethodInsnNode.ownerInternalClassName: InternalClassName get() = InternalClassName(owner)
val Type.internalClassName: InternalClassName get() = InternalClassName(internalName)
val ClassNode.internalName: InternalClassName get() = InternalClassName(name)
val FieldNode.typeInternalName: InternalClassName get() = InternalClassName(Type.getType(this.desc).internalName)

/**
 * What a compiled script class exposes to its lambdas: its top-level `val`s (final fields, reached
 * through a `getX()` getter) and `var`s (non-final fields, reached through `getX()`/`setX()`).
 *
 * Immutable: everything is derived once from the script's fields (see `scriptClassModelOf`).
 * [convertibleVars] are the vars proven safe to reroute through a shared cell.
 */
internal class ScriptModel(
    val internalName: InternalClassName,
    /** getter method name -> val field it reads. */
    val valGetterToField: Map<String, String>,
    /** val field -> its descriptor. */
    val valFieldDesc: Map<String, String>,
    /** var field names. */
    val varFields: Set<String>,
    /** var field -> its descriptor. */
    val varFieldDesc: Map<String, String>,
    /** getter/setter method name -> var field it accesses. */
    val varMethodToField: Map<String, String>,
    /** vars proven safe to turn into shared cells (trivial accessor + `<init>` shape). */
    val convertibleVars: Set<String>,
) {
    /** The var field a getter/setter method name accesses, or `null` if the name is not a var accessor. */
    fun varField(methodName: String): String? = varMethodToField[methodName]

    fun isVar(field: String): Boolean = field in varFields

    /** Descriptor of a captured top-level field (val or var). */
    fun fieldDesc(field: String): String = valFieldDesc[field] ?: varFieldDesc.getValue(field)
}

/**
 * A nested SAM lambda class that directly captures a compiled script as its [THIS0] field.
 *
 * Immutable structural facts only — everything the analysis derives about it lives in
 * [LambdaBodyAnalysis], [LambdaSites], and the [RewritePlan], not here.
 */
internal class Lambda(
    val classNode: ClassNode,
    /** The type of the captured `this$0` — the script this lambda reads through. */
    val receiverType: InternalClassName,
    /** The lambda's other (non-`this$0`, non-static) captured fields, in declaration order. */
    val otherFields: List<FieldNode>,
) {
    val internalClassName: InternalClassName get() = classNode.internalName
}

/** A place where `new X; ...; invokespecial X.<init>(..., LScript;)` constructs a lambda [X]. */
internal class LambdaInstantiationSite(
    val inOwnerClass: ClassNode,
    val inMethod: MethodNode,
    val initInsn: MethodInsnNode,
    /** True when the enclosing owner is the script itself (so `this` is the script). */
    val isInImmediateScriptClass: Boolean,
)

// ---- analysis results (each phase produces one of these instead of mutating shared state) ----

/**
 * What the sound dataflow proved about one capturing lambda's body and constructor, in isolation.
 * [usesScriptAsDataOnly] is the local verdict; whether the lambda is ultimately rewritten also depends
 * on its creation sites and on the lambdas it threads the script into (see [decideRewritableLambdas]).
 */
internal class LambdaBodyAnalysis(
    /** The body and constructor use the script only in liftable ways (getter/setter reads, threads). */
    val usesScriptAsDataOnly: Boolean,
    val valReads: Set<String>,
    val varReads: Set<String>,
    val varWrites: Set<String>,
    /** Inner lambda classes this one threads the script into. */
    val threaded: Set<InternalClassName>,
    /** Per-method classified script uses, used to rewrite the body precisely. */
    val accessesByMethod: Map<MethodNode, List<Access>>,
) {
    /** All top-level fields this lambda's own body reads or writes (its threaded inners' are folded in later). */
    val capturedFields: Set<String> get() = valReads + varReads + varWrites
}

/** Where a lambda is instantiated, and whether every such site pushes the script as a clean, removable suffix. */
internal class LambdaSites(
    val sites: List<LambdaInstantiationSite>,
    val allSitesClean: Boolean,
)

/** One lambda that will be rewritten, bundled with everything the rewriter needs for it. */
internal class RewritableLambda(
    val lambda: Lambda,
    /** Captured items (own + threaded inners'), in canonical sorted order — used at the ctor and every push site. */
    val capturedOrder: List<String>,
    val accessesByMethod: Map<MethodNode, List<Access>>,
    val sites: List<LambdaInstantiationSite>,
)

/** The full set of decisions the rewriter applies. */
internal class RewritePlan(val rewritableLambdas: List<RewritableLambda>)

// ---- Kotlin accessor naming + type predicates ----

/**
 * Kotlin's `is`-prefix accessor rule (JvmAbi): a property named `isX`, where the character after `is`
 * is not a lowercase letter, keeps its own name for the getter and uses `setX` for the setter,
 * regardless of type. Every other property uses the standard `getX`/`setX`.
 */
private fun String.hasIsPrefix(): Boolean = length > 2 && startsWith("is") && !this[2].isLowerCase()

private fun String.capitalized(): String = replaceFirstChar { it.uppercaseChar() }

internal fun String.getterName(): String = if (hasIsPrefix()) this else "get" + capitalized()

internal fun String.setterName(): String = if (hasIsPrefix()) "set" + substring(2) else "set" + capitalized()

/**
 * A Kotlin function type (`kotlin.jvm.functions.Function0`, `Function1`, …). A `val`/`var` of this type
 * holds a lambda that itself captured the enclosing script, so lifting it is pointless and unsound:
 * pointless because the script still rides inside the captured function (no over-capture is removed),
 * and unsound because that function is itself serialized by the configuration cache's lambda codec —
 * relocating it into a synthesized cell captured by another lambda is a shape that codec does not
 * support. Left behind the script, it takes the ordinary scrubbing path and fails gracefully at
 * execution instead.
 */
internal fun isKotlinFunctionType(descriptor: String): Boolean =
    descriptor.startsWith("Lkotlin/jvm/functions/Function")

/** True if [scriptType] is the last argument of [descriptor] and appears nowhere else in it. */
internal fun scriptIsLastAndUniqueArg(descriptor: String, scriptType: InternalClassName): Boolean {
    val args = Type.getArgumentTypes(descriptor)
    if (args.isEmpty() || args.last().internalClassName != scriptType) {
        return false
    }
    return args.dropLast(1).none { it.internalClassName == scriptType }
}
