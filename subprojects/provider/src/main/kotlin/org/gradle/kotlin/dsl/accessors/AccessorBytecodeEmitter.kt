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

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.reflect.TypeOf
import org.gradle.api.tasks.TaskProvider

import org.gradle.internal.hash.HashUtil

import org.gradle.kotlin.dsl.concurrent.WriterThread
import org.gradle.kotlin.dsl.concurrent.unorderedParallelMap
import org.gradle.kotlin.dsl.support.bytecode.ACONST_NULL
import org.gradle.kotlin.dsl.support.bytecode.ALOAD
import org.gradle.kotlin.dsl.support.bytecode.ARETURN
import org.gradle.kotlin.dsl.support.bytecode.CHECKCAST
import org.gradle.kotlin.dsl.support.bytecode.INVOKEINTERFACE
import org.gradle.kotlin.dsl.support.bytecode.INVOKESTATIC
import org.gradle.kotlin.dsl.support.bytecode.InternalName
import org.gradle.kotlin.dsl.support.bytecode.InternalNameOf
import org.gradle.kotlin.dsl.support.bytecode.KmTypeBuilder
import org.gradle.kotlin.dsl.support.bytecode.LDC
import org.gradle.kotlin.dsl.support.bytecode.RETURN
import org.gradle.kotlin.dsl.support.bytecode.actionTypeOf
import org.gradle.kotlin.dsl.support.bytecode.inlineFunctionFlags
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.method
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.publicKotlinClass
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.visitOptionalParameter
import org.gradle.kotlin.dsl.support.bytecode.visitParameter
import org.gradle.kotlin.dsl.support.bytecode.visitSignature
import org.gradle.kotlin.dsl.support.bytecode.with
import org.gradle.kotlin.dsl.support.bytecode.writeFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.writeFunctionOf
import org.gradle.kotlin.dsl.support.bytecode.writePropertyOf

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

import java.io.File


internal
object AccessorBytecodeEmitter {

