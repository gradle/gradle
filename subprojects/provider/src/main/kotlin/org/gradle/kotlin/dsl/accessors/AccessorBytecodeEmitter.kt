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
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.TaskProvider

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
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.actionTypeOf
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.visitParameter
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.writeFunctionOf
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
                    is Accessor.ForConfiguration -> emitAccessorsForConfiguration(accessor)
                    is Accessor.ForExtension -> emitAccessorForExtension(accessor)
                    is Accessor.ForContainerElement -> emitAccessorForContainerElement(accessor)
                    is Accessor.ForTask -> emitAccessorForTask(accessor)
//                    is Accessor.ForConvention -> TODO()
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
    fun emitAccessorForTask(accessor: Accessor.ForTask): Pair<InternalName, ByteArray> =
        emitContainerElementAccessorFor(
            accessor.spec,
            taskProviderTypeName,
            namedTaskWithTypeMethodDescriptor
        )

    private
    fun emitAccessorForContainerElement(accessor: Accessor.ForContainerElement): Pair<InternalName, ByteArray> =
        emitContainerElementAccessorFor(
            accessor.spec,
            namedDomainObjectProviderTypeName,
            namedWithTypeMethodDescriptor
        )

    private
    fun emitContainerElementAccessorFor(
        accessorSpec: TypedAccessorSpec,
        providerType: InternalName,
        namedMethodDescriptor: String
    ): Pair<InternalName, ByteArray> {

        // val $targetType.$name: $providerType<$returnType>
        //   get() = named("$name", $returnType::class.java)

        val className = internalNameForAccessorClassOf(accessorSpec)
        val (targetType, name, returnType) = accessorSpec
        val propertyName = name.kotlinIdentifier
        val receiverTypeName = internalNameFromSourceName(targetType.type)
        val (kotlinReturnType, jvmReturnType) = accessibleReturnTypeFor(returnType)
        val getterSignature = jvmGetterSignatureFor(
            propertyName,
            accessorDescriptorFor(receiverTypeName, providerType)
        )

        val header = writeFileFacadeClassHeader {
            writeElementAccessorMetadataFor(
                receiverTypeName,
                providerType,
                kotlinReturnType,
                propertyName,
                getterSignature
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {
                publicStaticMethod(getterSignature.name, getterSignature.desc) {
                    ALOAD(0)
                    LDC(propertyName)
                    LDC(jvmReturnType)
                    INVOKEINTERFACE(receiverTypeName, "named", namedMethodDescriptor)
                    ARETURN()
                }
            }

        return className to classBytes
    }

    private
    fun emitAccessorForExtension(accessor: Accessor.ForExtension): Pair<InternalName, ByteArray> {

        val accessorSpec = accessor.spec
        val className = internalNameForAccessorClassOf(accessorSpec)
        val (targetType, name, returnType) = accessorSpec
        val propertyName = name.kotlinIdentifier
        val receiverTypeName = internalNameFromSourceName(targetType.type)
        val (kotlinReturnType, jvmReturnType) = accessibleReturnTypeFor(returnType)

        val getterSignature = jvmGetterSignatureFor(
            propertyName,
            accessorDescriptorFor(receiverTypeName, jvmReturnType)
        )
        val configureSignature = JvmMethodSignature(
            propertyName,
            "(L$receiverTypeName;Lorg/gradle/api/Action;)V"
        )

        val header = writeFileFacadeClassHeader {
            writePropertyOf(
                receiverType = { visitClass(receiverTypeName) },
                returnType = { visitClass(kotlinReturnType) },
                propertyName = propertyName,
                getterSignature = getterSignature
            )
            writeFunctionOf(
                receiverType = { visitClass(receiverTypeName) },
                returnType = { visitClass(InternalNameOf.Unit) },
                parameters = {
                    visitParameter("configure", actionTypeOf {
                        visitClass(kotlinReturnType)
                    })
                },
                name = propertyName,
                signature = configureSignature
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {
                publicStaticMethod(getterSignature.name, getterSignature.desc) {
                    ALOAD(0)
                    LDC(name.original)
                    invokeRuntime(
                        "extensionOf",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
                    )
                    when (returnType) {
                        is TypeAccessibility.Accessible -> CHECKCAST(jvmReturnType)
                        else -> {}
                    }
                    ARETURN()
                }
                publicStaticMethod(configureSignature.name, configureSignature.desc) {
                    ALOAD(0)
                    CHECKCAST(ExtensionAware::class.internalName)
                    INVOKEINTERFACE(ExtensionAware::class.internalName, "getExtensions", "()Lorg/gradle/api/plugins/ExtensionContainer;")
                    LDC(name.original)
                    ALOAD(1)
                    INVOKEINTERFACE(ExtensionContainer::class.internalName, "configure", "(Ljava/lang/String;Lorg/gradle/api/Action;)V")
                    RETURN()
                }
            }

        return className to classBytes
    }

    private
    fun internalNameForAccessorClassOf(accessorSpec: TypedAccessorSpec): InternalName =
        InternalName("org/gradle/kotlin/dsl/Accessors${hashOf(accessorSpec)}Kt")

    private
    fun emitAccessorsForConfiguration(accessor: Accessor.ForConfiguration): Pair<InternalName, ByteArray> {

        val propertyName = accessor.name
        val className = InternalName("org/gradle/kotlin/dsl/${propertyName.capitalize()}ConfigurationAccessorsKt")

        val getterSignature = jvmGetterSignatureFor(accessor.name, configurationAccessorMethodSignature)
        val overload1 = JvmMethodSignature(propertyName, "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")

        val header = writeFileFacadeClassHeader {
            writeConfigurationAccessorMetadataFor(propertyName, getterSignature)
            writeFunctionOf(
                receiverType = { visitClass(DependencyHandler::class.internalName) },
                nullableReturnType = { visitClass(Dependency::class.internalName) },
                name = propertyName,
                parameterName = "dependencyNotation",
                parameterType = { visitClass(InternalNameOf.Any) },
                signature = overload1
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {

                emitConfigurationAccessorFor(propertyName, getterSignature)

                publicStaticMethod(overload1.name, overload1.desc) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    INVOKEINTERFACE(DependencyHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
                    ARETURN()
                }
            }

        return className to classBytes
    }

    private
    fun MethodVisitor.invokeRuntime(function: String, desc: String) {
        INVOKESTATIC(InternalName("org/gradle/kotlin/dsl/accessors/runtime/RuntimeKt"), function, desc)
    }

    private
    fun internalNameFromSourceName(type: String) =
        InternalName(type.replace('.', '/'))

    private
    fun hashOf(accessorSpec: TypedAccessorSpec) =
        HashUtil.createCompactMD5(accessorSpec.toString())

    private
    fun accessibleReturnTypeFor(returnType: TypeAccessibility): Pair<InternalName, InternalName> =
        when (returnType) {
            is TypeAccessibility.Accessible -> internalNameFromSourceName(returnType.type).let { it to it }
            is TypeAccessibility.Inaccessible -> InternalNameOf.Any to InternalNameOf.Object
        }

    private
    fun ClassWriter.emitConfigurationAccessorFor(name: String, signature: JvmMethodSignature) {
        emitContainerElementAccessorFor(configurationContainerInternalName, name, signature)
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
    fun KotlinClassMetadata.FileFacade.Writer.writeConfigurationAccessorMetadataFor(
        configurationName: String,
        getterSignature: JvmMethodSignature
    ) {
        writeElementAccessorMetadataFor(
            configurationContainerInternalName,
            namedDomainObjectProviderTypeName,
            configurationInternalName,
            configurationName,
            getterSignature
        )
    }

    private
    fun KotlinClassMetadata.FileFacade.Writer.writeElementAccessorMetadataFor(
        containerType: InternalName,
        providerType: InternalName,
        elementType: InternalName,
        propertyName: String,
        getterSignature: JvmMethodSignature
    ) {
        writePropertyOf(
            receiverType = {
                visitClass(containerType)
            },
            returnType = {
                visitClass(providerType)
                visitArgument(0, KmVariance.INVARIANT)!!.run {
                    visitClass(elementType)
                    visitEnd()
                }
            },
            propertyName = propertyName,
            getterSignature = getterSignature
        )
    }

    private
    val configurationContainerInternalName = ConfigurationContainer::class.internalName

    private
    val configurationInternalName = Configuration::class.internalName

    private
    val namedDomainObjectProviderTypeName = NamedDomainObjectProvider::class.internalName

    private
    val namedMethodDescriptor = "(Ljava/lang/String;)L$namedDomainObjectProviderTypeName;"

    private
    val namedWithTypeMethodDescriptor = "(Ljava/lang/String;Ljava/lang/Class;)L$namedDomainObjectProviderTypeName;"

    private
    val taskProviderTypeName = TaskProvider::class.internalName

    private
    val namedTaskWithTypeMethodDescriptor = "(Ljava/lang/String;Ljava/lang/Class;)L$taskProviderTypeName;"

    private
    val configurationAccessorMethodSignature = accessorDescriptorFor(configurationContainerInternalName, namedDomainObjectProviderTypeName)

    private
    fun accessorDescriptorFor(receiverType: InternalName, returnType: InternalName) =
        "(L$receiverType;)L$returnType;"
}


internal
sealed class Accessor {

    data class ForConfiguration(val name: String) : Accessor()

    data class ForExtension(val spec: TypedAccessorSpec) : Accessor()

//    data class ForConvention(val spec: TypedAccessorSpec) : Accessor()

    data class ForContainerElement(val spec: TypedAccessorSpec) : Accessor()

    data class ForTask(val spec: TypedAccessorSpec) : Accessor()
}


internal
fun accessorsFor(schema: ProjectSchema<TypeAccessibility>): Sequence<Accessor> = sequence {
    schema.run {
        AccessorScope().run {
            yieldAll(uniqueAccessorsFor(extensions).map(Accessor::ForExtension))
//            yieldAll(uniqueAccessorsFor(conventions).map(Accessor::ForConvention))
            yieldAll(uniqueAccessorsFor(tasks).map(Accessor::ForTask))
            yieldAll(uniqueAccessorsFor(containerElements).map(Accessor::ForContainerElement))
            yieldAll(accessorsForConfigurationsOf(schema))
        }
    }
}


internal
fun accessorsForConfigurationsOf(projectSchema: ProjectSchema<*>) =
    projectSchema.configurations.asSequence().map { Accessor.ForConfiguration(it) }
