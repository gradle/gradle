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

import org.gradle.api.logging.Logging
import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.VarInsnNode
import java.nio.file.Files
import java.nio.file.Path

private val logger = Logging.getLogger(CaptureMinimizer::class.java)

/**
 * Post-compile capture-minimization transform for Kotlin-DSL compiled scripts.
 *
 * Rewrites nested SAM lambda classes (Action/Spec/Runnable) so that a lambda capturing the compiled
 * script only to read/write top-level `val`/`var`s carries those (a `val` by value, a `var` via a
 * shared `kotlin.jvm.internal.Ref` cell) instead of the whole script (its `this$0` field). Nested
 * lambda chains are threaded inside-out. Everything not provably safe is left byte-identical.
 *
 * Conservative rule: a lambda is rewritten only if a sound dataflow ([analyzeScriptReferenceFlow])
 * proves every use of its script reference is a top-level val/var getter/setter or a thread into a
 * rewritable inner lambda's constructor. Any escape, inherited/other call, non-convertible var,
 * non-trivial constructor, or malformed creation site makes the whole lambda bail. Worst case is
 * today's behavior.
 */
object CaptureMinimizer {

    /** Bumped whenever the transform's behavior changes, so cached script workspaces are invalidated. */
    const val VERSION = 1

    /**
     * Minimizes captures in the compiled classes under [classesDir], in place.
     *
     * @param classesDir directory of a single script compilation unit's `.class` files
     * @return the number of lambdas de-captured
     */
    fun minimize(classesDir: Path): Int {
        val world = World.load(classesDir)
        world.identify()
        world.computeConvertibility()
        world.analyzeBodies()
        world.scanCreationSites()
        world.decide()
        world.foldCapturedVals()
        return CaptureRewriter(world).rewriteAndWrite()
    }
}

/**
 * The compilation unit under transformation: every class loaded from the workspace, plus the derived
 * scripts and script-capturing lambdas. Holds the mutable analysis state that the phases fill in and
 * that [CaptureRewriter] consumes.
 */
