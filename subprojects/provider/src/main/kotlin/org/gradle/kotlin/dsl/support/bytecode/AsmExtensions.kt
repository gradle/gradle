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

import kotlin.reflect.KClass


internal
fun publicClass(
    name: String,
    superName: String = "java/lang/Object",
    interfaces: Array<String>? = null,
    classBody: ClassWriter.() -> Unit = {}
) = ClassWriter(ClassWriter.COMPUTE_MAXS + ClassWriter.COMPUTE_FRAMES).run {
    visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, name, null, superName, interfaces)
    classBody()
    visitEnd()
    toByteArray()
}


internal
fun ClassWriter.publicDefaultConstructor(superName: String) {
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
    methodBody: MethodVisitor.() -> Unit
) {
    method(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, name, desc, signature, exceptions, methodBody)
}


internal
fun ClassVisitor.publicMethod(
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    methodBody: MethodVisitor.() -> Unit
) {
    method(Opcodes.ACC_PUBLIC, name, desc, signature, exceptions, methodBody)
}


internal
fun ClassVisitor.method(
    access: Int,
    name: String,
    desc: String,
    signature: String? = null,
    exceptions: Array<String>? = null,
    methodBody: MethodVisitor.() -> Unit
) {
    visitMethod(access, name, desc, signature, exceptions).apply {
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
fun MethodVisitor.NEW(type: String) {
    visitTypeInsn(Opcodes.NEW, type)
}


internal
fun MethodVisitor.NEWARRAY(primitiveType: Int) {
    visitIntInsn(Opcodes.NEWARRAY, primitiveType)
}


internal
fun MethodVisitor.LDC(value: Any) {
    visitLdcInsn(value)
}


internal
fun MethodVisitor.INVOKEVIRTUAL(owner: String, name: String, desc: String, itf: Boolean = false) {
    visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, itf)
}


internal
fun MethodVisitor.INVOKESPECIAL(owner: String, name: String, desc: String, itf: Boolean = false) {
    visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, desc, itf)
}


internal
fun MethodVisitor.INVOKEINTERFACE(owner: String, name: String, desc: String, itf: Boolean = true) {
    visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, desc, itf)
}


internal
fun MethodVisitor.INVOKESTATIC(owner: String, name: String, desc: String) {
    visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false)
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
    exceptionType: String,
    tryBlock: MethodVisitor.() -> Unit,
    catchBlock: MethodVisitor.() -> Unit
) {

    val tryBlockStart = Label()
    val tryBlockEnd = Label()
    val catchBlockStart = Label()
    val catchBlockEnd = Label()
    visitTryCatchBlock(tryBlockStart, tryBlockEnd, catchBlockStart, exceptionType)

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
fun MethodVisitor.GETSTATIC(owner: String, name: String, desc: String) {
    visitFieldInsn(Opcodes.GETSTATIC, owner, name, desc)
}


internal
fun MethodVisitor.GETFIELD(owner: String, name: String, desc: String) {
    visitFieldInsn(Opcodes.GETFIELD, owner, name, desc)
}


internal
fun MethodVisitor.PUTFIELD(owner: String, name: String, desc: String) {
    visitFieldInsn(Opcodes.PUTFIELD, owner, name, desc)
}


internal
fun MethodVisitor.CHECKCAST(type: String) {
    visitTypeInsn(Opcodes.CHECKCAST, type)
}


internal
fun MethodVisitor.ACONST_NULL() {
    visitInsn(Opcodes.ACONST_NULL)
}


internal
val KClass<*>.internalName: String
    get() = java.internalName


internal
inline val Class<*>.internalName: String
    get() = Type.getInternalName(this)
