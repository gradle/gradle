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

import org.gradle.model.internal.asm.AsmConstants
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.Interpreter
import org.objectweb.asm.tree.analysis.Value

/**
 * Sound dataflow deciding how a lambda uses its captured script reference.
 *
 * A "script value" originates from `getfield this$0` and is tracked (tainted) through
 * loads/stores/dups/checkcasts and control-flow merges. At each consuming instruction the interpreter
 * inspects its operands: a tainted receiver of a top-level val/var getter/setter is a liftable
 * read/write; a tainted last argument into a rewritable inner lambda's `<init>` is a thread; ANY other
 * consumption of a tainted value (argument to another call, `putfield`, `areturn`, array store, an
 * inherited or other method on the script, reading a non-liftable field) [bails][Result.bail].
 */
internal fun analyzeScriptReferenceFlow(
    lambda: Lambda,
    method: MethodNode,
    scriptModel: ScriptModel,
    lambdaClasses: Set<String>,
): Result {
    val result = Result()
    Analyzer(ScriptReferenceInterpreter(lambda, scriptModel, lambdaClasses, result)).analyze(lambda.name, method)
    return result
}

/** A classified use of the script reference: the producing `getfield this$0` instruction(s) and the call. */
internal class Access(
    val kind: Kind,
    val field: String,
    val call: AbstractInsnNode,
    val receiverSources: Set<AbstractInsnNode>,
) {
    enum class Kind { VAL_READ, VAR_READ, VAR_WRITE }
}

/** The accumulated result of analyzing one method body. */
internal class Result {

    val valReads = linkedSetOf<String>()
    val varReads = linkedSetOf<String>()
    val varWrites = linkedSetOf<String>()

    /** Inner lambda classes the script is threaded into. */
    val threaded = linkedSetOf<String>()

    /** Classified script-reference uses, in encounter order, for precise body rewriting. */
    val accesses = mutableListOf<Access>()

    var bailed = false
        private set

    var bailReason: String? = null
        private set

    /** Record that this body cannot be rewritten; keeps only the first reason. */
    fun bail(reason: String) {
        if (!bailed) {
            bailed = true
            bailReason = reason
        }
    }

    fun recordAccess(kind: Access.Kind, field: String, call: AbstractInsnNode, receiverSources: Set<AbstractInsnNode>) {
        when (kind) {
            Access.Kind.VAL_READ -> valReads
            Access.Kind.VAR_READ -> varReads
            Access.Kind.VAR_WRITE -> varWrites
        }.add(field)
        accesses.add(Access(kind, field, call, receiverSources))
    }
}

/**
 * Abstract value carrying the JVM slot [size], whether it may be the script reference ([script]), and
 * the set of `getfield this$0` instructions that produced it ([src]), which lets the rewriter replace
 * exactly the right instructions. Equality (used by the analyzer's fixpoint) covers all three.
 */
private data class V(
    val slotSize: Int,
    val script: Boolean,
    val src: Set<AbstractInsnNode> = emptySet(),
) : Value {
    override fun getSize(): Int = slotSize
}

