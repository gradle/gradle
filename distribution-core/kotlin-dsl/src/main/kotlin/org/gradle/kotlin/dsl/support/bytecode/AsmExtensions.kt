/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.support.bytecode

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Opcodes.T_BYTE
import org.jetbrains.org.objectweb.asm.Type

import kotlin.jvm.internal.CallableReference
import kotlin.reflect.KClass
import kotlin.reflect.KProperty


internal
fun publicClass(
    name: InternalName,
    superName: InternalName? = null,
    interfaces: List<InternalName>? = null,
    classBody: ClassWriter.() -> Unit = {}
) = beginPublicClass(name, superName, interfaces).run {
    classBody()
    endClass()
}


internal
fun beginPublicClass(name: InternalName, superName: InternalName? = null, interfaces: List<InternalName>? = null) =
    beginClass(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, name, superName, interfaces)


internal
fun beginClass(
    modifiers: Int,
    name: InternalName,
    superName: InternalName? = null,
    interfaces: List<InternalName>? = null
): ClassWriter = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES).apply {
    visit(
        Opcodes.V1_8,
        modifiers,
        name.value,
        null,
        (superName ?: InternalNameOf.javaLangObject).value,
        interfaces?.map { it.value }?.toTypedArray()
    )
}


internal
fun ClassWriter.endClass(): ByteArray {
    visitEnd()
    return toByteArray()
}


internal
fun ClassWriter.publicDefaultConstructor(superName: InternalName = InternalNameOf.javaLangObject) {
    publicMethod("<init>", "()V") {
        ALOAD(0)
        INVOKESPECIAL(superName, "<init>", "()V")
        RETURN()
    }
}


internal
fun ClassVisitor.publicStaticMethod(
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    deprecated: Boolean = false,
    methodBody: MethodVisitor.() -> Unit,
    annotations: MethodVisitor.() -> Unit
) {
    method(
        Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + if (deprecated) {
            Opcodes.ACC_DEPRECATED
        } else {
            0
        },
        name, desc, signature, exceptions, annotations, methodBody
    )
}


internal
fun ClassVisitor.publicMethod(
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    annotations: MethodVisitor.() -> Unit = {},
    methodBody: MethodVisitor.() -> Unit
) {
    method(Opcodes.ACC_PUBLIC, name, desc, signature, exceptions, annotations, methodBody)
}


internal
fun ClassVisitor.method(
    access: Int,
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    annotations: MethodVisitor.() -> Unit = {},
    methodBody: MethodVisitor.() -> Unit
) {
    visitMethod(access, name, desc, signature, exceptions).apply {
        annotations()
        visitCode()
        methodBody()
        visitMaxs(0, 0)
        visitEnd()
    }
}


internal
fun MethodVisitor.loadByteArray(byteArray: ByteArray) {
    LDC(byteArray.size)
    NEWARRAY(T_BYTE)
    for ((i, byte) in byteArray.withIndex()) {
        DUP()
        LDC(i)
        LDC(byte)
        BASTORE()
    }
}


internal
fun MethodVisitor.NEW(type: InternalName) {
    visitTypeInsn(Opcodes.NEW, type)
}


internal
fun MethodVisitor.visitTypeInsn(opcode: Int, type: InternalName) {
    visitTypeInsn(opcode, type.value)
}


internal
fun MethodVisitor.NEWARRAY(primitiveType: Int) {
    visitIntInsn(Opcodes.NEWARRAY, primitiveType)
}


internal
fun MethodVisitor.LDC(type: InternalName) {
    visitLdcInsn(Type.getType("L${type.value};"))
}


internal
fun MethodVisitor.LDC(value: Any) {
    visitLdcInsn(value)
}


internal
fun MethodVisitor.INVOKEVIRTUAL(owner: InternalName, name: String, desc: String, itf: Boolean = false) {
    visitMethodInsn_(Opcodes.INVOKEVIRTUAL, owner, name, desc, itf)
}


internal
fun MethodVisitor.INVOKESPECIAL(owner: InternalName, name: String, desc: String, itf: Boolean = false) {
    visitMethodInsn_(Opcodes.INVOKESPECIAL, owner, name, desc, itf)
}


internal
fun MethodVisitor.INVOKEINTERFACE(owner: InternalName, name: String, desc: String, itf: Boolean = true) {
    visitMethodInsn_(Opcodes.INVOKEINTERFACE, owner, name, desc, itf)
}


