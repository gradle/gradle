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

import org.gradle.kotlin.dsl.concurrent.WriterThread

import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
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

import java.io.File

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

            val header = writeFileFacadeClassHeader {
                writeConfigurationAccessorMetadataFor(accessor.configurationName, getterSignature)
            }

            val internalClassName =
                "org/gradle/kotlin/dsl/${accessor.configurationName.capitalize()}ConfigurationAccessorsKt"

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
                writeConfigurationAccessorMetadataFor(accessor.configurationName, getterSignature)
            }
        }

        val className = "org/gradle/kotlin/dsl/ConfigurationAccessorsKt"
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
        emitContainerElementAccessorFor(configurationContainerInternalName, accessor.configurationName, signature)
    }

    private
    fun ClassWriter.emitContainerElementAccessorFor(
        containerTypeName: String,
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
    fun writeModuleMetadataFor(className: String, outputDir: File) {
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
        jvmGetterSignatureFor(accessor.configurationName, configurationAccessorMethodSignature)

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
}


internal
sealed class Accessor {

    data class ForConfiguration(val configurationName: String) : Accessor()
}


internal
fun accessorsForConfigurationsOf(projectSchema: ProjectSchema<String>) =
    projectSchema.configurations.asSequence().map { Accessor.ForConfiguration(it) }
