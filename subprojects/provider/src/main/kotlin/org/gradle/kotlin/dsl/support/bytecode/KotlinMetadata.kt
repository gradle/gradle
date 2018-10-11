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

import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata

import org.jetbrains.org.objectweb.asm.ClassWriter

import java.io.File


internal
fun publicKotlinClass(
    internalClassName: String,
    header: KotlinClassHeader,
    classBody: ClassWriter.() -> Unit
): ByteArray = publicClass(internalClassName) {
    visitKotlinMetadataAnnotation(header)
    classBody()
}


internal
fun writeFileFacadeClassHeader(fileFacadeWriter: KotlinClassMetadata.FileFacade.Writer.() -> Unit) =
    KotlinClassMetadata.FileFacade.Writer().run {
        fileFacadeWriter()
        (visitExtensions(JvmPackageExtensionVisitor.TYPE) as KmPackageExtensionVisitor).run {
            visitEnd()
        }
        visitEnd()
        write().header
    }


internal
fun moduleMetadataBytesFor(fileFacades: List<String>): ByteArray =
    KotlinModuleMetadata.Writer().run {
        visitPackageParts("org.gradle.kotlin.dsl", fileFacades, emptyMap())
        visitEnd()
        write().bytes
    }


internal
fun moduleFileFor(baseDir: File, moduleName: String = baseDir.name) =
    baseDir.resolve("META-INF").resolve("$moduleName.kotlin_module")


/**
 * Writes the given [header] to the class file as a [kotlin.Metadata] annotation.
 **/
internal
fun ClassWriter.visitKotlinMetadataAnnotation(header: KotlinClassHeader) {
    visitAnnotation("Lkotlin/Metadata;", true).run {
        visit("mv", header.metadataVersion)
        visit("bv", header.bytecodeVersion)
        visit("k", header.kind)
        visitArray("d1").run {
            header.data1.forEach { visit(null, it) }
            visitEnd()
        }
        visitArray("d2").run {
            header.data2.forEach { visit(null, it) }
            visitEnd()
        }
        visitEnd()
    }
}


internal
fun KotlinClassMetadata.FileFacade.Writer.writePropertyOf(
    receiverType: KmTypeBuilder,
    returnType: KmTypeBuilder,
    propertyName: String,
    getterSignature: JvmMethodSignature,
    getterFlags: Flags = inlineGetterFlags
) {
    visitProperty(readOnlyPropertyFlags, propertyName, getterFlags, 6)!!.run {
        visitReceiverParameterType(0)!!.run {
            receiverType()
            visitEnd()
        }
        visitReturnType(0)!!.run {
            returnType()
            visitEnd()
        }
        (visitExtensions(JvmPropertyExtensionVisitor.TYPE) as JvmPropertyExtensionVisitor).run {
            visit(null, getterSignature, null)
            visitSyntheticMethodForAnnotations(null)
            visitEnd()
        }
        visitEnd()
    }
}


internal
typealias KmTypeBuilder = KmTypeVisitor.() -> Unit


internal
fun jvmGetterSignatureFor(propertyName: String, desc: String): JvmMethodSignature =
    JvmMethodSignature("get${propertyName.capitalize()}", desc)


internal
val readOnlyPropertyFlags = flagsOf(
    Flag.IS_PUBLIC,
    Flag.Property.HAS_GETTER,
    Flag.Property.IS_DECLARATION
)


internal
val inlineGetterFlags = flagsOf(
    Flag.IS_PUBLIC,
    Flag.PropertyAccessor.IS_NOT_DEFAULT,
    Flag.PropertyAccessor.IS_INLINE
)
