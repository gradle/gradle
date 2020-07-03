/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility.metadata

import gradlebuild.binarycompatibility.intArrayValue
import gradlebuild.binarycompatibility.intValue
import gradlebuild.binarycompatibility.stringArrayValue
import gradlebuild.binarycompatibility.stringValue
import javassist.CtClass
import javassist.CtConstructor
import javassist.CtField
import javassist.CtMember
import javassist.CtMethod
import javassist.Modifier
import javassist.bytecode.annotation.Annotation
import javassist.bytecode.annotation.AnnotationImpl
import kotlinx.metadata.Flag
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import java.lang.reflect.Proxy


object KotlinMetadataQueries {

    fun isKotlinFileFacadeClass(ctClass: CtClass): Boolean =
        if (Modifier.isPrivate(ctClass.modifiers)) false
        else queryKotlinMetadata(ctClass, false) { metadata ->
            when (metadata) {
                is KotlinClassMetadata.FileFacade -> true
                else -> false
            }
        }

    fun isKotlinInternal(ctClass: CtClass): Boolean =
        if (Modifier.isPrivate(ctClass.modifiers)) false
        else hasKotlinFlag(ctClass, Flag.IS_INTERNAL)

    fun isKotlinInternal(ctMember: CtMember): Boolean =
        if (Modifier.isPrivate(ctMember.modifiers)) false
        else hasKotlinFlag(ctMember, Flag.IS_INTERNAL)

    fun isKotlinOperatorFunction(ctMethod: CtMethod): Boolean =
        hasKotlinFlag(ctMethod, Flag.Function.IS_OPERATOR)

    fun isKotlinInfixFunction(ctMethod: CtMethod): Boolean =
        hasKotlinFlag(ctMethod, Flag.Function.IS_INFIX)

    private
    fun hasKotlinFlag(ctClass: CtClass, flag: Flag): Boolean =
        queryKotlinMetadata(ctClass, false) { metadata ->
            metadata.hasKotlinFlag(MemberType.TYPE, ctClass.name, flag)
        }

    private
    fun hasKotlinFlag(ctMember: CtMember, flag: Flag): Boolean =
        queryKotlinMetadata(ctMember.declaringClass, false) { metadata ->
            metadata.hasKotlinFlag(memberTypeFor(ctMember), ctMember.jvmSignature, flag)
        }

    private
    fun <T : Any?> queryKotlinMetadata(ctClass: CtClass, defaultResult: T, query: (KotlinClassMetadata) -> T): T =
        ctClass.kotlinClassHeader
            ?.let { KotlinClassMetadata.read(it) }
            ?.let { query(it) }
            ?: defaultResult

    private
    val CtClass.kotlinClassHeader: KotlinClassHeader?
        get() = ctAnnotation<Metadata>()?.let { annotation ->
            KotlinClassHeader(
                kind = annotation.getMemberValue("k")?.intValue,
                metadataVersion = annotation.getMemberValue("mv")?.intArrayValue,
                bytecodeVersion = annotation.getMemberValue("bv")?.intArrayValue,
                data1 = annotation.getMemberValue("d1")?.stringArrayValue,
                data2 = annotation.getMemberValue("d2")?.stringArrayValue,
                extraString = annotation.getMemberValue("xs")?.stringValue,
                packageName = annotation.getMemberValue("pn")?.stringValue,
                extraInt = annotation.getMemberValue("xi")?.intValue
            )
        }

    private
    inline fun <reified T : Any> CtClass.ctAnnotation(): Annotation? =
        getAnnotation(T::class.java)
            ?.takeIf { Proxy.isProxyClass(it::class.java) }
            ?.let { Proxy.getInvocationHandler(it) as? AnnotationImpl }
            ?.annotation

    private
    val CtMember.jvmSignature: String
        get() = when (this) {
            is CtField -> "$name:$signature"
            is CtConstructor ->
                if (parameterTypes.isEmpty()) "$name$signature"
                else "<init>$signature"
            is CtMethod -> "$name$signature"
            else -> throw IllegalArgumentException("Unsupported javassist member type '${this::class}'")
        }
}


internal
enum class MemberType {
    TYPE, CONSTRUCTOR, FIELD, METHOD
}


private
fun memberTypeFor(member: CtMember): MemberType =
    when (member) {
        is CtConstructor -> MemberType.CONSTRUCTOR
        is CtField -> MemberType.FIELD
        is CtMethod -> MemberType.METHOD
        else -> throw IllegalArgumentException("Unsupported javassist member type '${member::class}'")
    }
