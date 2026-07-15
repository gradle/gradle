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
 * inherited or other method on the script, reading a non-liftable field) [bails][MutableResult.bail].
 */
internal fun analyzeScriptReferenceFlowInLambdaMethod(
    lambda: Lambda,
    method: MethodNode,
    scriptModel: ScriptModel,
    lambdaClasses: Set<InternalClassName>,
): MutableResult {
    val result = MutableResult()
    Analyzer(ScriptReferenceInterpreter(lambda, scriptModel, lambdaClasses, result)).analyze(lambda.internalClassName.className, method)
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
internal class MutableResult {

    val valReads = linkedSetOf<String>()
    val varReads = linkedSetOf<String>()
    val varWrites = linkedSetOf<String>()

    /** Inner lambda classes the script is threaded into. */
    val threaded = linkedSetOf<InternalClassName>()

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
 * Abstract value carrying the JVM slot [size], whether it may be the script reference ([isThisScript]), and
 * the set of `getfield this$0` instructions that produced it ([producedBy]), which lets the rewriter replace
 * exactly the right instructions. Equality (used by the analyzer's fixpoint) covers all three.
 */
private data class TrackedValue(
    val slotSize: Int,
    val isThisScript: Boolean,
    val producedBy: Set<AbstractInsnNode> = emptySet(),
) : Value {
    override fun getSize(): Int = slotSize
}

private class ScriptReferenceInterpreter(
    private val lambda: Lambda,
    private val scriptModel: ScriptModel,
    private val lambdaClasses: Set<InternalClassName>,
    private val mutableResult: MutableResult,
) : Interpreter<TrackedValue>(AsmConstants.ASM_LEVEL) {

    override fun newValue(type: Type?): TrackedValue? = when {
        type == null -> TrackedValue(1, false) // uninitialized local
        type.sort == Type.VOID -> null
        else -> TrackedValue(type.size, false)
    }

    override fun newOperation(insn: AbstractInsnNode): TrackedValue = when (insn.opcode) {
        Opcodes.LCONST_0, Opcodes.LCONST_1, Opcodes.DCONST_0, Opcodes.DCONST_1 -> TrackedValue(2, false)
        Opcodes.LDC -> {
            val constant = (insn as LdcInsnNode).cst
            TrackedValue(if (constant is Long || constant is Double) 2 else 1, false)
        }
        Opcodes.GETSTATIC -> TrackedValue(Type.getType((insn as FieldInsnNode).desc).size, false)
        else -> TrackedValue(1, false)
    }

    override fun copyOperation(insn: AbstractInsnNode, value: TrackedValue): TrackedValue {
        // Storing the script into a local would let that slot's type change (script -> cell) at a
        // stack-map frame, which preserving the compiler's frames cannot express. Kotlin never caches
        // this$0 like this for a property read, so bailing here costs nothing in practice and keeps
        // every rewrite frame-neutral.
        if (value.isThisScript && insn.opcode == Opcodes.ASTORE) {
            mutableResult.bail("script stored to a local")
        }
        return value // loads/stores/dup otherwise preserve taint
    }

    override fun unaryOperation(insn: AbstractInsnNode, value: TrackedValue): TrackedValue? = when (insn.opcode) {
        Opcodes.GETFIELD -> {
            val field = insn as FieldInsnNode
            if (field.ownerInternalClassName == lambda.internalClassName && field.name == THIS0) {
                TrackedValue(1, isThisScript = true, setOf(insn)) // the source: reading the captured script
            } else {
                if (value.isThisScript)
                    classifyFieldRead(field, value) // reading a field of the script directly
                TrackedValue(Type.getType(field.desc).size, false)
            }
        }
        Opcodes.CHECKCAST -> {
            // A checkcast between the script load and its use would survive the rewrite and end up
            // casting the substituted Ref cell to the script type (a ClassCastException). Bail rather
            // than emit that, but keep the taint so any further use of the script is still detected.
            if (value.isThisScript)
                mutableResult.bail("script -> checkcast")
            value
        }
        Opcodes.PUTSTATIC -> {
            if (value.isThisScript)
                mutableResult.bail("script -> putstatic")
            null
        }
        Opcodes.INSTANCEOF -> {
            if (value.isThisScript)
                mutableResult.bail("script -> instanceof")
            TrackedValue(1, false)
        }
        Opcodes.INEG, Opcodes.L2I, Opcodes.F2I, Opcodes.D2I, Opcodes.I2B, Opcodes.I2C, Opcodes.I2S,
        Opcodes.FNEG, Opcodes.ARRAYLENGTH -> TrackedValue(1, false)
        Opcodes.LNEG, Opcodes.DNEG, Opcodes.I2L, Opcodes.F2L, Opcodes.D2L, Opcodes.I2D, Opcodes.L2D,
        Opcodes.F2D -> TrackedValue(2, false)
        else -> {
            if (value.isThisScript)
                mutableResult.bail("script -> unary op ${insn.opcode}")
            TrackedValue(1, false)
        }
    }

    override fun binaryOperation(insn: AbstractInsnNode, value1: TrackedValue, value2: TrackedValue): TrackedValue? {
        if (insn.opcode == Opcodes.PUTFIELD) {
            val field = insn as FieldInsnNode
            if (value2.isThisScript) mutableResult.bail("script stored into field ${field.name}")
            if (value1.isThisScript) classifyFieldWrite(field, value1) // this$0.<field> = ...
            return null
        }
        if (value1.isThisScript || value2.isThisScript) {
            // array loads / arithmetic on a script value are never legitimate lifts
            mutableResult.bail(if (insn.opcode == Opcodes.AALOAD) "script indexed" else "script -> binary op ${insn.opcode}")
        }
        return when (insn.opcode) {
            Opcodes.LALOAD, Opcodes.DALOAD, Opcodes.LADD, Opcodes.DADD, Opcodes.LSUB, Opcodes.DSUB,
            Opcodes.LMUL, Opcodes.DMUL, Opcodes.LDIV, Opcodes.DDIV, Opcodes.LREM, Opcodes.DREM,
            Opcodes.LSHL, Opcodes.LSHR, Opcodes.LUSHR, Opcodes.LAND, Opcodes.LOR, Opcodes.LXOR -> TrackedValue(2, false)
            else -> TrackedValue(1, false)
        }
    }

    override fun ternaryOperation(insn: AbstractInsnNode, value1: TrackedValue, value2: TrackedValue, value3: TrackedValue): TrackedValue? {
        if (value1.isThisScript || value2.isThisScript || value3.isThisScript) mutableResult.bail("script -> array store")
        return null
    }

    override fun naryOperation(insn: AbstractInsnNode, values: List<TrackedValue>): TrackedValue? = when (insn) {
        is MethodInsnNode -> {
            val hasReceiver = insn.opcode != Opcodes.INVOKESTATIC
            if (hasReceiver && values[0].isThisScript) {
                classifyReceiverCall(insn, values[0])
            }
            val firstArg = if (hasReceiver) 1 else 0
            for (i in firstArg until values.size) {
                if (values[i].isThisScript) checkScriptArgument(insn, i, values.size)
            }
            returnValueOf(insn.desc)
        }
        is InvokeDynamicInsnNode -> {
            if (values.any { it.isThisScript }) mutableResult.bail("script captured by invokedynamic")
            returnValueOf(insn.desc)
        }
        else -> { // MULTIANEWARRAY
            if (values.any { it.isThisScript }) mutableResult.bail("script -> multianewarray")
            TrackedValue(1, false)
        }
    }

    override fun returnOperation(insn: AbstractInsnNode, value: TrackedValue, expected: TrackedValue) {
        if (value.isThisScript) mutableResult.bail("script returned")
    }

    override fun merge(value1: TrackedValue, value2: TrackedValue): TrackedValue {
        if (value1 == value2) return value1
        // taint is conservative (OR); size is the safe minimum
        return TrackedValue(minOf(value1.slotSize, value2.slotSize), value1.isThisScript || value2.isThisScript, value1.producedBy + value2.producedBy)
    }

    private fun returnValueOf(methodDescriptor: String): TrackedValue? {
        val returnType = Type.getReturnType(methodDescriptor)
        return if (returnType.sort == Type.VOID) null else TrackedValue(returnType.size, false)
    }

    /** A tainted method argument is only allowed as the trailing script argument of a rewritable inner lambda's `<init>`. */
    private fun checkScriptArgument(call: MethodInsnNode, argIndex: Int, argCount: Int) {
        val threadsIntoInnerLambda = call.opcode == Opcodes.INVOKESPECIAL &&
            call.name == "<init>" &&
            call.ownerInternalClassName in lambdaClasses &&
            argIndex == argCount - 1 &&
            scriptIsLastAndUniqueArg(call.desc, lambda.receiverType)
        if (threadsIntoInnerLambda) {
            mutableResult.threaded.add(call.ownerInternalClassName)
        } else {
            mutableResult.bail("script passed as arg to ${call.owner}.${call.name}")
        }
    }

    private fun classifyReceiverCall(call: MethodInsnNode, receiver: TrackedValue) {
        if (call.ownerInternalClassName != lambda.receiverType) {
            mutableResult.bail("script call on non-script owner ${call.owner}")
            return
        }
        val valField = scriptModel.valGetterToField[call.name]
        if (valField != null && call.desc == "()" + scriptModel.valFieldDesc[valField]) {
            mutableResult.recordAccess(Access.Kind.VAL_READ, valField, call, receiver.producedBy)
            return
        }
        val varField = scriptModel.varField(call.name)
        if (varField != null) {
            val kind = if (call.name.startsWith("set")) Access.Kind.VAR_WRITE else Access.Kind.VAR_READ
            mutableResult.recordAccess(kind, varField, call, receiver.producedBy)
            return
        }
        mutableResult.bail("non-liftable script call ${call.name}${call.desc}")
    }

    private fun classifyFieldRead(field: FieldInsnNode, receiver: TrackedValue) {
        when {
            field.ownerInternalClassName != lambda.receiverType -> mutableResult.bail("field read on non-script owner")
            field.name in scriptModel.valFieldDesc -> mutableResult.recordAccess(Access.Kind.VAL_READ, field.name, field, receiver.producedBy)
            field.name in scriptModel.varFields -> mutableResult.recordAccess(Access.Kind.VAR_READ, field.name, field, receiver.producedBy)
            else -> mutableResult.bail("direct read of non-liftable script field ${field.name}")
        }
    }

    private fun classifyFieldWrite(field: FieldInsnNode, receiver: TrackedValue) {
        if (field.ownerInternalClassName == lambda.receiverType && field.name in scriptModel.varFields) {
            mutableResult.recordAccess(Access.Kind.VAR_WRITE, field.name, field, receiver.producedBy)
        } else {
            mutableResult.bail("direct write of non-var script field ${field.name}")
        }
    }
}
