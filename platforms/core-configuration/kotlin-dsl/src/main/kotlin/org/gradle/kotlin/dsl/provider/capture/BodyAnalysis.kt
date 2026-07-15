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
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

/**
 * Analyzes every capturing lambda's body with the sound dataflow ([analyzeScriptReferenceFlowInLambdaMethod]),
 * producing one [LambdaBodyAnalysis] per lambda. Nothing here mutates shared state.
 */
internal fun analyzeBodies(
    capturingLambdas: Map<InternalClassName, Lambda>,
    scripts: Map<InternalClassName, ScriptModel>,
    lambdaClassNames: Set<InternalClassName>,
): Map<InternalClassName, LambdaBodyAnalysis> =
    capturingLambdas.mapValues { (_, lambda) -> analyzeLambda(lambda, scripts.getValue(lambda.receiverType), lambdaClassNames) }

private fun analyzeLambda(lambda: Lambda, model: ScriptModel, lambdaClassNames: Set<InternalClassName>): LambdaBodyAnalysis {
    val valReads = linkedSetOf<String>()
    val varReads = linkedSetOf<String>()
    val varWrites = linkedSetOf<String>()
    val threaded = linkedSetOf<InternalClassName>()
    val accessesByMethod = linkedMapOf<MethodNode, List<Access>>()
    var usesScriptAsDataOnly = true
    fun skip(reason: String?) {
        usesScriptAsDataOnly = false
        logSkip(lambda.internalClassName, reason)
    }

    for (method in lambda.classNode.methods) {
        if (method.name == "<init>") {
            if (!ctorIsTrivial(lambda, method)) skip("non-trivial ctor")
        } else {
            val result = runCatching { analyzeScriptReferenceFlowInLambdaMethod(lambda, method, model, lambdaClassNames) }
                .getOrElse { skip("analysis failed: $it"); null }
            if (result != null) {
                if (result.bailed) skip(result.bailReason)
                // A single `getfield this$0` feeding more than one access (script aliased and read
                // repeatedly) cannot be rewritten in place, so bail and leave the class byte-identical.
                if (sharesAReceiverSource(result.accesses)) skip("script reference aliased across multiple accesses")
                valReads += result.valReads
                varReads += result.varReads
                varWrites += result.varWrites
                threaded += result.threaded
                if (result.accesses.isNotEmpty()) accessesByMethod[method] = result.accesses
            }
        }
    }
    // Function-typed captures and non-convertible vars can't be lifted (see isKotlinFunctionType / isVarConvertible).
    for (field in valReads + varReads + varWrites) {
        if (isKotlinFunctionType(model.fieldDesc(field))) skip("captures a function-typed field (deferred model access): $field")
    }
    for (varField in varReads + varWrites) {
        if (varField !in model.convertibleVars) skip("uses non-convertible var $varField")
    }
    return LambdaBodyAnalysis(usesScriptAsDataOnly, valReads, varReads, varWrites, threaded, accessesByMethod)
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
            Opcodes.PUTFIELD -> if ((insn as FieldInsnNode).ownerInternalClassName != lambda.internalClassName) return false
            Opcodes.INVOKESPECIAL -> if ((insn as MethodInsnNode).name != "<init>") return false
            else -> return false
        }
    }
    return true
}