    fun emitAccessorsFor(
        projectSchema: ProjectSchema<TypeAccessibility>,
        srcDir: File,
        binDir: File
    ): List<InternalName> = WriterThread().use { writer ->

        val internalClassNames = accessorsFor(projectSchema).unorderedParallelMap { accessor ->

            val (internalClassName, classBytes) =
                when (accessor) {
                    is Accessor.ForConfiguration -> emitAccessorsForConfiguration(accessor)
                    is Accessor.ForExtension -> emitAccessorForExtension(accessor)
                    is Accessor.ForContainerElement -> emitAccessorForContainerElement(accessor)
                    is Accessor.ForTask -> emitAccessorForTask(accessor)
                    is Accessor.ForConvention -> emitAccessorForConvention(accessor)
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
    fun emitAccessorForConvention(accessor: Accessor.ForConvention): Pair<InternalName, ByteArray> {

        val accessorSpec = accessor.spec
        val className = internalNameForAccessorClassOf(accessorSpec)
        val (accessibleReceiverType, name, returnType) = accessorSpec
        val receiverType = accessibleReceiverType.type.builder
        val propertyName = name.kotlinIdentifier
        val receiverTypeName = accessibleReceiverType.internalName()
        val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)
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
                receiverType = receiverType,
                returnType = kotlinReturnType,
                propertyName = propertyName,
                getterSignature = getterSignature
            )
            writeFunctionOf(
                receiverType = receiverType,
                returnType = KotlinType.unit,
                parameters = {
                    visitParameter("configure", actionTypeOf(kotlinReturnType))
                },
                name = propertyName,
                signature = configureSignature
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {
                publicStaticMethod(getterSignature) {
                    loadConventionOf(name, returnType, jvmReturnType)
                    ARETURN()
                }
                publicStaticMethod(configureSignature) {
                    ALOAD(1)
                    loadConventionOf(name, returnType, jvmReturnType)
                    invokeAction()
                    RETURN()
                }
            }

        return className to classBytes
    }

    private
    fun MethodVisitor.invokeAction() {
        INVOKEINTERFACE(Action::class.internalName, "execute", "(Ljava/lang/Object;)V")
    }

    private
    fun MethodVisitor.loadConventionOf(name: AccessorNameSpec, returnType: TypeAccessibility, jvmReturnType: InternalName) {
        ALOAD(0)
        LDC(name.original)
        invokeRuntime(
            "conventionPluginOf",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
        )
        if (returnType is TypeAccessibility.Accessible)
            CHECKCAST(jvmReturnType)
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

        // val $receiverType.$name: $providerType<$returnType>
        //   get() = named("$name", $returnType::class.java)

        val className = internalNameForAccessorClassOf(accessorSpec)
        val (accessibleReceiverType, name, returnType) = accessorSpec
        val propertyName = name.kotlinIdentifier
        val receiverType = accessibleReceiverType.type.builder
        val receiverTypeName = accessibleReceiverType.internalName()
        val getterSignature = jvmGetterSignatureFor(
            propertyName,
            accessorDescriptorFor(receiverTypeName, providerType)
        )
        val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)

        val header = writeFileFacadeClassHeader {
            writeElementAccessorMetadataFor(
                receiverType,
                providerType,
                kotlinReturnType,
                propertyName,
                getterSignature
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {
                publicStaticMethod(getterSignature) {
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
        val (accessibleReceiverType, name, returnType) = accessorSpec
        val propertyName = name.kotlinIdentifier
        val receiverType = accessibleReceiverType.type.builder
        val receiverTypeName = accessibleReceiverType.internalName()
        val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)
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
                receiverType = receiverType,
                returnType = kotlinReturnType,
                propertyName = propertyName,
                getterSignature = getterSignature
            )
            writeFunctionOf(
                receiverType = receiverType,
                returnType = KotlinType.unit,
                parameters = {
                    visitParameter("configure", actionTypeOf(kotlinReturnType))
                },
                name = propertyName,
                signature = configureSignature
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {
                publicStaticMethod(getterSignature) {
                    ALOAD(0)
                    LDC(name.original)
                    invokeRuntime(
                        "extensionOf",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
                    )
                    if (returnType is TypeAccessibility.Accessible)
                        CHECKCAST(jvmReturnType)
                    ARETURN()
                }
                publicStaticMethod(configureSignature) {
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
    fun accessibleTypesFor(typeAccessibility: TypeAccessibility): Pair<KmTypeBuilder, InternalName> = when (typeAccessibility) {
        is TypeAccessibility.Accessible -> typeAccessibility.run { type.builder to internalName() }
        is TypeAccessibility.Inaccessible -> KotlinType.any to InternalNameOf.Object
    }

    private
    val SchemaType.builder: KmTypeBuilder
        get() = value.builder

    private
    val TypeOf<*>.builder: KmTypeBuilder
        get() = when {
            isParameterized -> genericTypeOf(
                classOf(parameterizedTypeDefinition.concreteClass),
                actualTypeArguments[0].builder
            )
            else -> classOf(concreteClass)
        }

    private
    fun internalNameForAccessorClassOf(accessorSpec: TypedAccessorSpec): InternalName =
        InternalName("org/gradle/kotlin/dsl/Accessors${hashOf(accessorSpec)}Kt")

    private
    fun emitAccessorsForConfiguration(accessor: Accessor.ForConfiguration): Pair<InternalName, ByteArray> {

        val propertyName = accessor.name.original
        val className = InternalName("org/gradle/kotlin/dsl/${propertyName.capitalize()}ConfigurationAccessorsKt")

        val overload1 = JvmMethodSignature(
            propertyName,
            "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;"
        )
        val overload2 = JvmMethodSignature(
            propertyName,
            "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/String;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/ExternalModuleDependency;"
        )
        val overload3 = JvmMethodSignature(
            propertyName,
            "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lorg/gradle/api/artifacts/ExternalModuleDependency;"
        )
        val overload3Defaults = JvmMethodSignature(
            "$propertyName\$default",
            "(" +
                "Lorg/gradle/api/artifacts/dsl/DependencyHandler;" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "Ljava/lang/String;" +
                "ILjava/lang/Object;" +
                ")Lorg/gradle/api/artifacts/ExternalModuleDependency;"
        )
        val genericOverload = JvmMethodSignature(
            propertyName,
            "(" +
                "Lorg/gradle/api/artifacts/dsl/DependencyHandler;" +
                "Lorg/gradle/api/artifacts/Dependency;" +
                "Lorg/gradle/api/Action;" +
                ")Lorg/gradle/api/artifacts/Dependency;"
        )

        val constraintHandlerOverload1 = JvmMethodSignature(
            propertyName,
            "(Lorg/gradle/api/artifacts/dsl/DependencyConstraintHandler;Ljava/lang/Object;)Lorg/gradle/api/artifacts/DependencyConstraint;"
        )
        val constraintHandlerOverload2 = JvmMethodSignature(
            propertyName,
            "(Lorg/gradle/api/artifacts/dsl/DependencyConstraintHandler;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/DependencyConstraint;"
        )

        val header = writeFileFacadeClassHeader {

            writeFunctionOf(
                receiverType = GradleType.dependencyHandler,
                nullableReturnType = GradleType.dependency,
                name = propertyName,
                parameterName = "dependencyNotation",
                parameterType = KotlinType.any,
                signature = overload1
            )

            writeFunctionOf(
                receiverType = GradleType.dependencyHandler,
                returnType = GradleType.externalModuleDependency,
                name = propertyName,
                parameters = {
                    visitParameter("dependencyNotation", KotlinType.string)
                    visitParameter("configurationAction", actionTypeOf(GradleType.externalModuleDependency))
                },
                signature = overload2
            )

            writeFunctionOf(
                receiverType = GradleType.dependencyHandler,
                returnType = GradleType.externalModuleDependency,
                name = propertyName,
                parameters = {
                    visitParameter("group", KotlinType.string)
                    visitParameter("name", KotlinType.string)
                    visitOptionalParameter("version", KotlinType.string)
                    visitOptionalParameter("configuration", KotlinType.string)
                    visitOptionalParameter("classifier", KotlinType.string)
                    visitOptionalParameter("ext", KotlinType.string)
                },
                signature = overload3
            )

            val typeParameter: KmTypeBuilder = { visitTypeParameter(0) }
            visitFunction(inlineFunctionFlags, propertyName)!!.run {
                visitTypeParameter(0, "T", 0, KmVariance.INVARIANT)!!.run {
                    visitUpperBound(0).with(GradleType.dependency)
                    visitEnd()
                }
                visitReceiverParameterType(0).with(GradleType.dependencyHandler)
                visitParameter("dependency", typeParameter)
                visitParameter("action", actionTypeOf(typeParameter))
                visitReturnType(0).with(typeParameter)
                visitSignature(genericOverload)
                visitEnd()
            }

            writeFunctionOf(
                receiverType = GradleType.dependencyConstraintHandler,
                nullableReturnType = GradleType.dependencyConstraint,
                name = propertyName,
                parameterName = "constraintNotation",
                parameterType = KotlinType.any,
                signature = constraintHandlerOverload1
            )

            writeFunctionOf(
                receiverType = GradleType.dependencyConstraintHandler,
                returnType = GradleType.dependencyConstraint,
                name = propertyName,
                parameters = {
                    visitParameter("constraintNotation", KotlinType.any)
                    visitParameter("configurationAction", actionTypeOf(GradleType.dependencyConstraint))
                },
                signature = constraintHandlerOverload2
            )
        }

        val classBytes =
            publicKotlinClass(className, header) {

                publicStaticMethod(overload1) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    INVOKEINTERFACE(DependencyHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
                    ARETURN()
                }

                publicStaticMethod(overload2) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    invokeRuntime("addDependencyTo",
                        "(L${DependencyHandler::class.internalName};Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/Dependency;"
                    )
                    CHECKCAST(ExternalModuleDependency::class.internalName)
                    ARETURN()
                }

                publicStaticMethod(overload3) {
                    ACONST_NULL()
                    ARETURN()
                }

                method(
                    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
                    overload3Defaults.name,
                    overload3Defaults.desc) {
                    ACONST_NULL()
                    ARETURN()
                }

                publicStaticMethod(genericOverload) {
                    ALOAD(2)
                    ALOAD(1)
                    invokeAction()
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    INVOKEINTERFACE(DependencyHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
                    ARETURN()
                }

                publicStaticMethod(constraintHandlerOverload1) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    INVOKEINTERFACE(DependencyConstraintHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/DependencyConstraint;")
                    ARETURN()
                }

                publicStaticMethod(constraintHandlerOverload2) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    INVOKEINTERFACE(DependencyConstraintHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/DependencyConstraint;")
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
    fun hashOf(accessorSpec: TypedAccessorSpec) =
        HashUtil.createCompactMD5(accessorSpec.toString())

    private
    fun TypeAccessibility.Accessible.internalName() =
        type.value.concreteClass.internalName

    private
    fun ClassWriter.emitContainerElementAccessorFor(
        elementName: String,
        signature: JvmMethodSignature
    ) {
        publicStaticMethod(signature) {
            ALOAD(0)
            LDC(elementName)
            INVOKEINTERFACE(namedDomainObjectContainerTypeName, "named", namedMethodDescriptor)
            ARETURN()
        }
    }

    private
    fun KotlinClassMetadata.FileFacade.Writer.writeConfigurationAccessorMetadataFor(
        configurationName: String,
        getterSignature: JvmMethodSignature
    ) {
        writePropertyOf(
            receiverType = GradleType.containerOfConfiguration,
            returnType = GradleType.providerOfConfiguration,
            propertyName = configurationName,
            getterSignature = getterSignature
        )
    }

    private
    fun KotlinClassMetadata.FileFacade.Writer.writeElementAccessorMetadataFor(
        containerType: KmTypeBuilder,
        providerType: InternalName,
        elementType: KmTypeBuilder,
        propertyName: String,
        getterSignature: JvmMethodSignature
    ) {
        writePropertyOf(
            receiverType = containerType,
            returnType = genericTypeOf(classOf(providerType), elementType),
            propertyName = propertyName,
            getterSignature = getterSignature
        )
    }

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
    val namedDomainObjectContainerTypeName = NamedDomainObjectContainer::class.internalName

    private
    val configurationAccessorMethodSignature = accessorDescriptorFor(namedDomainObjectContainerTypeName, namedDomainObjectProviderTypeName)

    private
    fun accessorDescriptorFor(receiverType: InternalName, returnType: InternalName) =
        "(L$receiverType;)L$returnType;"
}


private
object GradleType {

    val dependencyConstraintHandler = classOf<DependencyConstraintHandler>()

    val dependencyConstraint = classOf<DependencyConstraint>()

    val dependencyHandler = classOf<DependencyHandler>()

    val dependency = classOf<Dependency>()

    val externalModuleDependency = classOf<ExternalModuleDependency>()

    val namedDomainObjectContainer = classOf<NamedDomainObjectContainer<*>>()

    val configuration = classOf<Configuration>()

    val namedDomainObjectProvider = classOf<NamedDomainObjectProvider<*>>()

    val containerOfConfiguration = genericTypeOf(namedDomainObjectContainer, configuration)

    val providerOfConfiguration = genericTypeOf(namedDomainObjectProvider, configuration)
}


private
object KotlinType {

    val string: KmTypeBuilder = { visitClass("kotlin/String") }

    val unit: KmTypeBuilder = { visitClass("kotlin/Unit") }

    val any: KmTypeBuilder = { visitClass("kotlin/Any") }
}


internal
sealed class Accessor {

    data class ForConfiguration(val name: AccessorNameSpec) : Accessor()

    data class ForExtension(val spec: TypedAccessorSpec) : Accessor()

    data class ForConvention(val spec: TypedAccessorSpec) : Accessor()

    data class ForContainerElement(val spec: TypedAccessorSpec) : Accessor()

    data class ForTask(val spec: TypedAccessorSpec) : Accessor()
}


internal
fun accessorsFor(schema: ProjectSchema<TypeAccessibility>): Sequence<Accessor> = sequence {
    schema.run {
        AccessorScope().run {
            yieldAll(uniqueAccessorsFor(extensions).map(Accessor::ForExtension))
            yieldAll(uniqueAccessorsFor(conventions).map(Accessor::ForConvention))
            yieldAll(uniqueAccessorsFor(tasks).map(Accessor::ForTask))
            yieldAll(uniqueAccessorsFor(containerElements).map(Accessor::ForContainerElement))

            val configurationNames = configurations.map(::AccessorNameSpec).asSequence()
            yieldAll(
                uniqueAccessorsFrom(configurationNames.map(::configurationAccessorSpec)).map(Accessor::ForContainerElement)
            )
            yieldAll(configurationNames.map(Accessor::ForConfiguration))
        }
    }
}


private
fun configurationAccessorSpec(nameSpec: AccessorNameSpec) =
    TypedAccessorSpec(
        accessibleType<NamedDomainObjectContainer<Configuration>>(),
        nameSpec,
        accessibleType<Configuration>()
    )


private
inline fun <reified T> accessibleType() =
    TypeAccessibility.Accessible(SchemaType.of<T>())


private
fun ClassVisitor.publicStaticMethod(
    jvmMethodSignature: JvmMethodSignature,
    signature: String? = null,
    exceptions: Array<String>? = null,
    methodBody: MethodVisitor.() -> Unit
) = jvmMethodSignature.run {
    publicStaticMethod(name, desc, signature, exceptions, methodBody)
}


private
inline fun <reified T> classOf(): KmTypeBuilder =
    classOf(T::class.java)


private
fun classOf(`class`: Class<*>) =
    classOf(`class`.internalName)


private
fun classOf(className: InternalName): KmTypeBuilder =
    className.value.replace('$', '.').let { kotlinName ->
        { visitClass(kotlinName) }
    }


private
fun genericTypeOf(genericType: KmTypeBuilder, genericArgument: KmTypeBuilder): KmTypeBuilder = {
    genericType()
    visitArgument(0, KmVariance.INVARIANT)!!.run {
        genericArgument()
        visitEnd()
    }
}