private class ScriptReferenceInterpreter(
    private val lambda: Lambda,
    private val scriptModel: ScriptModel,
    private val lambdaClasses: Set<String>,
    private val result: Result,
) : Interpreter<V>(AsmConstants.ASM_LEVEL) {

    override fun newValue(type: Type?): V? = when {
        type == null -> V(1, false) // uninitialized local
        type.sort == Type.VOID -> null
        else -> V(type.size, false)
    }

    override fun newOperation(insn: AbstractInsnNode): V = when (insn.opcode) {
        Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> V(2, false)
        Opcodes.LDC -> {
            val constant = (insn as LdcInsnNode).cst
            V(if (constant is Long || constant is Double) 2 else 1, false)
        }
        Opcodes.GETSTATIC -> V(Type.getType((insn as FieldInsnNode).desc).size, false)
        else -> V(1, false)
    }

    override fun copyOperation(insn: AbstractInsnNode, value: V): V {
        // Storing the script into a local would let that slot's type change (script -> cell) at a
        // stack-map frame, which preserving the compiler's frames cannot express. Kotlin never caches
        // this$0 like this for a property read, so bailing here costs nothing in practice and keeps
        // every rewrite frame-neutral.
        if (value.script && insn.opcode == Opcodes.ASTORE) {
            result.bail("script stored to a local")
        }
        return value // loads/stores/dup otherwise preserve taint
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: V): V? = when (insn.opcode) {
        Opcodes.GETFIELD -> {
            val field = insn as FieldInsnNode
            if (field.owner == lambda.name && field.name == THIS0) {
                V(1, true, setOf(insn)) // the source: reading the captured script
            } else {
                if (value.script) classifyFieldRead(field, value) // reading a field of the script directly
                V(Type.getType(field.desc).size, false)
            }
        }
        Opcodes.CHECKCAST -> {
            // A checkcast between the script load and its use would survive the rewrite and end up
            // casting the substituted Ref cell to the script type (a ClassCastException). Bail rather
            // than emit that, but keep the taint so any further use of the script is still detected.
            if (value.script) result.bail("script -> checkcast")
            value
        }
        Opcodes.PUTSTATIC -> {
            if (value.script) result.bail("script -> putstatic")
            null
        }
        Opcodes.INSTANCEOF -> {
            if (value.script) result.bail("script -> instanceof")
            V(1, false)
        }
        Opcodes.INEG, Opcodes.L2I, Opcodes.F2I, Opcodes.D2I, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S,
        Opcodes.FNEG, Opcodes.ARRAYLENGTH -> V(1, false)
        Opcodes.LNEG, Opcodes.DNEG, Opcodes.I2L, Opcodes.F2L, Opcodes.D2L, Opcodes.I2D, Opcodes.L2D,
        Opcodes.F2D -> V(2, false)
        else -> {
            if (value.script) result.bail("script -> unary op ${insn.opcode}")
            V(1, false)
        }
    }

    override fun binaryOperation(insn: AbstractInsnNode, value1: V, value2: V): V? {
        if (insn.opcode == Opcodes.PUTFIELD) {
            val field = insn as FieldInsnNode
            if (value2.script) result.bail("script stored into field ${field.name}")
            if (value1.script) classifyFieldWrite(field, value1) // this$0.<field> = ...
            return null
        }
        if (value1.script || value2.script) {
            // array loads / arithmetic on a script value are never legitimate lifts
            result.bail(if (insn.opcode == Opcodes.AALOAD) "script indexed" else "script -> binary op ${insn.opcode}")
        }
        return when (insn.opcode) {
            Opcodes.LALOAD, Opcodes.DALOAD, Opcodes.LADD, Opcodes.DADD, Opcodes.LSUB, Opcodes.DSUB,
            Opcodes.LMUL, Opcodes.DMUL, Opcodes.LDIV, Opcodes.DDIV, Opcodes.LREM, Opcodes.DREM,
            Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR -> V(2, false)
            else -> V(1, false)
        }
    }

    override fun ternaryOperation(insn: AbstractInsnNode, value1: V, value2: V, value3: V): V? {
        if (value1.script || value2.script || value3.script) result.bail("script -> array store")
        return null
    }

    override fun naryOperation(insn: AbstractInsnNode, values: List<V>): V? = when (insn) {
        is MethodInsnNode -> {
            val hasReceiver = insn.opcode != Opcodes.INVOKESTATIC
            if (hasReceiver && values[0].script) {
                classifyReceiverCall(insn, values[0])
            }
            val firstArg = if (hasReceiver) 1 else 0
            for (i in firstArg until values.size) {
                if (values[i].script) checkScriptArgument(insn, i, values.size)
            }
            returnValueOf(insn.desc)
        }
        is InvokeDynamicInsnNode -> {
            if (values.any { it.script }) result.bail("script captured by invokedynamic")
            returnValueOf(insn.desc)
        }
        else -> { // MULTIANEWARRAY
            if (values.any { it.script }) result.bail("script -> multianewarray")
            V(1, false)
        }
    }

    override fun returnOperation(insn: AbstractInsnNode, value: V, expected: V) {
        if (value.script) result.bail("script returned")
    }

    override fun merge(value1: V, value2: V): V {
        if (value1 == value2) return value1
        val mergedSources = when {
            value1.src.isEmpty() -> value2.src
            value2.src.isEmpty() -> value1.src
            else -> value1.src + value2.src
        }
        // taint is conservative (OR); size is the safe minimum
        return V(minOf(value1.slotSize, value2.slotSize), value1.script || value2.script, mergedSources)
    }

    private fun returnValueOf(methodDescriptor: String): V? {
        val returnType = Type.getReturnType(methodDescriptor)
        return if (returnType.sort == Type.VOID) null else V(returnType.size, false)
    }

    /** A tainted method argument is only allowed as the trailing script argument of a rewritable inner lambda's `<init>`. */
    private fun checkScriptArgument(call: MethodInsnNode, argIndex: Int, argCount: Int) {
        val threadsIntoInnerLambda = call.opcode == Opcodes.INVOKESPECIAL &&
            call.name == "<init>" &&
            call.owner in lambdaClasses &&
            argIndex == argCount - 1 &&
            scriptIsLastAndUniqueArg(call.desc, lambda.scriptType)
        if (threadsIntoInnerLambda) {
            result.threaded.add(call.owner)
        } else {
            result.bail("script passed as arg to ${call.owner}.${call.name}")
        }
    }

    private fun classifyReceiverCall(call: MethodInsnNode, receiver: V) {
        if (call.owner != lambda.scriptType) {
            result.bail("script call on non-script owner ${call.owner}")
            return
        }
        val valField = scriptModel.valGetterToField[call.name]
        if (valField != null && call.desc == "()" + scriptModel.valFieldDesc[valField]) {
            result.recordAccess(Access.Kind.VAL_READ, valField, call, receiver.src)
            return
        }
        val varField = scriptModel.varField(call.name)
        if (varField != null) {
            val kind = if (call.name.startsWith("set")) Access.Kind.VAR_WRITE else Access.Kind.VAR_READ
            result.recordAccess(kind, varField, call, receiver.src)
            return
        }
        result.bail("non-liftable script call ${call.name}${call.desc}")
    }

    private fun classifyFieldRead(field: FieldInsnNode, receiver: V) {
        when {
            field.owner != lambda.scriptType -> result.bail("field read on non-script owner")
            field.name in scriptModel.valFieldDesc -> result.recordAccess(Access.Kind.VAL_READ, field.name, field, receiver.src)
            field.name in scriptModel.varFields -> result.recordAccess(Access.Kind.VAR_READ, field.name, field, receiver.src)
            else -> result.bail("direct read of non-liftable script field ${field.name}")
        }
    }

    private fun classifyFieldWrite(field: FieldInsnNode, receiver: V) {
        if (field.owner == lambda.scriptType && field.name in scriptModel.varFields) {
            result.recordAccess(Access.Kind.VAR_WRITE, field.name, field, receiver.src)
        } else {
            result.bail("direct write of non-var script field ${field.name}")
        }
    }
}
