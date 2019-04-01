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

    fun <T : Any?> queryKotlinMetadata(ctClass: CtClass, defaultResult: T, query: (KotlinClassMetadata) -> T): T =
        ctClass.kotlinClassHeader
            ?.let { KotlinClassMetadata.read(it) }
            ?.let { query(it) }
            ?: defaultResult

    fun isKotlinFileFacadeClass(): (KotlinClassMetadata) -> Boolean = { metadata ->
        when (metadata) {
            is KotlinClassMetadata.FileFacade -> true
            else -> false
        }
    }

    fun isKotlinInternal(ctClass: CtClass): (KotlinClassMetadata) -> Boolean = { metadata ->
        if (Modifier.isPrivate(ctClass.modifiers)) false
        else metadata.isKotlinInternal(ctClass.name, isConstructor = false, isField = false, isMethod = false)
    }

    fun isKotlinInternal(ctMember: CtMember): (KotlinClassMetadata) -> Boolean = { metadata ->
        if (Modifier.isPrivate(ctMember.modifiers)) false
        else metadata.isKotlinInternal(ctMember.jvmSignature, ctMember is CtConstructor, ctMember is CtField, ctMember is CtMethod)
    }

    private
    fun KotlinClassMetadata.isKotlinInternal(jvmSignature: String, isConstructor: Boolean, isField: Boolean, isMethod: Boolean): Boolean {

        var isInternal = false

        val internalFunctionVisitor = object : KmFunctionVisitor() {
            override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? =
                if (isInternal || !isConstructor || type != JvmFunctionExtensionVisitor.TYPE) null
                else object : JvmFunctionExtensionVisitor() {
                    override fun visit(desc: JvmMethodSignature?) {
                        if (jvmSignature == desc?.asString()) {
                            isInternal = true
                        }
                    }
                }
        }

        val internalPropertyVisitorFactory: (Flags, Flags, Flags) -> KmPropertyVisitor? = { flags, getterFlags, setterFlags ->
            if (isInternal || (!isField && !isMethod)) null
            else {
                val internalField = Flag.IS_INTERNAL(flags)
                val internalGetter = Flag.IS_INTERNAL(getterFlags)
                val internalSetter = Flag.IS_INTERNAL(setterFlags)
                if (!internalField && !internalGetter && !internalSetter) null
                else object : KmPropertyVisitor() {
                    override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
                        if (type != JvmPropertyExtensionVisitor.TYPE) return null
                        return object : JvmPropertyExtensionVisitor() {
                            override fun visit(fieldDesc: JvmFieldSignature?, getterDesc: JvmMethodSignature?, setterDesc: JvmMethodSignature?) {
                                if (
                                    (internalField && jvmSignature == fieldDesc?.asString())
                                    || (internalGetter && jvmSignature == getterDesc?.asString())
                                    || (internalSetter && jvmSignature == setterDesc?.asString())
                                ) {
                                    isInternal = true
                                }
                            }
                        }
                    }
                }
            }
        }

        val packageVisitor = object : KmPackageVisitor() {

            override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
                if (isInternal || !isMethod || !Flag.IS_INTERNAL(flags)) null
                else internalFunctionVisitor

            override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                internalPropertyVisitorFactory(flags, getterFlags, setterFlags)

            override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? =
                if (isInternal || type != JvmPackageExtensionVisitor.TYPE) null
                else object : JvmPackageExtensionVisitor() {
                    override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        internalPropertyVisitorFactory(flags, getterFlags, setterFlags)
                }
        }

        when (this) {
            is KotlinClassMetadata.Class -> {
                accept(object : KmClassVisitor() {

                    override fun visit(flags: Flags, name: ClassName) {
                        if (Flag.IS_INTERNAL(flags)) {
                            isInternal = true
                        }
                    }

                    override fun visitConstructor(flags: Flags): KmConstructorVisitor? =
                        if (isInternal || !Flag.IS_INTERNAL(flags)) null
                        else object : KmConstructorVisitor() {
                            override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor? =
                                if (isInternal || type != JvmConstructorExtensionVisitor.TYPE) null
                                else object : JvmConstructorExtensionVisitor() {
                                    override fun visit(desc: JvmMethodSignature?) {
                                        if (jvmSignature == desc?.asString()) {
                                            isInternal = true
                                        }
                                    }
                                }
                        }

                    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
                        if (isInternal || !isMethod || !Flag.IS_INTERNAL(flags)) null
                        else internalFunctionVisitor

                    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                        internalPropertyVisitorFactory(flags, getterFlags, setterFlags)

                    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? =
                        if (isInternal || type != JvmClassExtensionVisitor.TYPE) null
                        else object : JvmClassExtensionVisitor() {
                            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                                internalPropertyVisitorFactory(flags, getterFlags, setterFlags)
                        }
                })
            }
            is KotlinClassMetadata.FileFacade -> {
                accept(packageVisitor)
            }
            is KotlinClassMetadata.MultiFileClassPart -> {
                accept(packageVisitor)
            }
            is KotlinClassMetadata.MultiFileClassFacade -> Unit
            is KotlinClassMetadata.SyntheticClass -> Unit
            is KotlinClassMetadata.Unknown -> Unit
            else -> throw IllegalStateException("Unsupported Kotlin metadata type '${this::class}'")
        }

        return isInternal
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
