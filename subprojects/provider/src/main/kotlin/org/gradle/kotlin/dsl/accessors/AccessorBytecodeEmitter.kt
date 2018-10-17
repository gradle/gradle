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

import kotlinx.metadata.KmVariance
import kotlinx.metadata.jvm.JvmMethodSignature
import kotlinx.metadata.jvm.KotlinClassMetadata

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer

import org.gradle.internal.hash.HashUtil

import org.gradle.kotlin.dsl.concurrent.WriterThread
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.CHECKCAST
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESTATIC
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.InternalNameOf
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.writePropertyOf

import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor

import java.io.File

import kotlin.streams.asStream
import kotlin.streams.toList


internal
object AccessorBytecodeEmitter {

    fun emitAccessorsFor(
        projectSchema: ProjectSchema<TypeAccessibility>,
        srcDir: File,
        binDir: File
    ): List<InternalName> = WriterThread().use { writer ->

        // TODO: honor Gradle max workers?
        // TODO: make it observable via build operations
        val internalClassNames = accessorsFor(projectSchema).asStream().unordered().parallel().map { accessor ->

            val (internalClassName, classBytes) =
                when (accessor) {
                    is Accessor.ForConfiguration -> emitAccessorForConfiguration(accessor)
                    is Accessor.ForExtension -> emitAccessorForExtension(accessor)
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

    private
    fun emitAccessorForExtension(accessor: Accessor.ForExtension): Pair<InternalName, ByteArray> {

        val (targetType, name, returnType) = accessor.spec

        val internalClassName = InternalName("org/gradle/kotlin/dsl/ExtensionAccessors${hashOf(accessor)}Kt")

        val accessorName = name.kotlinIdentifier
        val receiverTypeName = internalNameFromSourceName(targetType.type)
        val returnTypeName = accessibleReturnTypeFor(returnType)

        val signature = jvmGetterSignatureFor(
            accessorName,
            accessorDescriptorFor(receiverTypeName, returnTypeName)
        )

        val header = writeFileFacadeClassHeader {
            writePropertyOf(
                receiverType = { visitClass(receiverTypeName) },
                returnType = { visitClass(returnTypeName) },
                propertyName = accessorName,
                getterSignature = signature
            )
        }

        val classBytes =
            publicKotlinClass(internalClassName, header) {
                publicStaticMethod(signature.name, signature.desc) {
                    ALOAD(0)
                    LDC(name.original)
                    invokeRuntime(
                        "extensionOf",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
                    )
                    when (returnType) {
                        is TypeAccessibility.Accessible -> CHECKCAST(returnTypeName)
                        else -> {}
                    }
                    ARETURN()
                }
            }

        return internalClassName to classBytes
    }

    private
    fun MethodVisitor.invokeRuntime(function: String, desc: String) {
        INVOKESTATIC(InternalName("org/gradle/kotlin/dsl/accessors/runtime/RuntimeKt"), function, desc)
    }

    private
    fun internalNameFromSourceName(type: String) =
        InternalName(type.replace('.', '/'))

    private
    fun hashOf(accessor: Accessor.ForExtension) =
        HashUtil.createCompactMD5(accessor.spec.toString())

    private
    fun accessibleReturnTypeFor(returnType: TypeAccessibility): InternalName =
        when (returnType) {
            is TypeAccessibility.Accessible -> internalNameFromSourceName(returnType.type)
            is TypeAccessibility.Inaccessible -> InternalNameOf.Object
        }

    private
    fun emitAccessorForConfiguration(accessor: Accessor.ForConfiguration): Pair<InternalName, ByteArray> {

        val internalClassName =
            InternalName("org/gradle/kotlin/dsl/${accessor.name.capitalize()}ConfigurationAccessorsKt")

        val getterSignature = jvmMethodSignatureFor(accessor)

        val header = writeFileFacadeClassHeader {
            writeConfigurationAccessorMetadataFor(accessor.name, getterSignature)
        }

        val classBytes =
            publicKotlinClass(internalClassName, header) {

                emitConfigurationAccessorFor(accessor, getterSignature)
            }

        return internalClassName to classBytes
    }

    fun emitExtensionsWithOneClassPerConfiguration(
        projectSchema: ProjectSchema<*>,
        srcDir: File,
        binDir: File
    ): List<InternalName> = WriterThread().use { writer ->

        // TODO: honor Gradle max workers?
        // TODO: make it observable via build operations
        val internalClassNames = accessorsForConfigurationsOf(projectSchema).asStream().unordered().parallel().map { accessor ->

            val getterSignature = jvmMethodSignatureFor(accessor)

            val header = writeFileFacadeClassHeader {
                writeConfigurationAccessorMetadataFor(accessor.name, getterSignature)
            }

            val internalClassName =
                InternalName("org/gradle/kotlin/dsl/${accessor.name.capitalize()}ConfigurationAccessorsKt")

            val classBytes =
                publicKotlinClass(internalClassName, header) {
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

        val header = writeFileFacadeClassHeader {
            for ((accessor, getterSignature) in accessorGetterSignaturePairs) {
                writeConfigurationAccessorMetadataFor(accessor.name, getterSignature)
            }
        }

        val className = InternalName("org/gradle/kotlin/dsl/ConfigurationAccessorsKt")
        val classBytes =
            publicKotlinClass(className, header) {
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
        emitContainerElementAccessorFor(configurationContainerInternalName, accessor.name, signature)
    }

    private
    fun ClassWriter.emitContainerElementAccessorFor(
        containerTypeName: InternalName,
        elementName: String,
        signature: JvmMethodSignature
    ) {
        publicStaticMethod(signature.name, signature.desc) {
            ALOAD(0)
            LDC(elementName)
            INVOKEINTERFACE(containerTypeName, "named", namedMethodDescriptor)
            ARETURN()
        }
    }

    private
    fun writeModuleMetadataFor(className: InternalName, outputDir: File) {
        moduleFileFor(outputDir).run {
            parentFile.mkdir()
            writeBytes(moduleMetadataBytesFor(listOf(className)))
        }
    }

    private
    fun KotlinClassMetadata.FileFacade.Writer.writeConfigurationAccessorMetadataFor(
        configurationName: String,
        getterSignature: JvmMethodSignature
    ) {
        writePropertyOf(
            receiverType = {
                visitClass(configurationContainerInternalName)
            },
            returnType = {
                visitClass(namedDomainObjectProviderInternalName)
                visitArgument(0, KmVariance.INVARIANT)!!.run {
                    visitClass(configurationInternalName)
                    visitEnd()
                }
            },
            propertyName = configurationName,
            getterSignature = getterSignature
        )
    }

    private
    fun jvmMethodSignatureFor(accessor: Accessor.ForConfiguration): JvmMethodSignature =
        jvmGetterSignatureFor(accessor.name, configurationAccessorMethodSignature)

    private
    val configurationContainerInternalName = ConfigurationContainer::class.internalName

    private
    val configurationInternalName = Configuration::class.internalName

    private
    val namedDomainObjectProviderInternalName = NamedDomainObjectProvider::class.internalName

    private
    val namedMethodDescriptor = "(Ljava/lang/String;)L$namedDomainObjectProviderInternalName;"

    private
    val configurationAccessorMethodSignature = accessorDescriptorFor(configurationContainerInternalName, namedDomainObjectProviderInternalName)

    private
    fun accessorDescriptorFor(receiverType: InternalName, returnType: InternalName) =
        "(L$receiverType;)L$returnType;"
}


internal
sealed class Accessor {

    data class ForConfiguration(val name: String) : Accessor()

    data class ForExtension(val spec: TypedAccessorSpec) : Accessor()
}


internal
fun accessorsFor(projectSchema: ProjectSchema<TypeAccessibility>): Sequence<Accessor> = projectSchema.run {
    (configurations.asSequence().map { Accessor.ForConfiguration(it) }
        + extensions.asSequence().mapNotNull(::typedAccessorSpec).map { Accessor.ForExtension(it) })
}


internal
fun accessorsForConfigurationsOf(projectSchema: ProjectSchema<*>) =
    projectSchema.configurations.asSequence().map { Accessor.ForConfiguration(it) }
