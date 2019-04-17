/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility.metadata

import javassist.CtClass
import javassist.CtConstructor
import javassist.CtField
import javassist.CtMember
import javassist.CtMethod
import javassist.Modifier
import javassist.bytecode.annotation.Annotation
import javassist.bytecode.annotation.AnnotationImpl

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmClassExtensionVisitor
import kotlinx.metadata.KmClassVisitor
import kotlinx.metadata.KmConstructorExtensionVisitor
import kotlinx.metadata.KmConstructorVisitor
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.KmFunctionExtensionVisitor
import kotlinx.metadata.KmFunctionVisitor
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.JvmClassExtensionVisitor
import kotlinx.metadata.jvm.JvmConstructorExtensionVisitor
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor

import org.gradle.binarycompatibility.intArrayValue
import org.gradle.binarycompatibility.intValue
import org.gradle.binarycompatibility.stringArrayValue
import org.gradle.binarycompatibility.stringValue

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
        else queryKotlinMetadata(ctClass, false) { metadata ->
            metadata.isKotlinInternal(ctClass.name, MemberType.TYPE)
        }

    fun isKotlinInternal(ctMember: CtMember): Boolean =
        if (Modifier.isPrivate(ctMember.modifiers)) false
        else queryKotlinMetadata(ctMember.declaringClass, false) { metadata ->
            metadata.isKotlinInternal(ctMember.jvmSignature, memberTypeFor(ctMember))
        }

    private
    fun <T : Any?> queryKotlinMetadata(ctClass: CtClass, defaultResult: T, query: (KotlinClassMetadata) -> T): T =
        ctClass.kotlinClassHeader
            ?.let { KotlinClassMetadata.read(it) }
            ?.let { query(it) }
            ?: defaultResult

    private
    fun KotlinClassMetadata.isKotlinInternal(jvmSignature: String, memberType: MemberType): Boolean {

        var isInternal = false
        var isDoneVisiting = false

        val internalFunctionVisitor = object : KmFunctionVisitor() {
            override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? =
                if (isDoneVisiting) null
                else object : JvmFunctionExtensionVisitor() {
                    override fun visit(desc: JvmMethodSignature?) {
                        if (jvmSignature == desc?.asString()) {
                            isInternal = true
                            isDoneVisiting = true
                        }
                    }
                }
        }

        val internalPropertyVisitorFactory: (Flags, Flags, Flags) -> KmPropertyVisitor? = { flags, getterFlags, setterFlags ->
            if (isDoneVisiting || (memberType != MemberType.FIELD && memberType != MemberType.METHOD)) null
            else {
                val internalField = Flag.IS_INTERNAL(flags)
                val internalGetter = Flag.IS_INTERNAL(getterFlags)
                val internalSetter = Flag.IS_INTERNAL(setterFlags)
                if (!internalField && !internalGetter && !internalSetter) null
                else object : KmPropertyVisitor() {
                    override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor =
                        object : JvmPropertyExtensionVisitor() {
                            override fun visit(fieldDesc: JvmFieldSignature?, getterDesc: JvmMethodSignature?, setterDesc: JvmMethodSignature?) {
                                if (
                                    (internalField && jvmSignature == fieldDesc?.asString())
                                    || (internalGetter && jvmSignature == getterDesc?.asString())
                                    || (internalSetter && jvmSignature == setterDesc?.asString())
                                ) {
                                    isInternal = true
                                    isDoneVisiting = true
                                }
                            }
                        }
                }
            }
        }

        val internalPackageVisitor = object : KmPackageVisitor() {

            override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
                if (isDoneVisiting || memberType != MemberType.METHOD || !Flag.IS_INTERNAL(flags)) null
                else internalFunctionVisitor

            override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                internalPropertyVisitorFactory(flags, getterFlags, setterFlags)

            override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? =
                if (isDoneVisiting) null
                else object : JvmPackageExtensionVisitor() {
                    override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        internalPropertyVisitorFactory(flags, getterFlags, setterFlags)
                }
        }

        val internalClassVisitor = object : KmClassVisitor() {

            override fun visit(flags: Flags, name: ClassName) {
                if (isDoneVisiting || memberType != MemberType.TYPE) return
                if (Flag.IS_INTERNAL(flags) && jvmSignature == name.replace("/", ".")) {
                    isInternal = true
                    isDoneVisiting = true
                }
            }

            override fun visitConstructor(flags: Flags): KmConstructorVisitor? =
                if (isDoneVisiting || memberType != MemberType.CONSTRUCTOR || !Flag.IS_INTERNAL(flags)) null
                else object : KmConstructorVisitor() {
                    override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor =
                        object : JvmConstructorExtensionVisitor() {
                            override fun visit(desc: JvmMethodSignature?) {
                                if (jvmSignature == desc?.asString()) {
                                    isInternal = true
                                    isDoneVisiting = true
                                }
                            }
                        }
                }

            override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
                if (isDoneVisiting || memberType != MemberType.METHOD || !Flag.IS_INTERNAL(flags)) null
                else internalFunctionVisitor

            override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                internalPropertyVisitorFactory(flags, getterFlags, setterFlags)

            override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? =
                if (isDoneVisiting) null
                else object : JvmClassExtensionVisitor() {
                    override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        internalPropertyVisitorFactory(flags, getterFlags, setterFlags)
                }
        }

        when (this) {
            is KotlinClassMetadata.Class -> accept(internalClassVisitor)
            is KotlinClassMetadata.FileFacade -> accept(internalPackageVisitor)
            is KotlinClassMetadata.MultiFileClassPart -> accept(internalPackageVisitor)
            is KotlinClassMetadata.MultiFileClassFacade -> Unit
            is KotlinClassMetadata.SyntheticClass -> Unit
            is KotlinClassMetadata.Unknown -> Unit
            else -> throw IllegalStateException("Unsupported Kotlin metadata type '${this::class}'")
        }

        return isInternal
    }

    private
    enum class MemberType {
        TYPE, CONSTRUCTOR, FIELD, METHOD;
    }

    private
    fun memberTypeFor(member: CtMember): MemberType =
        when (member) {
            is CtConstructor -> MemberType.CONSTRUCTOR
            is CtField -> MemberType.FIELD
            is CtMethod -> MemberType.METHOD
            else -> throw IllegalArgumentException("Unsupported javassist member type '${member::class}'")
        }

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
