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
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.deprecation.ConfigurationDeprecationType
import org.gradle.internal.hash.Hashing.hashString
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
import org.gradle.kotlin.dsl.support.bytecode.genericTypeOf
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.kotlinDeprecation
import org.gradle.kotlin.dsl.support.bytecode.providerConvertibleOfStar
import org.gradle.kotlin.dsl.support.bytecode.providerOfStar
import org.gradle.kotlin.dsl.support.bytecode.publicFunctionFlags
import org.gradle.kotlin.dsl.support.bytecode.publicFunctionWithAnnotationsFlags
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.publicStaticSyntheticMethod
import org.gradle.kotlin.dsl.support.bytecode.visitOptionalParameter
import org.gradle.kotlin.dsl.support.bytecode.visitParameter
import org.gradle.kotlin.dsl.support.bytecode.visitSignature
import org.gradle.kotlin.dsl.support.bytecode.with
import org.gradle.kotlin.dsl.support.bytecode.writeFunctionOf
import org.gradle.kotlin.dsl.support.bytecode.writePropertyOf
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.org.objectweb.asm.MethodVisitor


internal
fun fragmentsFor(accessor: Accessor): Fragments = when (accessor) {
    is Accessor.ForConfiguration -> fragmentsForConfiguration(accessor)
    is Accessor.ForExtension -> fragmentsForExtension(accessor)
    is Accessor.ForConvention -> fragmentsForConvention(accessor)
    is Accessor.ForTask -> fragmentsForTask(accessor)
    is Accessor.ForContainerElement -> fragmentsForContainerElement(accessor)
}


