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

package org.gradle.kotlin.dsl.accessors

import kotlinx.metadata.ClassName
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmExtensionType
import kotlinx.metadata.KmPackageExtensionVisitor
import kotlinx.metadata.KmPackageVisitor
import kotlinx.metadata.KmPropertyExtensionVisitor
import kotlinx.metadata.KmPropertyVisitor
import kotlinx.metadata.KmTypeVisitor
import kotlinx.metadata.KmVariance
import kotlinx.metadata.KmVersionRequirementVisitor
import kotlinx.metadata.flagsOf
import kotlinx.metadata.jvm.JvmFieldSignature
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.JvmPackageExtensionVisitor
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor
import kotlinx.metadata.jvm.KmModuleVisitor
import kotlinx.metadata.jvm.KotlinClassHeader
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.KotlinModuleMetadata

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

import org.gradle.kotlin.dsl.execution.ALOAD
import org.gradle.kotlin.dsl.execution.ARETURN
import org.gradle.kotlin.dsl.execution.INVOKEINTERFACE
import org.gradle.kotlin.dsl.execution.LDC
import org.gradle.kotlin.dsl.execution.internalName
import org.gradle.kotlin.dsl.execution.method
import org.gradle.kotlin.dsl.execution.publicClass
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes

import java.io.File

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import kotlin.streams.asStream
import kotlin.streams.toList


internal
object AccessorBytecodeEmitter {

    fun emitExtensionsWithOneClassPerConfiguration(
        projectSchema: ProjectSchema<String>,
        srcDir: File,
        binDir: File
    ): List<String> = WriterThread().use { writer ->

        val internalClassNames = accessorsForConfigurationsOf(projectSchema).asStream().unordered().parallel().map { accessor ->

            val getterSignature = jvmMethodSignatureFor(accessor)

            val header =
                KotlinClassMetadata.FileFacade.Writer().let { writer ->
                    writer.writeMetadataFor(accessor, getterSignature)
                    finishWriterAndGetClassHeader(writer)
                }

            val internalClassName =
                "org/gradle/kotlin/dsl/${accessor.configurationName.capitalize()}ConfigurationAccessorsKt"

            val classBytes =
                publicClass(internalClassName) {
                    visitKotlinMetadataAnnotation(header)
                    emitConfigurationAccessorFor(accessor, getterSignature)
                }

            writer.writeFile(
                binDir.resolve("$internalClassName.class"),
                classBytes
            )

            internalClassName
        }.toList()

        writer.writeFile(
            moduleFileFor(binDir),
            moduleMetadataBytesFor(internalClassNames)
        )
        internalClassNames
    }

    fun emitExtensionsSingleThreaded(accessors: Sequence<Accessor>, outputDir: File) {

        val accessorGetterSignaturePairs = accessors.filterIsInstance<Accessor.ForConfiguration>().map { accessor ->
            accessor to jvmMethodSignatureFor(accessor)
        }.toList()

        val metadataWriter = KotlinClassMetadata.FileFacade.Writer()
        for ((accessor, getterSignature) in accessorGetterSignaturePairs) {
            metadataWriter.writeMetadataFor(accessor, getterSignature)
        }
        val header = finishWriterAndGetClassHeader(metadataWriter)

        val className = "org/gradle/kotlin/dsl/ConfigurationAccessorsKt"
        val classBytes =
            publicClass(className) {
                visitKotlinMetadataAnnotation(header)
                for ((accessor, getterSignature) in accessorGetterSignaturePairs) {
                    emitConfigurationAccessorFor(accessor, getterSignature)
                }
            }

        outputDir.resolve("$className.class").run {
            parentFile.mkdirs()
            writeBytes(classBytes)
        }

        writeModuleMetadataFor(className, outputDir)
    }

    private
    fun ClassWriter.emitConfigurationAccessorFor(accessor: Accessor.ForConfiguration, signature: JvmMethodSignature) {
        method(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, signature.name, signature.desc) {
            ALOAD(0)
            LDC(accessor.configurationName)
            INVOKEINTERFACE(configurationContainerInternalName, "named", namedMethodDescriptor)
            ARETURN()
        }
    }

