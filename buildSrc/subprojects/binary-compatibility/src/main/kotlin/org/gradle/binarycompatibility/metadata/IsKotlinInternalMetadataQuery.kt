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

import kotlinx.metadata.ClassName
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
import kotlinx.metadata.jvm.JvmClassExtensionVisitor
import kotlinx.metadata.jvm.JvmConstructorExtensionVisitor
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmFunctionExtensionVisitor
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.KotlinClassMetadata


internal
fun KotlinClassMetadata.isKotlinInternal(memberType: MemberType, jvmSignature: String): Boolean =
    when (this) {
        is KotlinClassMetadata.Class -> classVisitorFor(memberType, jvmSignature).apply(::accept).isKotlinInternal
        is KotlinClassMetadata.FileFacade -> packageVisitorFor(memberType, jvmSignature).apply(::accept).isKotlinInternal
        is KotlinClassMetadata.MultiFileClassPart -> packageVisitorFor(memberType, jvmSignature).apply(::accept).isKotlinInternal
        is KotlinClassMetadata.MultiFileClassFacade -> false
        is KotlinClassMetadata.SyntheticClass -> false
        is KotlinClassMetadata.Unknown -> false
        else -> throw IllegalStateException("Unsupported Kotlin metadata type '${this::class}'")
    }


@Suppress("unchecked_cast")
private
fun <T> classVisitorFor(memberType: MemberType, jvmSignature: String): T where T : KmClassVisitor, T : IsKotlinInternal =
    when (memberType) {
        MemberType.TYPE -> IsInternalTypeKmClassVisitor(jvmSignature)
        MemberType.FIELD -> IsInternalFieldKmClassVisitor(jvmSignature)
        MemberType.CONSTRUCTOR -> IsInternalConstructorKmClassVisitor(jvmSignature)
        MemberType.METHOD -> IsInternalMethodKmClassVisitor(jvmSignature)
    } as T


@Suppress("unchecked_cast")
private
fun <T> packageVisitorFor(memberType: MemberType, jvmSignature: String): T where T : KmPackageVisitor, T : IsKotlinInternal =
    when (memberType) {
        MemberType.FIELD -> IsInternalFieldKmPackageVisitor(jvmSignature)
        MemberType.METHOD -> IsInternalMethodKmPackageVisitor(jvmSignature)
        else -> IsInternalNoopKmPackageVisitor
    } as T


private
interface IsKotlinInternal {
    var isKotlinInternal: Boolean
}


private
class IsInternalTypeKmClassVisitor(
    private val jvmSignature: String
) : KmClassVisitor(), IsKotlinInternal {

    override var isKotlinInternal = false

    private
    var isDoneVisiting = false

    override fun visit(flags: Flags, name: ClassName) {
        if (!isDoneVisiting && jvmSignature == name.replace("/", ".")) {
            isKotlinInternal = flags.isInternal
            isDoneVisiting = true
        }
    }
}


private
class IsInternalFieldKmClassVisitor(
    private val jvmSignature: String
) : KmClassVisitor(), IsKotlinInternal {

    override var isKotlinInternal = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { isInternal: Boolean ->
        isKotlinInternal = isInternal
        isDoneVisiting = true
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmClassExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
                IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)
        }
}


private
class IsInternalConstructorKmClassVisitor(
    private val jvmSignature: String
) : KmClassVisitor(), IsKotlinInternal {

    override var isKotlinInternal = false

    private
    var isDoneVisiting = false

    override fun visitConstructor(flags: Flags): KmConstructorVisitor? =
        if (isDoneVisiting) null
        else object : KmConstructorVisitor() {
            override fun visitExtensions(type: KmExtensionType): KmConstructorExtensionVisitor =
                object : JvmConstructorExtensionVisitor() {
                    override fun visit(desc: JvmMethodSignature?) {
                        if (jvmSignature == desc?.asString()) {
                            isKotlinInternal = flags.isInternal
                            isDoneVisiting = true
                        }
                    }
                }
        }
}


private
class IsInternalMethodKmClassVisitor(
    private val jvmSignature: String
) : KmClassVisitor(), IsKotlinInternal {

    override var isKotlinInternal = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { isInternal: Boolean ->
        isKotlinInternal = isInternal
        isDoneVisiting = true
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
        if (isDoneVisiting) null
        else IsInternalMethodKmFunctionVisitor(jvmSignature, flags, onMatch)

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmClassExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmClassExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor =
                IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)
        }
}


private
class IsInternalFieldKmPackageVisitor(
    private val jvmSignature: String
) : KmPackageVisitor(), IsKotlinInternal {

    override var isKotlinInternal = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { isInternal: Boolean ->
        isKotlinInternal = isInternal
        isDoneVisiting = true
    }

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmPackageExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor =
                IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)
        }
}


private
class IsInternalMethodKmPackageVisitor(
    private val jvmSignature: String
) : KmPackageVisitor(), IsKotlinInternal {

    override var isKotlinInternal = false

    private
    var isDoneVisiting = false

    private
    val onMatch = { isInternal: Boolean ->
        isKotlinInternal = isInternal
        isDoneVisiting = true
    }

    override fun visitFunction(flags: Flags, name: String): KmFunctionVisitor? =
        if (isDoneVisiting) null
        else IsInternalMethodKmFunctionVisitor(jvmSignature, flags, onMatch)

    override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? =
        if (isDoneVisiting) null
        else IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)

    override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? =
        if (isDoneVisiting) null
        else object : JvmPackageExtensionVisitor() {
            override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor =
                IsInternalMemberKmPropertyExtensionVisitor(jvmSignature, flags, getterFlags, setterFlags, onMatch)
        }
}


private
object IsInternalNoopKmPackageVisitor : KmPackageVisitor(), IsKotlinInternal {
    override var isKotlinInternal = false
}


private
class IsInternalMemberKmPropertyExtensionVisitor(
    private val jvmSignature: String,
    private val fieldFlags: Flags,
    private val getterFlags: Flags,
    private val setterFlags: Flags,
    private val onMatch: (Boolean) -> Unit
) : KmPropertyVisitor() {

    private
    val kmPropertyExtensionVisitor = object : JvmPropertyExtensionVisitor() {
        override fun visit(fieldDesc: JvmFieldSignature?, getterDesc: JvmMethodSignature?, setterDesc: JvmMethodSignature?) {
            when (jvmSignature) {
                fieldDesc?.asString() -> onMatch(fieldFlags.isInternal)
                getterDesc?.asString() -> onMatch(getterFlags.isInternal)
                setterDesc?.asString() -> onMatch(setterFlags.isInternal)
            }
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor =
        kmPropertyExtensionVisitor
}


private
class IsInternalMethodKmFunctionVisitor(
    private val jvmSignature: String,
    private val functionFlags: Flags,
    private val onMatch: (Boolean) -> Unit
) : KmFunctionVisitor() {

    private
    val kmFunctionExtensionVisitor = object : JvmFunctionExtensionVisitor() {
        override fun visit(desc: JvmMethodSignature?) {
            if (jvmSignature == desc?.asString()) {
                onMatch(functionFlags.isInternal)
            }
        }
    }

    override fun visitExtensions(type: KmExtensionType): KmFunctionExtensionVisitor? =
        kmFunctionExtensionVisitor
}


private
val Flags.isInternal: Boolean
    get() = kotlinx.metadata.Flag.IS_INTERNAL(this)