internal
fun MethodVisitor.INVOKESTATIC(owner: InternalName, name: String, desc: String) {
    visitMethodInsn_(Opcodes.INVOKESTATIC, owner, name, desc, false)
}


private
fun MethodVisitor.visitMethodInsn_(opcode: Int, owner: InternalName, name: String, desc: String, itf: Boolean) {
    visitMethodInsn(opcode, owner.value, name, desc, itf)
}


internal
fun MethodVisitor.BASTORE() {
    visitInsn(Opcodes.BASTORE)
}


internal
fun MethodVisitor.DUP() {
    visitInsn(Opcodes.DUP)
}


internal
fun MethodVisitor.ARETURN() {
    visitInsn(Opcodes.ARETURN)
}


internal
fun MethodVisitor.RETURN() {
    visitInsn(Opcodes.RETURN)
}


internal
fun MethodVisitor.ALOAD(`var`: Int) {
    visitVarInsn(Opcodes.ALOAD, `var`)
}


internal
fun MethodVisitor.ASTORE(`var`: Int) {
    visitVarInsn(Opcodes.ASTORE, `var`)
}


internal
fun MethodVisitor.GOTO(label: Label) {
    visitJumpInsn(Opcodes.GOTO, label)
}


internal
inline fun <reified T> MethodVisitor.TRY_CATCH(
    noinline tryBlock: MethodVisitor.() -> Unit,
    noinline catchBlock: MethodVisitor.() -> Unit
) =
    TRY_CATCH(T::class.internalName, tryBlock, catchBlock)


internal
fun MethodVisitor.TRY_CATCH(
    exceptionType: InternalName,
    tryBlock: MethodVisitor.() -> Unit,
    catchBlock: MethodVisitor.() -> Unit
) {

    val tryBlockStart = Label()
    val tryBlockEnd = Label()
    val catchBlockStart = Label()
    val catchBlockEnd = Label()
    visitTryCatchBlock(tryBlockStart, tryBlockEnd, catchBlockStart, exceptionType.value)

    visitLabel(tryBlockStart)
    tryBlock()
    GOTO(catchBlockEnd)
    visitLabel(tryBlockEnd)

    visitLabel(catchBlockStart)
    catchBlock()
    visitLabel(catchBlockEnd)
}


internal
fun <T : Enum<T>> MethodVisitor.GETSTATIC(field: T) {
    val owner = field.declaringClass.internalName
    GETSTATIC(owner, field.name, "L$owner;")
}


internal
fun MethodVisitor.GETSTATIC(field: KProperty<*>) {
    require(field is CallableReference)
    val owner = (field.owner as kotlin.jvm.internal.ClassBasedDeclarationContainer).jClass.internalName
    GETSTATIC(owner, field.name, "L$owner;")
}


internal
fun MethodVisitor.GETSTATIC(owner: InternalName, name: String, desc: String) {
    visitFieldInsn(Opcodes.GETSTATIC, owner.value, name, desc)
}


internal
fun MethodVisitor.GETFIELD(owner: InternalName, name: String, desc: String) {
    visitFieldInsn(Opcodes.GETFIELD, owner.value, name, desc)
}


internal
fun MethodVisitor.PUTFIELD(owner: InternalName, name: String, desc: String) {
    visitFieldInsn(Opcodes.PUTFIELD, owner.value, name, desc)
}


internal
fun MethodVisitor.CHECKCAST(type: KClass<*>) {
    CHECKCAST(type.internalName)
}


internal
fun MethodVisitor.CHECKCAST(type: InternalName) {
    visitTypeInsn(Opcodes.CHECKCAST, type)
}


internal
fun MethodVisitor.ACONST_NULL() {
    visitInsn(Opcodes.ACONST_NULL)
}


internal
fun MethodVisitor.kotlinDeprecation(message: String) {
    visitAnnotation("Lkotlin/Deprecated;", true).apply {
        visit("message", message)
        visitEnd()
    }
}


/**
 * A JVM internal type name (as in `java/lang/Object` instead of `java.lang.Object`).
 */
@Suppress("experimental_feature_warning")
internal
inline class InternalName(val value: String) {

    companion object {
        fun from(sourceName: String) = InternalName(sourceName.replace('.', '/'))
    }

    override fun toString() = value
}


internal
object InternalNameOf {

    val javaLangObject = InternalName("java/lang/Object")
}


internal
val KClass<*>.internalName: InternalName
    get() = java.internalName


internal
inline val Class<*>.internalName: InternalName
    get() = InternalName(Type.getInternalName(this))