    fun emitExtensionsMultiThreaded(accessors: Sequence<Accessor>, outputDir: File) {

        val executor = Executors.newFixedThreadPool(2)

        val headerQ = ArrayBlockingQueue<Request>(32)
        val header = executor.submit(Callable {
            emitKotlinClassHeader(headerQ)
        })

        val moduleQ = ArrayBlockingQueue<Request>(32)
        executor.submit {
            emitKotlinModule(moduleQ, header, outputDir)
        }

        accessors.filterIsInstance<Accessor.ForConfiguration>().forEach { accessor ->
            val r = Request.AccessorOf(accessor, jvmMethodSignatureFor(accessor))
            headerQ.put(r)
            moduleQ.put(r)
        }

        headerQ.put(Request.Done)
        moduleQ.put(Request.Done)

        close(executor)
    }

    private
    fun close(writer: ExecutorService) {
        writer.shutdown()
        writer.awaitTermination(1, TimeUnit.DAYS)
    }

    sealed class Request {

        data class AccessorOf(val accessor: Accessor.ForConfiguration, val signature: JvmMethodSignature) : Request()

        object Done : Request()
    }

    private
    fun emitKotlinModule(moduleQ: ArrayBlockingQueue<Request>, header: Future<KotlinClassHeader>, outputDir: File) {

        val className = "org/gradle/kotlin/dsl/ConfigurationAccessorsKt"

        // Write the module metadata
        writeModuleMetadataFor(className, outputDir)

        val classBytes =
            publicClass(className) {
                moduleQ.forEachRequest {
                    method(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, signature.name, signature.desc) {
                        ALOAD(0)
                        LDC(accessor.configurationName)
                        INVOKEINTERFACE(configurationContainerInternalName, "named", namedMethodDescriptor)
                        ARETURN()
                    }
                }
                visitKotlinMetadataAnnotation(header.get())
            }

        outputDir.resolve("$className.class").run {
            parentFile.mkdirs()
            writeBytes(classBytes)
        }
    }

    private
    fun writeModuleMetadataFor(className: String, outputDir: File) {
        moduleFileFor(outputDir).run {
            parentFile.mkdir()
            writeBytes(moduleMetadataBytesFor(listOf(className)))
        }
    }

    private
    fun moduleFileFor(outputDir: File) =
        outputDir.resolve("META-INF").resolve("${outputDir.name}.kotlin_module")

    private
    fun moduleMetadataBytesFor(fileFacades: List<String>): ByteArray =
        KotlinModuleMetadata.Writer().run {
            visitPackageParts("org.gradle.kotlin.dsl", fileFacades, emptyMap())
            visitEnd()
            write().bytes
        }

    private
    fun emitKotlinClassHeader(requests: ArrayBlockingQueue<Request>): KotlinClassHeader {
        val metadataWriter = KotlinClassMetadata.FileFacade.Writer()
        requests.forEachRequest {
            metadataWriter.writeMetadataFor(accessor, signature)
        }
        return finishWriterAndGetClassHeader(metadataWriter)
    }

    private
    fun finishWriterAndGetClassHeader(metadataWriter: KotlinClassMetadata.FileFacade.Writer): KotlinClassHeader =
        metadataWriter.run {
            (visitExtensions(JvmPackageExtensionVisitor.TYPE) as KmPackageExtensionVisitor).run {
                visitEnd()
            }
            visitEnd()
            write().header
        }