internal class World private constructor(
    val dir: Path,
    val classes: Map<String, ClassNode>,
) {
    val lambdaClasses = mutableSetOf<String>()
    val scripts = mutableMapOf<String, ScriptModel>()
    val lambdas = linkedMapOf<String, Lambda>()
    val sitesByLambda = mutableMapOf<String, MutableList<Site>>()

    /** Names of classes mutated by the rewrite, to be written back. */
    val changed = mutableSetOf<String>()

    companion object {
        fun load(dir: Path): World {
            val classes = linkedMapOf<String, ClassNode>()
            Files.walk(dir).use { paths ->
                paths.filter { it.toString().endsWith(".class") }.forEach { file ->
                    val classNode = ClassNode(AsmConstants.ASM_LEVEL)
                    // Keep the compiler's stack-map frames (EXPAND_FRAMES) so the rewrite can preserve
                    // them on write, avoiding frame recomputation (and any class loading it would need).
                    ClassReader(Files.readAllBytes(file)).accept(classNode, ClassReader.EXPAND_FRAMES)
                    classes[classNode.name] = classNode
                }
            }
            return World(dir, classes)
        }
    }

    fun sitesFor(lambda: Lambda): List<Site> = sitesByLambda[lambda.name].orEmpty()

    // ---- identify scripts and the lambdas that capture them ----

    fun identify() {
        findLambdaClasses()
        identifyScripts()
        identifyCapturingLambdas()
    }

    private fun findLambdaClasses() {
        // A lambda class is one with a field literally named `this$0`.
        classes.values
            .filter { classNode -> classNode.fields.any { it.name == THIS0 } }
            .forEach { lambdaClasses.add(it.name) }
    }

    private fun identifyScripts() {
        // Script classes are the (in-world, non-lambda) targets of some `this$0` field.
        val candidates = mutableSetOf<String>()
        for (classNode in classes.values) {
            for (field in classNode.fields) {
                if (field.name == THIS0) {
                    val target = Type.getType(field.desc).internalName
                    if (target in classes && target !in lambdaClasses) {
                        candidates.add(target)
                    }
                }
            }
        }
        candidates.forEach { scripts[it] = buildScriptModel(classes.getValue(it)) }
    }

    private fun buildScriptModel(scriptClass: ClassNode): ScriptModel {
        val model = ScriptModel(scriptClass.name)
        for (field in scriptClass.fields) {
            if (field.access.isStatic || field.name.startsWith("\$\$") || field.name == THIS0 || field.name == "host") {
                continue
            }
            if (field.access.isFinal) {
                model.valGetterToField[field.name.getterName()] = field.name
                model.valFieldDesc[field.name] = field.desc
            } else {
                model.varFields.add(field.name)
                model.varFieldDesc[field.name] = field.desc
                model.varMethodToField[field.name.getterName()] = field.name
                model.varMethodToField[field.name.setterName()] = field.name
            }
        }
        return model
    }

    private fun identifyCapturingLambdas() {
        for (name in lambdaClasses) {
            val classNode = classes.getValue(name)
            val this0 = classNode.fields.firstOrNull { it.name == THIS0 }
            val scriptType = this0?.let { Type.getType(it.desc).internalName }
            // Only lambdas whose this$0 is a compiled script are our target — not those capturing an
            // enclosing lambda.
            if (scriptType != null && scriptType in scripts) {
                val otherFields = classNode.fields.filter { it.name != THIS0 && !it.access.isStatic }
                lambdas[name] = Lambda(classNode, scriptType, otherFields)
            }
        }
    }

    // ---- prove which vars can become shared cells (script-side scan; positive proof only) ----

    fun computeConvertibility() {
        for (model in scripts.values) {
            val scriptClass = classes.getValue(model.internalName)
            model.varFields.filterTo(model.convertibleVars) { isVarConvertible(scriptClass, it) }
        }
    }

    private fun isVarConvertible(scriptClass: ClassNode, varField: String): Boolean {
        val getter = scriptClass.findMethod(varField.getterName()) ?: return false
        val setter = scriptClass.findMethod(varField.setterName()) ?: return false
        var initPuts = 0
        var getterGets = 0
        var setterPuts = 0
        for (method in scriptClass.methods) {
            for (insn in method.instructions) {
                if (insn !is FieldInsnNode || insn.owner != scriptClass.name || insn.name != varField) {
                    continue
                }
                when {
                    method === getter && insn.opcode == Opcodes.GETFIELD -> getterGets++
                    insn.opcode == Opcodes.PUTFIELD && (method === setter || method.name == "<init>") -> {
                        if (!insn.writesToThis()) return false
                        if (method === setter) setterPuts++ else initPuts++
                    }
                    else -> return false // any other access to the var -> not convertible
                }
            }
        }
        // initPuts may be 0 when the initializer is the JVM default (Kotlin elides `= 0` / `= null`);
        // the synthesized empty cell then supplies that default.
        return initPuts <= 1 && getterGets == 1 && setterPuts == 1
    }

    // ---- per-lambda body analysis (sound dataflow) ----

    fun analyzeBodies() {
        for (lambda in lambdas.values) {
            val model = scripts.getValue(lambda.scriptType)
            for (method in lambda.classNode.methods) {
                if (method.name == "<init>") {
                    if (!ctorIsTrivial(lambda, method)) failLambda(lambda, "non-trivial ctor")
                } else {
                    analyzeMethodBody(lambda, method, model)
                }
            }
            rejectUnliftableCaptures(lambda, model)
        }
    }

    private fun analyzeMethodBody(lambda: Lambda, method: MethodNode, model: ScriptModel) {
        val result = try {
            analyzeScriptReferenceFlow(lambda, method, model, lambdaClasses)
        } catch (e: Throwable) {
            failLambda(lambda, "analysis failed: $e")
            return
        }
        if (result.bailed) failLambda(lambda, result.bailReason)
        // The rewriter replaces each producing `getfield this$0` in place, so a single source feeding
        // more than one access (the script aliased and read repeatedly) cannot be rewritten soundly —
        // bail and leave the class byte-identical.
        if (sharesAReceiverSource(result.accesses)) {
            failLambda(lambda, "script reference aliased across multiple accesses")
        }
        lambda.valReads += result.valReads
        lambda.varReads += result.varReads
        lambda.varWrites += result.varWrites
        lambda.threaded += result.threaded
        if (result.accesses.isNotEmpty()) {
            lambda.accessesByMethod[method] = result.accesses
        }
    }

    private fun rejectUnliftableCaptures(lambda: Lambda, model: ScriptModel) {
        // A function-typed val/var holds a lambda that may itself capture the (build-model) script;
        // leaving it behind the script gives the intended graceful execution-time failure (see
        // isKotlinFunctionType). The value type is what matters, so this applies to vals and vars alike.
        for (field in lambda.valReads + lambda.varReads + lambda.varWrites) {
            if (isKotlinFunctionType(model.fieldDesc(field))) {
                failLambda(lambda, "captures a function-typed field (deferred model access): $field")
            }
        }
        // Only vars need the script-side getter/setter rerouting, which needs the trivial-accessor shape.
        for (varField in lambda.varReads + lambda.varWrites) {
            if (varField !in model.convertibleVars) {
                failLambda(lambda, "uses non-convertible var $varField")
            }
        }
    }

    /** True if a single `getfield this$0` instruction is the receiver source of more than one access. */
    private fun sharesAReceiverSource(accesses: List<Access>): Boolean {
        val seen = mutableSetOf<AbstractInsnNode>()
        return accesses.any { access -> access.receiverSources.any { !seen.add(it) } }
    }

    /** A trivial constructor only stores its parameters into its own fields and calls the super constructor. */
    private fun ctorIsTrivial(lambda: Lambda, ctor: MethodNode): Boolean {
        for (insn in ctor.instructions) {
            when (insn.opcode) {
                -1 -> {} // pseudo-instruction (label / line number / frame)
                Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD, Opcodes.RETURN -> {}
                Opcodes.PUTFIELD -> if ((insn as FieldInsnNode).owner != lambda.name) return false
                Opcodes.INVOKESPECIAL -> if ((insn as MethodInsnNode).name != "<init>") return false
                else -> return false
            }
        }
        return true
    }

    private fun failLambda(lambda: Lambda, reason: String?) {
        lambda.localOk = false
        logBail(lambda, reason)
    }

    // ---- creation sites ----

    fun scanCreationSites() {
        for (classNode in classes.values) {
            val scriptContext = classNode.name in scripts
            for (method in classNode.methods) {
                for (init in method.instructions.constructorCalls()) {
                    val lambda = lambdas[init.owner] ?: continue
                    sitesByLambda.getOrPut(lambda.name) { mutableListOf() }.add(Site(classNode, method, init, scriptContext))
                    if (!isCleanScriptPushSuffix(init, scriptContext, classNode)) {
                        lambda.creationSitesOk = false
                        logBail(lambda, "malformed creation site in ${classNode.name}.${method.name}")
                    }
                }
            }
        }
    }

    /** The script push must be exactly `aload_0` (script context) or `aload_0; getfield this$0` (lambda context). */
    private fun isCleanScriptPushSuffix(init: MethodInsnNode, scriptContext: Boolean, enclosing: ClassNode): Boolean {
        val args = Type.getArgumentTypes(init.desc)
        if (args.isEmpty() || args.last().internalName !in scripts) {
            return false
        }
        val scriptPush = init.previousReal()
        if (scriptContext) {
            return scriptPush.isAload0()
        }
        if (scriptPush !is FieldInsnNode ||
            scriptPush.opcode != Opcodes.GETFIELD ||
            scriptPush.name != THIS0 ||
            scriptPush.owner != enclosing.name
        ) {
            return false
        }
        return scriptPush.previousReal().isAload0()
    }

    // ---- decide rewritability (fixpoint) ----

    fun decide() {
        for (lambda in lambdas.values) {
            lambda.rewritable = lambda.localOk && lambda.creationSitesOk
        }
        var changed = true
        while (changed) {
            changed = false
            for (lambda in lambdas.values) {
                if (lambda.rewritable && revokeIfDependencyNotRewritable(lambda)) {
                    changed = true
                }
            }
        }
    }

    /** Revokes a lambda's rewritability if a lambda it depends on is not rewritable; returns whether it did. */
    private fun revokeIfDependencyNotRewritable(lambda: Lambda): Boolean {
        // Every inner lambda it threads the script into must itself be rewritable.
        for (inner in lambda.threaded) {
            if (!isRewritable(inner)) {
                lambda.rewritable = false
                logBail(lambda, "inner $inner not rewritable")
                return true
            }
        }
        // Every lambda-context creator must be rewritable, so its creation site is fixed consistently.
        for (site in sitesFor(lambda)) {
            if (!site.scriptContext && !isRewritable(site.ownerClass.name)) {
                lambda.rewritable = false
                logBail(lambda, "creator ${site.ownerClass.name} not rewritable")
                return true
            }
        }
        return false
    }

    private fun isRewritable(lambdaName: String): Boolean = lambdas[lambdaName]?.rewritable == true

    // ---- fold captured vals/vars (a lambda carries its own plus its threaded inners') ----

    fun foldCapturedVals() {
        for (lambda in lambdas.values) {
            if (!lambda.rewritable) continue
            // sorted set -> canonical order used at the constructor and at every push site
            val captured = sortedSetOf<String>()
            collectCapturedFields(lambda, captured, mutableSetOf())
            lambda.order = captured.toList()
        }
    }

    private fun collectCapturedFields(lambda: Lambda, accumulator: MutableSet<String>, visited: MutableSet<String>) {
        if (!visited.add(lambda.name)) return
        accumulator += lambda.valReads
        accumulator += lambda.varReads
        accumulator += lambda.varWrites
        for (inner in lambda.threaded) {
            lambdas[inner]?.let { collectCapturedFields(it, accumulator, visited) }
        }
    }

    private fun logBail(lambda: Lambda, reason: String?) {
        logger.debug("Capture minimization skips {}: {}", lambda.name, reason)
    }
}

/** True if the instruction writes to `this` (i.e. it is preceded by `aload_0; <single value push>`). */
private fun FieldInsnNode.writesToThis(): Boolean {
    val value = previousReal() ?: return false
    return value.previousReal().isAload0()
}

private fun AbstractInsnNode?.isAload0(): Boolean =
    this != null && opcode == Opcodes.ALOAD && (this as VarInsnNode).`var` == 0