private
fun fragmentsForConfiguration(accessor: Accessor.ForConfiguration): Fragments = accessor.run {

    val name = config.target
    val propertyName = name.original
    val className = "${propertyName.uppercaseFirstChar()}ConfigurationAccessorsKt"
    val (functionFlags, deprecationBlock) =
        if (config.hasDeclarationDeprecations()) publicFunctionWithAnnotationsFlags to config.getDeclarationDeprecationBlock()
        else publicFunctionFlags to ""

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
                     */$deprecationBlock
                    fun DependencyHandler.`$kotlinIdentifier`(dependencyNotation: Any): Dependency? =
                        add("$stringLiteral", dependencyNotation)
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(0)
                    LDC(name.original)
                    ALOAD(1)
                    invokeDependencyHandlerAdd()
                    ARETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
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
                     */$deprecationBlock
                    fun DependencyHandler.`$kotlinIdentifier`(
                        dependencyNotation: String,
                        dependencyConfiguration: Action<ExternalModuleDependency>
                    ): ExternalModuleDependency = addDependencyTo(
                        this, "$stringLiteral", dependencyNotation, dependencyConfiguration
                    ) as ExternalModuleDependency
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    invokeRuntime(
                        "addDependencyTo",
                        "(L${GradleTypeName.dependencyHandler};Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/Dependency;"
                    )
                    CHECKCAST(GradleTypeName.externalModuleDependency)
                    ARETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
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
                     * @param dependencyNotation notation for the dependency to be added.
                     * @param dependencyConfiguration expression to use to configure the dependency.
                     * @return The dependency.
                     *
                     * @see [DependencyHandler.add]
                     */$deprecationBlock
                    fun DependencyHandler.`$kotlinIdentifier`(
                        dependencyNotation: Provider<*>,
                        dependencyConfiguration: Action<ExternalModuleDependency>
                    ): Unit = addConfiguredDependencyTo(
                        this, "$stringLiteral", dependencyNotation, dependencyConfiguration
                    )
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    invokeRuntime(
                        "addConfiguredDependencyTo",
                        "(L${GradleTypeName.dependencyHandler};Ljava/lang/String;Lorg/gradle/api/provider/Provider;Lorg/gradle/api/Action;)V"
                    )
                    RETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = KotlinType.unit,
                    name = propertyName,
                    parameters = {
                        visitParameter("dependencyNotation", providerOfStar())
                        visitParameter("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                    },
                    signature = signature
                )
            },
            signature = JvmMethodSignature(
                propertyName,
                "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Lorg/gradle/api/provider/Provider;Lorg/gradle/api/Action;)V"
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
                     */$deprecationBlock
                    fun DependencyHandler.`$kotlinIdentifier`(
                        dependencyNotation: ProviderConvertible<*>,
                        dependencyConfiguration: Action<ExternalModuleDependency>
                    ): Unit = addConfiguredDependencyTo(
                        this, "$stringLiteral", dependencyNotation, dependencyConfiguration
                    )
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    invokeRuntime(
                        "addConfiguredDependencyTo",
                        "(L${GradleTypeName.dependencyHandler};Ljava/lang/String;Lorg/gradle/api/provider/ProviderConvertible;Lorg/gradle/api/Action;)V"
                    )
                    RETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = KotlinType.unit,
                    name = propertyName,
                    parameters = {
                        visitParameter("dependencyNotation", providerConvertibleOfStar())
                        visitParameter("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                    },
                    signature = signature
                )
            },
            signature = JvmMethodSignature(
                propertyName,
                "(Lorg/gradle/api/artifacts/dsl/DependencyHandler;Lorg/gradle/api/provider/ProviderConvertible;Lorg/gradle/api/Action;)V"
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
                     */$deprecationBlock
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

                publicStaticMaybeDeprecatedMethod(signature, config) {
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
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
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
                     */$deprecationBlock
                    fun <T : ModuleDependency> DependencyHandler.`$kotlinIdentifier`(
                        dependency: T,
                        dependencyConfiguration: T.() -> Unit
                    ): T = add("$stringLiteral", dependency, dependencyConfiguration)
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(2)
                    ALOAD(1)
                    invokeAction()
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    INVOKEINTERFACE(GradleTypeName.dependencyHandler, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
                    ARETURN()
                }
            },
            metadata = {
                writer.visitFunction(functionFlags, propertyName)!!.run {
                    visitTypeParameter(0, "T", 0, KmVariance.INVARIANT)!!.run {
                        visitUpperBound(0).with(GradleType.dependency)
                        visitEnd()
                    }
                    visitReceiverParameterType(0).with(GradleType.dependencyHandler)
                    visitParameter("dependency", KotlinType.typeParameter)
                    visitParameter("action", actionTypeOf(KotlinType.typeParameter))
                    visitReturnType(0).with(KotlinType.typeParameter)
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
                     */$deprecationBlock
                    fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any): DependencyConstraint? =
                        add("$stringLiteral", constraintNotation)
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    INVOKEINTERFACE(GradleTypeName.dependencyConstraintHandler, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/DependencyConstraint;")
                    ARETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
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
                     */$deprecationBlock
                    fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any, block: DependencyConstraint.() -> Unit): DependencyConstraint? =
                        add("$stringLiteral", constraintNotation, block)
                """
            },
            bytecode = {
                publicStaticMaybeDeprecatedMethod(signature, config) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    INVOKEINTERFACE(GradleTypeName.dependencyConstraintHandler, "add", "(Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/DependencyConstraint;")
                    ARETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    functionFlags = functionFlags,
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
                    /**
                     * Adds an artifact to the '$original' configuration.
                     *
                     * @param artifactNotation the group of the module to be added as a dependency.
                     * @return The artifact.
                     *
                     * @see [ArtifactHandler.add]
                     */
                    fun ArtifactHandler.`$kotlinIdentifier`(artifactNotation: Any): PublishArtifact =
                        add("$stringLiteral", artifactNotation)
                """
            },
            bytecode = {
                publicStaticMethod(signature) {
                    ALOAD(0)
                    LDC(name.original)
                    ALOAD(1)
                    INVOKEINTERFACE(GradleTypeName.artifactHandler, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/PublishArtifact;")
                    ARETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    receiverType = GradleType.artifactHandler,
                    returnType = GradleType.publishArtifact,
                    name = propertyName,
                    functionFlags = publicFunctionFlags,
                    parameters = {
                        visitParameter("artifactNotation", KotlinType.any)
                    },
                    signature = signature
                )
            },
            signature = JvmMethodSignature(
                name.original,
                "(Lorg/gradle/api/artifacts/dsl/ArtifactHandler;Ljava/lang/Object;)Lorg/gradle/api/artifacts/PublishArtifact;"
            )
        ),
        AccessorFragment(
            source = name.run {
                """
                    /**
                     * Adds an artifact to the '$original' configuration.
                     *
                     * @param artifactNotation the group of the module to be added as a dependency.
                     * @param configureAction The action to execute to configure the artifact.
                     * @return The artifact.
                     *
                     * @see [ArtifactHandler.add]
                     */
                    fun ArtifactHandler.`$kotlinIdentifier`(
                        artifactNotation: Any,
                        configureAction:  ConfigurablePublishArtifact.() -> Unit
                    ): PublishArtifact =
                        add("$stringLiteral", artifactNotation, configureAction)
                """
            },
            bytecode = {
                publicStaticMethod(signature) {
                    ALOAD(0)
                    LDC(propertyName)
                    ALOAD(1)
                    ALOAD(2)
                    INVOKEINTERFACE(GradleTypeName.artifactHandler, "add", "(Ljava/lang/String;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/PublishArtifact;")
                    ARETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    receiverType = GradleType.artifactHandler,
                    returnType = GradleType.publishArtifact,
                    name = propertyName,
                    functionFlags = publicFunctionFlags,
                    parameters = {
                        visitParameter("artifactNotation", KotlinType.any)
                        visitParameter("configureAction", actionTypeOf(GradleType.configurablePublishArtifact))
                    },
                    signature = signature
                )
            },
            signature = JvmMethodSignature(
                name.original,
                "(Lorg/gradle/api/artifacts/dsl/ArtifactHandler;Ljava/lang/Object;Lorg/gradle/api/Action;)Lorg/gradle/api/artifacts/PublishArtifact;"
            )
        ),
        AccessorFragment(
            source = "",
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
fun fragmentsForContainerElement(accessor: Accessor.ForContainerElement) =
    fragmentsForContainerElementOf(
        GradleTypeName.namedDomainObjectProvider,
        GradleTypeName.namedWithTypeMethodDescriptor,
        accessor.spec,
        existingContainerElementAccessor(accessor.spec)
    )


private
fun fragmentsForTask(accessor: Accessor.ForTask) =
    fragmentsForContainerElementOf(
        GradleTypeName.taskProvider,
        GradleTypeName.namedTaskWithTypeMethodDescriptor,
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
    val (accessibleReceiverType, name, extensionType) = accessorSpec
    val propertyName = name.kotlinIdentifier
    val receiverType = accessibleReceiverType.type.builder
    val receiverTypeName = accessibleReceiverType.internalName()
    val (kotlinExtensionType, jvmExtensionType) = accessibleTypesFor(extensionType)

    return className to sequenceOf(

        AccessorFragment(
            source = extensionAccessor(accessorSpec),
            signature = jvmGetterSignatureFor(
                propertyName,
                accessorDescriptorFor(receiverTypeName, jvmExtensionType)
            ),
            bytecode = {
                publicStaticMethod(signature) {
                    ALOAD(0)
                    LDC(name.original)
                    invokeRuntime(
                        "extensionOf",
                        "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
                    )
                    if (extensionType is TypeAccessibility.Accessible)
                        CHECKCAST(jvmExtensionType)
                    ARETURN()
                }
            },
            metadata = {
                writer.writePropertyOf(
                    receiverType = receiverType,
                    returnType = kotlinExtensionType,
                    propertyName = propertyName,
                    getterSignature = signature
                )
            }
        ),

        AccessorFragment(
            source = "",
            signature = JvmMethodSignature(
                propertyName,
                "(L$receiverTypeName;Lorg/gradle/api/Action;)V"
            ),
            bytecode = {
                publicStaticMethod(signature) {
                    ALOAD(0)
                    CHECKCAST(GradleTypeName.extensionAware)
                    INVOKEINTERFACE(
                        GradleTypeName.extensionAware,
                        "getExtensions",
                        "()Lorg/gradle/api/plugins/ExtensionContainer;"
                    )
                    LDC(name.original)
                    ALOAD(1)
                    INVOKEINTERFACE(
                        GradleTypeName.extensionContainer,
                        "configure",
                        "(Ljava/lang/String;Lorg/gradle/api/Action;)V"
                    )
                    RETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    receiverType = receiverType,
                    returnType = KotlinType.unit,
                    parameters = {
                        visitParameter(
                            "configure",
                            actionTypeOf(kotlinExtensionType)
                        )
                    },
                    name = propertyName,
                    signature = signature
                )
            }
        )
    )
}


private
fun fragmentsForConvention(accessor: Accessor.ForConvention): Fragments {

    val accessorSpec = accessor.spec
    val className = internalNameForAccessorClassOf(accessorSpec)
    val (accessibleReceiverType, name, conventionType) = accessorSpec
    val receiverType = accessibleReceiverType.type.builder
    val propertyName = name.kotlinIdentifier
    val receiverTypeName = accessibleReceiverType.internalName()
    val (kotlinConventionType, jvmConventionType) = accessibleTypesFor(conventionType)

    return className to sequenceOf(

        AccessorFragment(
            source = conventionAccessor(accessorSpec),
            signature = jvmGetterSignatureFor(
                propertyName,
                accessorDescriptorFor(receiverTypeName, jvmConventionType)
            ),
            bytecode = {
                publicStaticMethod(signature) {
                    loadConventionOf(name, conventionType, jvmConventionType)
                    ARETURN()
                }
            },
            metadata = {
                writer.writePropertyOf(
                    receiverType = receiverType,
                    returnType = kotlinConventionType,
                    propertyName = propertyName,
                    getterSignature = signature
                )
            }
        ),

        AccessorFragment(
            source = "",
            bytecode = {
                publicStaticMethod(signature) {
                    ALOAD(1)
                    loadConventionOf(name, conventionType, jvmConventionType)
                    invokeAction()
                    RETURN()
                }
            },
            metadata = {
                writer.writeFunctionOf(
                    receiverType = receiverType,
                    returnType = KotlinType.unit,
                    parameters = {
                        visitParameter("configure", actionTypeOf(kotlinConventionType))
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
fun MethodVisitor.invokeDependencyHandlerAdd() {
    INVOKEINTERFACE(GradleTypeName.dependencyHandler, "add", "(Ljava/lang/String;Ljava/lang/Object;)Lorg/gradle/api/artifacts/Dependency;")
}


private
fun MethodVisitor.invokeRuntime(function: String, desc: String) {
    INVOKESTATIC(InternalName("org/gradle/kotlin/dsl/accessors/runtime/RuntimeKt"), function, desc)
}


private
fun hashOf(accessorSpec: TypedAccessorSpec) =
    hashString(accessorSpec.toString()).toCompactString()


private
fun TypeAccessibility.Accessible.internalName() =
    type.value.concreteClass.internalName


private
fun MethodVisitor.invokeAction() {
    INVOKEINTERFACE(GradleTypeName.action, "execute", "(Ljava/lang/Object;)V")
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
    is TypeAccessibility.Inaccessible -> KotlinType.any to InternalNameOf.javaLangObject
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
        isWildcard -> (upperBound ?: lowerBound)?.builder ?: KotlinType.any
        else -> classOf(concreteClass)
    }


internal
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
fun internalNameForAccessorClassOf(accessorSpec: TypedAccessorSpec): String =
    "Accessors${hashOf(accessorSpec)}Kt"


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
fun accessorDescriptorFor(receiverType: InternalName, returnType: InternalName) =
    "(L$receiverType;)L$returnType;"


private
fun ConfigurationEntry<AccessorNameSpec>.getDeclarationDeprecationMessage() = if (hasDeclarationDeprecations()) {
    val deprecationType = ConfigurationDeprecationType.DEPENDENCY_DECLARATION
    val summary = "The ${target.original} configuration has been deprecated for ${deprecationType.displayName()}."
    val suggestion = "Please ${deprecationType.usage} the ${dependencyDeclarationAlternatives.joinToString(" or ", transform = ::quote)} configuration instead."
    "$summary $suggestion"
} else {
    ""
}


private
fun ConfigurationEntry<AccessorNameSpec>.getDeclarationDeprecationBlock() =
    "\n                    @Deprecated(message = \"${getDeclarationDeprecationMessage()}\")"


private
fun quote(str: String) = "'$str'"


private
fun BytecodeFragmentScope.publicStaticMaybeDeprecatedMethod(
    jvmMethodSignature: JvmMethodSignature,
    config: ConfigurationEntry<AccessorNameSpec>,
    signature: String? = null,
    exceptions: Array<String>? = null,
    methodBody: MethodVisitor.() -> Unit
) {
    if (config.hasDeclarationDeprecations()) {
        publicStaticMethod(
            jvmMethodSignature, signature, exceptions, true,
            {
                kotlinDeprecation(config.getDeclarationDeprecationMessage())
            },
            methodBody
        )
    } else {
        publicStaticMethod(jvmMethodSignature, signature, exceptions, false, {}, methodBody)
    }
}
