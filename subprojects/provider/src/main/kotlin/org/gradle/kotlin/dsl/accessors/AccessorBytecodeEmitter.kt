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
import org.gradle.kotlin.dsl.support.bytecode.beginFileFacadeClassHeader
import org.gradle.kotlin.dsl.support.bytecode.beginPublicClass
import org.gradle.kotlin.dsl.support.bytecode.closeHeader
import org.gradle.kotlin.dsl.support.bytecode.endClass
import org.gradle.kotlin.dsl.support.bytecode.inlineFunctionFlags
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.method
import org.gradle.kotlin.dsl.support.bytecode.moduleFileFor
import org.gradle.kotlin.dsl.support.bytecode.moduleMetadataBytesFor
import org.gradle.kotlin.dsl.support.bytecode.nonInlineFunctionFlags
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.visitKotlinMetadataAnnotation
import org.gradle.kotlin.dsl.support.bytecode.visitOptionalParameter
import org.gradle.kotlin.dsl.support.bytecode.visitParameter
import org.gradle.kotlin.dsl.support.bytecode.visitSignature
import org.gradle.kotlin.dsl.support.bytecode.with
import org.gradle.kotlin.dsl.support.bytecode.writeFunctionOf
import org.gradle.kotlin.dsl.support.bytecode.writePropertyOf

import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

import java.io.File


private
data class AccessorFragment(
    val source: String,
    val bytecode: BytecodeWriter,
    val metadata: MetadataWriter,
    val signature: JvmMethodSignature
)


private
typealias BytecodeWriter = BytecodeFragmentScope.() -> Unit


private
class BytecodeFragmentScope(
    val signature: JvmMethodSignature,
    writer: ClassWriter
) : ClassVisitor(Opcodes.ASM6, writer)


private
typealias MetadataWriter = MetadataFragmentScope.() -> Unit


private
data class MetadataFragmentScope(
    val signature: JvmMethodSignature,
    val writer: KotlinClassMetadata.FileFacade.Writer
)


private
typealias Fragments = Pair<InternalName, Sequence<AccessorFragment>>


internal
object AccessorBytecodeEmitter {

    fun emitAccessorsFor(
        projectSchema: ProjectSchema<TypeAccessibility>,
        srcDir: File,
        binDir: File
    ): List<InternalName> = WriterThread().use { writer ->

        writer.execute {
            makeAccessorOutputDirs(srcDir, binDir)
        }

        val emittedClassNames = accessorsFor(projectSchema).unorderedParallelMap { accessor ->

            val (className, fragments) =
                when (accessor) {
                    is Accessor.ForConfiguration -> fragmentsForConfiguration(accessor)
                    is Accessor.ForExtension -> fragmentsForExtension(accessor)
                    is Accessor.ForConvention -> fragmentsForConvention(accessor)
                    is Accessor.ForTask -> fragmentsForTask(accessor)
                    is Accessor.ForContainerElement -> fragmentsForContainerElement(accessor)
                }

            val sourceCode = mutableListOf<String>()
            val metadataWriter = beginFileFacadeClassHeader()
            val classWriter = beginPublicClass(className)

            for ((source, bytecode, metadata, signature) in fragments) {
                sourceCode.add(source)
                MetadataFragmentScope(signature, metadataWriter).run(metadata)
                BytecodeFragmentScope(signature, classWriter).run(bytecode)
            }

            val sourceFile = srcDir.resolve("${className.value.removeSuffix("Kt")}.kt")
            writer.execute {
                writeAccessorsTo(sourceFile, sourceCode.asSequence())
            }

            val classHeader = metadataWriter.closeHeader()
            val classBytes = classWriter.run {
                visitKotlinMetadataAnnotation(classHeader)
                classWriter.endClass()
            }
            val classFile = binDir.resolve("$className.class")
            writer.writeFile(classFile, classBytes)

            className
        }.toList()

        writer.writeFile(
            moduleFileFor(binDir),
            moduleMetadataBytesFor(emittedClassNames)
        )

        emittedClassNames
    }