    private
    fun KotlinClassMetadata.FileFacade.Writer.writeMetadataFor(accessor: Accessor.ForConfiguration, getterSignature: JvmMethodSignature) {
        visitProperty(readOnlyPropertyFlags, accessor.configurationName, getterFlags, 6)!!.run {
            visitReceiverParameterType(0)!!.run {
                visitClass(configurationContainerInternalName)
                visitEnd()
            }
            visitReturnType(0)!!.run {
                visitClass(namedDomainObjectProviderInternalName)
                visitArgument(0, KmVariance.INVARIANT)!!.run {
                    visitClass(configurationInternalName)
                    visitEnd()
                }
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

    private
    fun jvmMethodSignatureFor(accessor: Accessor.ForConfiguration): JvmMethodSignature =
        JvmMethodSignature(
            "get${accessor.configurationName.capitalize()}",
            configurationAccessorMethodSignature
        )

    private
    val configurationContainerInternalName = ConfigurationContainer::class.internalName

    private
    val configurationInternalName = Configuration::class.internalName

    private
    val namedDomainObjectProviderInternalName = NamedDomainObjectProvider::class.internalName

    private
    val namedMethodDescriptor = "(Ljava/lang/String;)L$namedDomainObjectProviderInternalName;"

    private
    val configurationAccessorMethodSignature = "(L$configurationContainerInternalName;)L$namedDomainObjectProviderInternalName;"

    private
    val readOnlyPropertyFlags = flagsOf(
        Flag.IS_PUBLIC,
        Flag.Property.HAS_GETTER,
        Flag.Property.IS_DECLARATION
    )

    private
    val getterFlags = flagsOf(
        Flag.IS_PUBLIC,
        Flag.PropertyAccessor.IS_NOT_DEFAULT,
        Flag.PropertyAccessor.IS_INLINE
    )

    private
    fun <E> ArrayBlockingQueue<E>.forEachRequest(f: Request.AccessorOf.() -> Unit) {
        loop@ while (true) {
            when (val request = take()) {
                is Request.AccessorOf -> f(request)
                else -> break@loop
            }
        }
    }
}


internal
sealed class Accessor {

    data class ForConfiguration(val configurationName: String) : Accessor()
}


/**
 * Writes the given [header] to the class file as a [kotlin.Metadata] annotation.
 **/
private
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


object PrintingVisitor {

    object ForPackage : KmPackageVisitor() {

        override fun visitExtensions(type: KmExtensionType): KmPackageExtensionVisitor? {
            println("visitExtensions($type)")
            return object : JvmPackageExtensionVisitor() {
                override fun visitLocalDelegatedProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
                    println("visitLocalDelegatedProperty($flags, $name, $getterFlags, $setterFlags)")
                    return super.visitLocalDelegatedProperty(flags, name, getterFlags, setterFlags)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitProperty(flags: Flags, name: String, getterFlags: Flags, setterFlags: Flags): KmPropertyVisitor? {
            println("visitProperty($flags, $name, $getterFlags, $setterFlags)")
            return ForProperty
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForProperty : KmPropertyVisitor() {

        override fun visitExtensions(type: KmExtensionType): KmPropertyExtensionVisitor? {
            println("visitExtensions($type")
            return object : JvmPropertyExtensionVisitor() {
                override fun visit(fieldDesc: JvmFieldSignature?, getterDesc: JvmMethodSignature?, setterDesc: JvmMethodSignature?) {
                    println("visit($fieldDesc, $getterDesc, $setterDesc)")
                    super.visit(fieldDesc, getterDesc, setterDesc)
                }

                override fun visitSyntheticMethodForAnnotations(desc: JvmMethodSignature?) {
                    println("visitSyntheticMethodForAnnotations($desc)")
                    super.visitSyntheticMethodForAnnotations(desc)
                }

                override fun visitEnd() {
                    println("visitEnd()")
                    super.visitEnd()
                }
            }
        }

        override fun visitReceiverParameterType(flags: Flags): KmTypeVisitor? {
            println("visitReceiverParameterType($flags)")
            return ForType
        }

        override fun visitReturnType(flags: Flags): KmTypeVisitor? {
            println("visitReturnType($flags)")
            return ForType
        }

        override fun visitVersionRequirement(): KmVersionRequirementVisitor? {
            println("visitVersionRequirement()")
            return super.visitVersionRequirement()
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForType : KmTypeVisitor() {

        override fun visitClass(name: ClassName) {
            println("visitClass($name)")
            super.visitClass(name)
        }

        override fun visitArgument(flags: Flags, variance: KmVariance): KmTypeVisitor? {
            println("visitArgument($flags, $variance)")
            return ForType
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }

    object ForModule : KmModuleVisitor() {
        override fun visitAnnotation(annotation: KmAnnotation) {
            println("visitAnnotation($annotation)")
            super.visitAnnotation(annotation)
        }

        override fun visitPackageParts(fqName: String, fileFacades: List<String>, multiFileClassParts: Map<String, String>) {
            println("visitPackageParts($fqName, $fileFacades, $multiFileClassParts")
            super.visitPackageParts(fqName, fileFacades, multiFileClassParts)
        }

        override fun visitEnd() {
            println("visitEnd()")
            super.visitEnd()
        }
    }
}


internal
fun accessorsForConfigurationsOf(projectSchema: ProjectSchema<String>) =
    projectSchema.configurations.asSequence().map { Accessor.ForConfiguration(it) }