    private
    fun fragmentsForConfiguration(accessor: Accessor.ForConfiguration): Fragments = accessor.run {

        val propertyName = name.original
        val className = InternalName("$packagePath/${propertyName.capitalize()}ConfigurationAccessorsKt")

        className to sequenceOf(
            AccessorFragment(
                source = name.run {
                    """
                        /**
                         * Adds a dependency to the '$original' configuration.
                         *
                         * @param dependencyNotation notation for the dependency to be added.
                         * @return The dependency.
                         *
                         * @see [DependencyHandler.add]
                         */
                        fun DependencyHandler.`$kotlinIdentifier`(dependencyNotation: Any): Dependency? =
                            add("$stringLiteral", dependencyNotation)
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(0)
                        LDC(name.original)
                        ALOAD(1)
                        invokeDependencyHandlerAdd()
                        ARETURN()
                    }
                },
                metadata = {
                    writer.writeFunctionOf(
                        receiverType = GradleType.dependencyHandler,
                        nullableReturnType = GradleType.dependency,
                        name = signature.name,
                        parameterName = "dependencyNotation",
                        parameterType = KotlinType.any,
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    name.original,
                    "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;"
                )
            ),
            AccessorFragment(
                source = name.run {
                    """
                        /**
                         * Adds a dependency to the '$original' configuration.
                         *
                         * @param dependencyNotation notation for the dependency to be added.
                         * @param dependencyConfiguration expression to use to configure the dependency.
                         * @return The dependency.
                         *
                         * @see [DependencyHandler.add]
                         */
                        inline fun DependencyHandler.`$kotlinIdentifier`(
                            dependencyNotation: String,
                            dependencyConfiguration: Action<ExternalModuleDependency>
                        ): ExternalModuleDependency = addDependencyTo(
                            this, "$stringLiteral", dependencyNotation, dependencyConfiguration
                        ) as ExternalModuleDependency
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(0)
                        LDC(propertyName)
                        ALOAD(1)
                        ALOAD(2)
                        invokeRuntime(
                            "addDependencyTo",
                            "(L${DependencyHandler::class.internalName};Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/Dependency;"
                        )
                        CHECKCAST(ExternalModuleDependency::class.internalName)
                        ARETURN()
                    }
                },
                metadata = {
                    writer.writeFunctionOf(
                        receiverType = GradleType.dependencyHandler,
                        returnType = GradleType.externalModuleDependency,
                        name = propertyName,
                        parameters = {
                            visitParameter("dependencyNotation", KotlinType.string)
                            visitParameter("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                        },
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/String;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/ExternalModuleDependency;"
                )
            ),
            AccessorFragment(
                source = name.run {
                    """
                        /**
                         * Adds a dependency to the '$original' configuration.
                         *
                         * @param group the group of the module to be added as a dependency.
                         * @param name the name of the module to be added as a dependency.
                         * @param version the optional version of the module to be added as a dependency.
                         * @param configuration the optional configuration of the module to be added as a dependency.
                         * @param classifier the optional classifier of the module artifact to be added as a dependency.
                         * @param ext the optional extension of the module artifact to be added as a dependency.
                         * @param dependencyConfiguration expression to use to configure the dependency.
                         * @return The dependency.
                         *
                         * @see [DependencyHandler.create]
                         * @see [DependencyHandler.add]
                         */
                        fun DependencyHandler.`$kotlinIdentifier`(
                            group: String,
                            name: String,
                            version: String? = null,
                            configuration: String? = null,
                            classifier: String? = null,
                            ext: String? = null,
                            dependencyConfiguration: Action<ExternalModuleDependency>? = null
                        ): ExternalModuleDependency = addExternalModuleDependencyTo(
                            this, "$stringLiteral", group, name, version, configuration, classifier, ext, dependencyConfiguration
                        )
                    """
                },
                bytecode = {

                    val methodBody: MethodVisitor.() -> Unit = {
                        ALOAD(0)
                        LDC(propertyName)
                        (1..7).forEach { ALOAD(it) }
                        invokeRuntime(
                            "addExternalModuleDependencyTo",
                            "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/ExternalModuleDependency;"
                        )
                        ARETURN()
                    }

                    publicStaticMethod(signature) {
                        methodBody()
                    }

                    // Usually, this method would compute the default argument values
                    // and delegate to the original implementation.
                    // Here we can simply inline the implementation in both
                    // methods.
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
                            "Lorg/gradle/api/Action;" +
                            "ILjava/lang/Object;" +
                            ")Lorg/gradle/api/artifacts/ExternalModuleDependency;"
                    )
                    publicStaticSyntheticMethod(overload3Defaults) {
                        methodBody()
                    }
                },
                metadata = {
                    // TODO:accessors - inline function with optional parameters
                    writer.writeFunctionOf(
                        functionFlags = nonInlineFunctionFlags,
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
                            visitOptionalParameter("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                        },
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/ExternalModuleDependency;"
                )
            ),
            AccessorFragment(
                source = name.run {
                    """
                        /**
                         * Adds a dependency to the '$original' configuration.
                         *
                         * @param dependency dependency to be added.
                         * @param dependencyConfiguration expression to use to configure the dependency.
                         * @return The dependency.
                         *
                         * @see [DependencyHandler.add]
                         */
                        inline fun <T : ModuleDependency> DependencyHandler.`$kotlinIdentifier`(
                            dependency: T,
                            dependencyConfiguration: T.() -> Unit
                        ): T = add("$stringLiteral", dependency, dependencyConfiguration)
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(2)
                        ALOAD(1)
                        invokeAction()
                        ALOAD(0)
                        LDC(propertyName)
                        ALOAD(1)
                        INVOKEINTERFACE(DependencyHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
                        ARETURN()
                    }
                },
                metadata = {
                    writer.visitFunction(inlineFunctionFlags, propertyName)!!.run {
                        visitTypeParameter(0, "T", 0, KmVariance.INVARIANT)!!.run {
                            visitUpperBound(0).with(GradleType.dependency)
                            visitEnd()
                        }
                        visitReceiverParameterType(0).with(GradleType.dependencyHandler)
                        visitParameter("dependency", typeParameter)
                        visitParameter("action", actionTypeOf(typeParameter))
                        visitReturnType(0).with(typeParameter)
                        visitSignature(signature)
                        visitEnd()
                    }
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(" +
                        "Lorg/gradle/api/artifacts/dsl/DependencyHandler;" +
                        "Lorg/gradle/api/artifacts/Dependency;" +
                        "Lorg/gradle/api/Action;" +
                        ")Lorg/gradle/api/artifacts/Dependency;"
                )
            ),
            AccessorFragment(
                source = name.run {
                    """
                        /**
                         * Adds a dependency constraint to the '$original' configuration.
                         *
                         * @param constraintNotation the dependency constraint notation
                         *
                         * @return the added dependency constraint
                         *
                         * @see [DependencyConstraintHandler.add]
                         */
                        @Incubating
                        fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any): DependencyConstraint? =
                            add("$stringLiteral", constraintNotation)
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(0)
                        LDC(propertyName)
                        ALOAD(1)
                        INVOKEINTERFACE(DependencyConstraintHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/DependencyConstraint;")
                        ARETURN()
                    }
                },
                metadata = {
                    writer.writeFunctionOf(
                        receiverType = GradleType.dependencyConstraintHandler,
                        nullableReturnType = GradleType.dependencyConstraint,
                        name = propertyName,
                        parameterName = "constraintNotation",
                        parameterType = KotlinType.any,
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(Lorg/gradle/api/artifacts/dsl/DependencyConstraintHandler;Ljava/lang/Object;)Lorg/gradle/api/artifacts/DependencyConstraint;"
                )
            ),
            AccessorFragment(
                source = name.run {
                    """
                        /**
                         * Adds a dependency constraint to the '$original' configuration.
                         *
                         * @param constraintNotation the dependency constraint notation
                         * @param block the block to use to configure the dependency constraint
                         *
                         * @return the added dependency constraint
                         *
                         * @see [DependencyConstraintHandler.add]
                         */
                        @Incubating
                        fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any, block: DependencyConstraint.() -> Unit): DependencyConstraint? =
                            add("$stringLiteral", constraintNotation, block)
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(0)
                        LDC(propertyName)
                        ALOAD(1)
                        ALOAD(2)
                        INVOKEINTERFACE(DependencyConstraintHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/DependencyConstraint;")
                        ARETURN()
                    }
                },
                metadata = {
                    writer.writeFunctionOf(
                        receiverType = GradleType.dependencyConstraintHandler,
                        returnType = GradleType.dependencyConstraint,
                        name = propertyName,
                        parameters = {
                            visitParameter("constraintNotation", KotlinType.any)
                            visitParameter("block", actionTypeOf(GradleType.dependencyConstraint))
                        },
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(Lorg/gradle/api/artifacts/dsl/DependencyConstraintHandler;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/DependencyConstraint;"
                )
            ),
            AccessorFragment(
                source = name.run {
                    """
                    """
                },
                bytecode = {
                },
                metadata = {
                },
                signature = JvmMethodSignature(
                    name.original,
                    ""
                )
            )
        )
    }

    private
    val typeParameter: KmTypeBuilder = { visitTypeParameter(0) }

    private
    fun fragmentsForContainerElement(accessor: Accessor.ForContainerElement) =
        fragmentsForContainerElementOf(
            namedDomainObjectProviderTypeName,
            namedWithTypeMethodDescriptor,
            accessor.spec,
            existingContainerElementAccessor(accessor.spec)
        )

    private
    fun fragmentsForTask(accessor: Accessor.ForTask) =
        fragmentsForContainerElementOf(
            taskProviderTypeName,
            namedTaskWithTypeMethodDescriptor,
            accessor.spec,
            existingTaskAccessor(accessor.spec)
        )

    private
    fun fragmentsForContainerElementOf(
        providerType: InternalName,
        namedMethodDescriptor: String,
        accessorSpec: TypedAccessorSpec,
        source: String
    ): Fragments {

        val className = internalNameForAccessorClassOf(accessorSpec)
        val (accessibleReceiverType, name, returnType) = accessorSpec
        val propertyName = name.kotlinIdentifier
        val receiverType = accessibleReceiverType.type.builder
        val receiverTypeName = accessibleReceiverType.internalName()
        val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)

        return className to sequenceOf(
            AccessorFragment(
                source = source,
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(0)
                        LDC(propertyName)
                        LDC(jvmReturnType)
                        INVOKEINTERFACE(receiverTypeName, "named", namedMethodDescriptor)
                        ARETURN()
                    }
                },
                metadata = {
                    writer.writeElementAccessorMetadataFor(
                        receiverType,
                        providerType,
                        kotlinReturnType,
                        propertyName,
                        signature
                    )
                },
                signature = jvmGetterSignatureFor(
                    propertyName,
                    accessorDescriptorFor(receiverTypeName, providerType)
                )
            )
        )
    }

    private
    fun fragmentsForExtension(accessor: Accessor.ForExtension): Fragments {

        val accessorSpec = accessor.spec
        val className = internalNameForAccessorClassOf(accessorSpec)
        val (accessibleReceiverType, name, returnType) = accessorSpec
        val propertyName = name.kotlinIdentifier
        val receiverType = accessibleReceiverType.type.builder
        val receiverTypeName = accessibleReceiverType.internalName()
        val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)

        return className to sequenceOf(

            AccessorFragment(
                source = extensionAccessor(accessorSpec),
                bytecode = {
                    publicStaticMethod(signature) {
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
                },
                metadata = {
                    writer.writePropertyOf(
                        receiverType = receiverType,
                        returnType = kotlinReturnType,
                        propertyName = propertyName,
                        getterSignature = signature
                    )
                },
                signature = jvmGetterSignatureFor(
                    propertyName,
                    accessorDescriptorFor(receiverTypeName, jvmReturnType)
                )
            ),

            AccessorFragment(
                source = name.run {
                    """
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(0)
                        CHECKCAST(extensionAwareTypeName)
                        INVOKEINTERFACE(extensionAwareTypeName, "getExtensions", "()Lorg/gradle/api/plugins/ExtensionContainer;")
                        LDC(name.original)
                        ALOAD(1)
                        INVOKEINTERFACE(extensionContainerTypeName, "configure", "(Ljava/lang/String;Lorg/gradle/api/Action;)V")
                        RETURN()
                    }
                },
                metadata = {
                    writer.writeFunctionOf(
                        receiverType = receiverType,
                        returnType = KotlinType.unit,
                        parameters = {
                            visitParameter("configure", actionTypeOf(kotlinReturnType))
                        },
                        name = propertyName,
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(L$receiverTypeName;Lorg/gradle/api/Action;)V"
                )
            )
        )
    }

    private
    fun fragmentsForConvention(accessor: Accessor.ForConvention): Fragments {

        val accessorSpec = accessor.spec
        val className = internalNameForAccessorClassOf(accessorSpec)
        val (accessibleReceiverType, name, returnType) = accessorSpec
        val receiverType = accessibleReceiverType.type.builder
        val propertyName = name.kotlinIdentifier
        val receiverTypeName = accessibleReceiverType.internalName()
        val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)

        return className to sequenceOf(

            AccessorFragment(
                source = conventionAccessor(accessorSpec),
                bytecode = {
                    publicStaticMethod(signature) {
                        loadConventionOf(name, returnType, jvmReturnType)
                        ARETURN()
                    }
                },
                metadata = {
                    writer.writePropertyOf(
                        receiverType = receiverType,
                        returnType = kotlinReturnType,
                        propertyName = propertyName,
                        getterSignature = signature
                    )
                },
                signature = jvmGetterSignatureFor(
                    propertyName,
                    accessorDescriptorFor(receiverTypeName, jvmReturnType)
                )
            ),

            AccessorFragment(
                source = name.run {
                    """
                    """
                },
                bytecode = {
                    publicStaticMethod(signature) {
                        ALOAD(1)
                        loadConventionOf(name, returnType, jvmReturnType)
                        invokeAction()
                        RETURN()
                    }
                },
                metadata = {
                    writer.writeFunctionOf(
                        receiverType = receiverType,
                        returnType = KotlinType.unit,
                        parameters = {
                            visitParameter("configure", actionTypeOf(kotlinReturnType))
                        },
                        name = propertyName,
                        signature = signature
                    )
                },
                signature = JvmMethodSignature(
                    propertyName,
                    "(L$receiverTypeName;Lorg/gradle/api/Action;)V"
                )
            )
        )
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
                actualTypeArguments.map { it.builder }
            )
            isWildcard -> upperBound?.builder ?: KotlinType.any
            else -> classOf(concreteClass)
        }

    private
    fun internalNameForAccessorClassOf(accessorSpec: TypedAccessorSpec): InternalName =
        InternalName("$packagePath/Accessors${hashOf(accessorSpec)}Kt")

    private
    fun MethodVisitor.invokeDependencyHandlerAdd() {
        INVOKEINTERFACE(DependencyHandler::class.internalName, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
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
    val extensionAwareTypeName = ExtensionAware::class.internalName

    private
    val extensionContainerTypeName = ExtensionContainer::class.internalName

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
    fun accessorDescriptorFor(receiverType: InternalName, returnType: InternalName) =
        "(L$receiverType;)L$returnType;"
}


internal
fun makeAccessorOutputDirs(srcDir: File, binDir: File) {
    srcDir.resolve(packagePath).mkdirs()
    binDir.resolve(packagePath).mkdirs()
    binDir.resolve("META-INF").mkdir()
}


internal
const val packagePath = "org/gradle/kotlin/dsl"


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
                uniqueAccessorsFrom(
                    configurationNames.map(::configurationAccessorSpec)
                ).map(Accessor::ForContainerElement)
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
fun ClassVisitor.publicStaticSyntheticMethod(
    signature: JvmMethodSignature,
    methodBody: MethodVisitor.() -> Unit
) = method(
    Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_SYNTHETIC,
    signature.name,
    signature.desc,
    methodBody = methodBody
)


private
inline fun <reified T> classOf(): KmTypeBuilder =
    classOf(T::class.java)


private
fun classOf(`class`: Class<*>) =
    classOf(`class`.internalName)


private
fun classOf(className: InternalName): KmTypeBuilder =
    kotlinNameOf(className).let { kotlinName ->
        { visitClass(kotlinName) }
    }


private
fun kotlinNameOf(className: InternalName) = className.run {
    when {
        value.startsWith("kotlin/jvm/functions/") -> {
            "kotlin/" + value.substringAfter("kotlin/jvm/functions/")
        }
        else -> {
            kotlinPrimitiveTypes[value] ?: value.replace('$', '.')
        }
    }
}


private
val kotlinPrimitiveTypes = primitiveTypeStrings.asSequence().map { (jvmName, kotlinName) ->
    jvmName.replace('.', '/') to "kotlin/$kotlinName"
}.toMap()


private
fun genericTypeOf(genericType: KmTypeBuilder, genericArgument: KmTypeBuilder): KmTypeBuilder = {
    genericType()
    visitArgument(0, KmVariance.INVARIANT)!!.run {
        genericArgument()
        visitEnd()
    }
}


private
fun genericTypeOf(genericType: KmTypeBuilder, genericArguments: Iterable<KmTypeBuilder>): KmTypeBuilder = {
    genericType()
    genericArguments.forEach { argument ->
        visitArgument(0, KmVariance.INVARIANT)!!.run {
            argument()
            visitEnd()
        }
    }
}
