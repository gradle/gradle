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

import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.deprecation.ConfigurationDeprecationType
import org.gradle.internal.hash.Hashing.hashString
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.primitiveTypeStrings
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
import org.gradle.kotlin.dsl.support.bytecode.genericTypeOf
import org.gradle.kotlin.dsl.support.bytecode.inlineGetterAttributes
import org.gradle.kotlin.dsl.support.bytecode.internalName
import org.gradle.kotlin.dsl.support.bytecode.jvmGetterSignatureFor
import org.gradle.kotlin.dsl.support.bytecode.kotlinDeprecation
import org.gradle.kotlin.dsl.support.bytecode.newClassTypeOf
import org.gradle.kotlin.dsl.support.bytecode.newFunctionOf
import org.gradle.kotlin.dsl.support.bytecode.newOptionalValueParameterOf
import org.gradle.kotlin.dsl.support.bytecode.newPropertyOf
import org.gradle.kotlin.dsl.support.bytecode.newTypeParameterOf
import org.gradle.kotlin.dsl.support.bytecode.newValueParameterOf
import org.gradle.kotlin.dsl.support.bytecode.nullable
import org.gradle.kotlin.dsl.support.bytecode.providerConvertibleOfStar
import org.gradle.kotlin.dsl.support.bytecode.providerOfStar
import org.gradle.kotlin.dsl.support.bytecode.publicFunctionAttributes
import org.gradle.kotlin.dsl.support.bytecode.publicFunctionWithAnnotationsAttributes
import org.gradle.kotlin.dsl.support.bytecode.publicStaticMethod
import org.gradle.kotlin.dsl.support.bytecode.publicStaticSyntheticMethod
import org.gradle.kotlin.dsl.support.bytecode.readOnlyPropertyAttributes
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Type
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.KmPropertyAccessorAttributes
import kotlin.metadata.KmType
import kotlin.metadata.KmTypeProjection
import kotlin.metadata.KmVariance
import kotlin.metadata.isNullable
import kotlin.metadata.jvm.JvmMethodSignature
import kotlin.metadata.jvm.hasAnnotationsInBytecode


internal
fun fragmentsFor(accessor: Accessor): Fragments = when (accessor) {
    is Accessor.ForConfiguration -> fragmentsForConfiguration(accessor)
    is Accessor.ForExtension -> fragmentsForExtension(accessor)
    is Accessor.ForTask -> fragmentsForTask(accessor)
    is Accessor.ForContainerElement -> fragmentsForContainerElement(accessor)
    is Accessor.ForModelDefault -> fragmentsForModelDefault(accessor)
    is Accessor.ForProjectType -> fragmentsForProjectType(accessor)
    is Accessor.ForContainerElementFactory -> fragmentsForContainerElementFactory(accessor)
}

private fun fragmentsForProjectType(accessor: Accessor.ForProjectType): Fragments = accessor.run {
    val className = "${accessor.spec.projectFeatureName.original.uppercaseFirstChar()}ContainerElementFactoriesKt"
    val functionName = spec.projectFeatureName.original
    val (kotlinModelType, _) = accessibleTypesFor(accessor.spec.modelType)
    val (kotlinTargetType, jvmTargetType) = accessibleTypesFor(accessor.spec.targetType)
    val deprecation = highestDeprecationByLevel(accessor.spec.modelType.deprecation(), accessor.spec.targetType.deprecation())
    val annotations = "${maybeDeprecationAnnotations(deprecation)}${maybeOptInAnnotationSource(accessor.spec.modelType, accessor.spec.targetType)}"

    val targetTypeKotlinString = spec.targetType.type.kotlinString
    val featureKind = when (accessor.spec.targetType.type.value.concreteClass) {
        Project::class.java -> "project type"
        else -> "project feature"
    }

    className to sequenceOf(
        AccessorFragment(
            source = """
            |        /**
            |         * Applies the "$functionName" $featureKind to the target and configures the definition with the [configure] action.
            |         */
            |        @Incubating
            |        ${annotations}fun $targetTypeKotlinString.`${functionName}`(configure: Action<in ${spec.modelType.type.kotlinString}>) {
            |            applyProjectType(this, "$functionName", configure)
            |        }
            """.trimMargin(),
            signature = JvmMethodSignature(
                functionName,
                "(L$jvmTargetType;Lorg/gradle/api/Action;)V"
            ),
            bytecode = {
                publicStaticMethod(signature, annotations = {
                    visitAnnotation(Type.getDescriptor(Incubating::class.java), true).visitEnd()
                }) {
                    maybeWithDeprecation(deprecation)
                    ALOAD(0)
                    CHECKCAST(DynamicObjectAware::class.internalName)
                    LDC(functionName)
                    ALOAD(1)
                    invokeRuntime("applyProjectFeature", "(L${DynamicObjectAware::class.internalName};L${String::class.internalName};L${Action::class.internalName};)V")
                    RETURN()
                }
            },
            metadata = {
                kmPackage.functions += newFunctionOf(
                    functionAttributes = publicFunctionWithAnnotationsAttributes, // has @Incubating and maybe deprecations
                    receiverType = kotlinTargetType,
                    valueParameters = listOf(
                        newValueParameterOf("configure", newClassTypeOf(Action::class.java.name.replace(".", "/"), KmTypeProjection(KmVariance.IN, kotlinModelType)))
                    ),
                    returnType = KotlinType.unit,
                    name = functionName,
                    signature = signature
                )
            }
        ))
}

private fun fragmentsForContainerElementFactory(accessor: Accessor.ForContainerElementFactory): Fragments = accessor.run {
    val elementFactoryName = accessor.spec.name.original
    val elementFactoryHash = hashString(accessor.toString()).toCompactString() // with multiple factories having the same name, resolve ambiguity with the hash
    val className = "${accessor.spec.name.original.uppercaseFirstChar()}${elementFactoryHash}ContainerElementFactoriesKt"
    val (kotlinElementType, _) = accessibleTypesFor(accessor.spec.elementType)
    val (kotlinReceiverType, jvmReceiverType) = accessibleTypesFor(accessor.spec.receiverType)
    val elementTypeKotlinString = accessor.spec.elementType.type.kotlinString
    val deprecation = accessor.spec.elementType.deprecation()
    val annotations = "${maybeDeprecationAnnotations(deprecation)}${maybeOptInAnnotationSource(accessor.spec.elementType)}"


    className to sequenceOf(
        AccessorFragment(
            source = elementFactoryName.run {
                """
                |        /**
                |         * Registers or configures a new "$elementFactoryName" element in a named domain object container of [$elementTypeKotlinString].
                |         */
                |        @${Incubating::class.simpleName}
                |        ${annotations}fun ${accessor.spec.receiverType.type.kotlinString}.`$elementFactoryName`(
                |            name: String,
                |            configure: Action<in $elementTypeKotlinString>
                |        ) {
                |            if (name in names) {
                |                named(name, configure)
                |            } else {
                |                register(name, configure)
                |            }
                |        }
                """.trimMargin()
            },
            bytecode = {
                publicStaticMethod(signature, annotations = {
                    visitAnnotation(Type.getDescriptor(Incubating::class.java), true).visitEnd()
                    maybeWithDeprecation(deprecation)
                }) {
                    ALOAD(0)
                    ALOAD(1)
                    ALOAD(2)
                    invokeRuntime("maybeRegister", signature.descriptor)
                    RETURN()
                }
            },
            metadata = {
                kmPackage.functions += newFunctionOf(
                    functionAttributes = publicFunctionWithAnnotationsAttributes, // has @Incubating and maybe deprecations
                    receiverType = kotlinReceiverType,
                    valueParameters = listOf(
                        newValueParameterOf("name", KotlinType.string.also { it.isNullable = false }),
                        newValueParameterOf("configure", newClassTypeOf(Action::class.java.name.replace(".", "/"), KmTypeProjection(KmVariance.IN, kotlinElementType)))
                    ),
                    returnType = KotlinType.unit,
                    name = elementFactoryName,
                    signature = signature
                )
            },
            signature = JvmMethodSignature(
                elementFactoryName,
                "(L$jvmReceiverType;Ljava/lang/String;Lorg/gradle/api/Action;)V"
            )
        )
    )
}

@Suppress("LongMethod")
private
fun fragmentsForConfiguration(accessor: Accessor.ForConfiguration): Fragments = accessor.run {

    val name = config.target
    val propertyName = name.original
    val className = "${propertyName.uppercaseFirstChar()}ConfigurationAccessorsKt"
    val (functionFlags, deprecationBlock) =
        if (config.hasDeclarationDeprecations()) publicFunctionWithAnnotationsAttributes to config.getDeclarationDeprecationBlock()
        else publicFunctionAttributes to ""

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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = nullable(GradleType.dependency),
                    name = signature.name,
                    valueParameters = listOf(
                        newValueParameterOf("dependencyNotation", KotlinType.any),
                    ),
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = GradleType.externalModuleDependency,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("dependencyNotation", KotlinType.string),
                        newValueParameterOf("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                    ),
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = KotlinType.unit,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("dependencyNotation", providerOfStar()),
                        newValueParameterOf("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                    ),
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = KotlinType.unit,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("dependencyNotation", providerConvertibleOfStar()),
                        newValueParameterOf("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency))
                    ),
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
                     */
                    @Deprecated("Use single-string notation instead")
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
                    for (i in 1..7) {
                        ALOAD(i)
                    }
                    @Suppress("MaxLineLength")
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = GradleType.externalModuleDependency,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("group", KotlinType.string),
                        newValueParameterOf("name", KotlinType.string),
                        newOptionalValueParameterOf("version", KotlinType.string),
                        newOptionalValueParameterOf("configuration", KotlinType.string),
                        newOptionalValueParameterOf("classifier", KotlinType.string),
                        newOptionalValueParameterOf("ext", KotlinType.string),
                        newOptionalValueParameterOf("dependencyConfiguration", actionTypeOf(GradleType.externalModuleDependency)),
                    ),
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyHandler,
                    returnType = KotlinType.typeParameter,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("dependency", KotlinType.typeParameter),
                        newValueParameterOf("action", actionTypeOf(KotlinType.typeParameter))
                    ),
                    typeParameters = listOf(
                        newTypeParameterOf(name = "T", variance = KmVariance.INVARIANT, upperBound = GradleType.dependency)
                    ),
                    signature = signature
                )
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
                    fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any): DependencyConstraint =
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyConstraintHandler,
                    returnType = GradleType.dependencyConstraint,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("constraintNotation", KotlinType.any),
                    ),
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
                    fun DependencyConstraintHandler.`$kotlinIdentifier`(constraintNotation: Any, block: DependencyConstraint.() -> Unit): DependencyConstraint =
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = functionFlags,
                    receiverType = GradleType.dependencyConstraintHandler,
                    returnType = GradleType.dependencyConstraint,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("constraintNotation", KotlinType.any),
                        newValueParameterOf("block", actionTypeOf(GradleType.dependencyConstraint))
                    ),
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
                kmPackage.functions += newFunctionOf(
                    receiverType = GradleType.artifactHandler,
                    returnType = GradleType.publishArtifact,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("artifactNotation", KotlinType.any),
                    ),
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
                kmPackage.functions += newFunctionOf(
                    receiverType = GradleType.artifactHandler,
                    returnType = GradleType.publishArtifact,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("artifactNotation", KotlinType.any),
                        newValueParameterOf("configureAction", actionTypeOf(GradleType.configurablePublishArtifact))
                    ),
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
fun fragmentsForContainerElement(accessor: Accessor.ForContainerElement): Fragments =
    fragmentsForContainerElementOf(
        GradleTypeName.namedDomainObjectProvider,
        GradleTypeName.namedWithTypeMethodDescriptor,
        accessor.spec,
        existingContainerElementAccessor(accessor.spec)
    )


private
fun fragmentsForTask(accessor: Accessor.ForTask): Fragments =
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
    val receiverType = accessibleReceiverType.type.kmType
    val receiverTypeName = accessibleReceiverType.internalName()
    val (kotlinReturnType, jvmReturnType) = accessibleTypesFor(returnType)
    val deprecation = returnType.deprecation()
    val optIns = returnType.requiredOptIns()

    return className to sequenceOf(
        AccessorFragment(
            source = source,
            bytecode = {
                publicStaticMethod(signature) {
                    maybeWithDeprecation(deprecation)
                    maybeWithOptInRequirement(optIns)
                    ALOAD(0)
                    LDC(propertyName)
                    LDC(jvmReturnType)
                    INVOKEINTERFACE(receiverTypeName, "named", namedMethodDescriptor)
                    ARETURN()
                }
            },
            metadata = {
                kmPackage.properties += newPropertyOf(
                    name = propertyName,
                    receiverType = receiverType,
                    returnType = genericTypeOf(classOf(providerType), kotlinReturnType),
                    getterSignature = signature,
                    getterAttributes = {
                        inlineGetterAttributes()
                        hasAnnotationsIfDeprecated(deprecation)
                        hasAnnotationsIfRequiresOptIn(optIns)
                    },
                    propertyAttributes = {
                        readOnlyPropertyAttributes()
                        hasAnnotationsIfDeprecated(deprecation)
                        hasAnnotationsIfRequiresOptIn(optIns)
                    })
            },
            signature = jvmGetterSignatureFor(
                propertyName,
                accessorDescriptorFor(receiverTypeName, providerType)
            )
        )
    )
}


private
fun MetadataFragmentScope.maybeFunctionHasAnnotations(attributes: KmFunction.() -> Unit): KmFunction.() -> Unit = when {
    useLowPriorityInOverloadResolution -> {
        {
            attributes(this)
            hasAnnotationsInBytecode = true
        }
    }

    else -> attributes
}

private
fun MetadataFragmentScope.maybePropertyHasAnnotations(attributes: KmProperty.() -> Unit): KmProperty.() -> Unit = when {
    useLowPriorityInOverloadResolution -> {
        {
            attributes(this)
            hasAnnotationsInBytecode = true
        }
    }

    else -> attributes
}


private
fun fragmentsForExtension(accessor: Accessor.ForExtension): Fragments {

    val accessorSpec = accessor.spec
    val className = internalNameForAccessorClassOf(accessorSpec)
    val (accessibleReceiverType, name, extensionType) = accessorSpec
    val propertyName = name.kotlinIdentifier
    val receiverType = accessibleReceiverType.type.kmType
    val receiverTypeName = accessibleReceiverType.internalName()
    val (kotlinExtensionType, jvmExtensionType) = accessibleTypesFor(extensionType)
    val deprecation = accessorSpec.type.deprecation()
    val optInRequirement = accessorSpec.type.requiredOptIns()

    return className to sequenceOf(

        AccessorFragment(
            source = extensionAccessor(accessorSpec),
            signature = jvmGetterSignatureFor(
                propertyName,
                accessorDescriptorFor(receiverTypeName, jvmExtensionType)
            ),
            bytecode = {
                publicStaticMethod(signature) {
                    if (useLowPriorityInOverloadResolution) {
                        withLowPriorityInOverloadResolution()
                    }
                    maybeWithDeprecation(deprecation)
                    maybeWithOptInRequirement(optInRequirement)
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
                kmPackage.properties += newPropertyOf(
                    propertyAttributes = maybePropertyHasAnnotations {
                        readOnlyPropertyAttributes()
                        hasAnnotationsIfDeprecated(deprecation)
                        hasAnnotationsIfRequiresOptIn(optInRequirement)
                    },
                    name = propertyName,
                    receiverType = receiverType,
                    returnType = kotlinExtensionType,
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
                    if (useLowPriorityInOverloadResolution) {
                        withLowPriorityInOverloadResolution()
                    }
                    maybeWithDeprecation(deprecation)
                    maybeWithOptInRequirement(optInRequirement)
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
                kmPackage.functions += newFunctionOf(
                    functionAttributes = maybeFunctionHasAnnotations {
                        publicFunctionAttributes()
                        hasAnnotationsIfDeprecated(deprecation)
                        hasAnnotationsIfRequiresOptIn(optInRequirement)
                    },
                    receiverType = receiverType,
                    returnType = KotlinType.unit,
                    name = propertyName,
                    valueParameters = listOf(
                        newValueParameterOf("configure", actionTypeOf(kotlinExtensionType))
                    ),
                    signature = signature
                )
            }
        )
    )
}

private fun KmFunction.hasAnnotationsIfDeprecated(deprecation: Deprecated?) {
    if (deprecation != null) {
        hasAnnotationsInBytecode = true
    }
}

private fun KmProperty.hasAnnotationsIfDeprecated(deprecation: Deprecated?) {
    if (deprecation != null) {
        hasAnnotationsInBytecode = true
    }
}

private fun KmPropertyAccessorAttributes.hasAnnotationsIfDeprecated(deprecation: Deprecated?) {
    if (deprecation != null) {
        hasAnnotationsInBytecode = true
    }
}

private fun KmFunction.hasAnnotationsIfRequiresOptIn(optInRequirements: List<AnnotationRepresentation>?) {
    if (optInRequirements != null) {
        hasAnnotationsInBytecode = true
    }
}

private fun KmProperty.hasAnnotationsIfRequiresOptIn(optInRequirements: List<AnnotationRepresentation>?) {
    if (optInRequirements != null) {
        hasAnnotationsInBytecode = true
    }
}

private fun KmPropertyAccessorAttributes.hasAnnotationsIfRequiresOptIn(optInRequirements: List<AnnotationRepresentation>?) {
    if (optInRequirements != null) {
        hasAnnotationsInBytecode = true
    }
}


private
fun MethodVisitor.withLowPriorityInOverloadResolution() {
    visitAnnotation("Lkotlin/internal/LowPriorityInOverloadResolution;", true).visitEnd()
}

private fun MethodVisitor.maybeWithDeprecation(deprecated: Deprecated?) {
    if (deprecated != null) {
        visitAnnotation("Lkotlin/Deprecated;", true).run {
            visit("message", deprecated.message)
            visitEnum("level", "Lkotlin/DeprecationLevel;", deprecated.level.name)
            visitEnd()
        }
    }
}

private object AnnotationUtils {
    private fun handleAnnotation(
        annotation: AnnotationRepresentation,
        visitAnnotation: (typeDescriptor: String) -> AnnotationVisitor
    ) {
        val annotationClass = annotation.type.value.concreteClass
        visitAnnotation(Type.getDescriptor(annotationClass)).run {
            for ((name, valueRepresentation) in annotation.values) {
                visitValue(name, valueRepresentation)
            }
            visitEnd()
        }
    }

    fun MethodVisitor.writeAnnotation(annotation: AnnotationRepresentation) {
        handleAnnotation(annotation) { visitAnnotation(it, true) }
    }

    fun AnnotationVisitor.writeAnnotation(name: String?, annotation: AnnotationRepresentation) {
        handleAnnotation(annotation) { visitAnnotation(name, it) }
    }

    private fun AnnotationVisitor.visitValue(name: String?, value: AnnotationValueRepresentation) {
        when (value) {
            is AnnotationValueRepresentation.AnnotationValue -> writeAnnotation(name, value.representation)
            is AnnotationValueRepresentation.ClassValue -> visit(name, Type.getType(value.type.value.concreteClass))
            is AnnotationValueRepresentation.EnumValue -> visitEnum(name, Type.getDescriptor(value.type.value.concreteClass), value.entryName)
            is AnnotationValueRepresentation.PrimitiveValue -> visit(name, value.value)
            is AnnotationValueRepresentation.ValueArray -> visitArray(name).run {
                value.elements.forEach { element ->
                    visitValue(null, element)
                }
                visitEnd()
            }
        }
    }

}

private fun MethodVisitor.maybeWithOptInRequirement(optInRequirements: List<AnnotationRepresentation>?) {
    optInRequirements?.forEach { annotation ->
        with(AnnotationUtils) {
            writeAnnotation(annotation)
        }
    }
}

private
fun fragmentsForModelDefault(
    accessor: Accessor.ForModelDefault
): Fragments {

    val accessorSpec = accessor.spec
    val className = internalNameForAccessorClassOf(accessorSpec)
    val (accessibleReceiverType, name, modelType) = accessorSpec
    val projectFeatureName = name.kotlinIdentifier
    val receiverType = accessibleReceiverType.type.kmType
    val (kotlinPublicType, jvmPublicType) = accessibleTypesFor(modelType)
    val deprecation = accessor.spec.type.deprecation()
    val optIns = accessor.spec.type.requiredOptIns()

    return className to sequenceOf(
        AccessorFragment(
            source = modelDefaultAccessor(accessorSpec),
            bytecode = {
                publicStaticMethod(signature) {
                    maybeWithDeprecation(deprecation)
                    maybeWithOptInRequirement(optIns)
                    ALOAD(0)
                    LDC(projectFeatureName)
                    LDC(jvmPublicType)
                    ALOAD(1)
                    INVOKEINTERFACE(GradleTypeName.modeDefaults, "add", "(Ljava/lang/String;Ljava/lang/Class;Lorg/gradle/api/Action;)V")
                    RETURN()
                }
            },
            metadata = {
                kmPackage.functions += newFunctionOf(
                    receiverType = receiverType,
                    returnType = KotlinType.unit,
                    name = projectFeatureName,
                    valueParameters = listOf(
                        newValueParameterOf("configureAction", actionTypeOf(kotlinPublicType))
                    ),
                    signature = signature,
                    functionAttributes = {
                        publicFunctionAttributes()
                        hasAnnotationsIfDeprecated(deprecation)
                        hasAnnotationsIfRequiresOptIn(optIns)
                    }
                )
            },
            signature = JvmMethodSignature(
                name.kotlinIdentifier,
                "(Lorg/gradle/api/initialization/SharedModelDefaults;Lorg/gradle/api/Action;)V"
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

internal fun TypeAccessibility.deprecation(): Deprecated? =
    when (this) {
        is TypeAccessibility.Accessible -> type.value.concreteClass.run {
            (annotations.find { it is Deprecated } as Deprecated?)?.let { Deprecated(it.message, ReplaceWith(""), it.level) }
                ?: (annotations.find { it is java.lang.Deprecated } as java.lang.Deprecated?)?.let { Deprecated("Deprecated in Java") }
        }

        else -> null
    }

internal fun highestDeprecationByLevel(deprecated: Deprecated?, other: Deprecated?): Deprecated? =
    listOfNotNull(deprecated, other).maxByOrNull { it.level }

internal fun TypeAccessibility.requiredOptIns(): List<AnnotationRepresentation>? =
    when (this) {
        is TypeAccessibility.Accessible -> this.optInRequirements.takeIf { it.isNotEmpty() }
        else -> null
    }


private
fun MethodVisitor.invokeAction() {
    INVOKEINTERFACE(GradleTypeName.action, "execute", "(Ljava/lang/Object;)V")
}


private
fun accessibleTypesFor(typeAccessibility: TypeAccessibility): Pair<KmType, InternalName> = when (typeAccessibility) {
    is TypeAccessibility.Accessible -> typeAccessibility.run { type.kmType to internalName() }
    is TypeAccessibility.Inaccessible -> KotlinType.any to InternalNameOf.javaLangObject
}


private
val SchemaType.kmType: KmType
    get() = value.kmType


private
val TypeOf<*>.kmType: KmType
    get() = when {
        isParameterized -> genericTypeOf(
            classOf(parameterizedTypeDefinition.concreteClass),
            actualTypeArguments.map { it.kmTypeProjection }
        )

        isWildcard -> (upperBound ?: lowerBound)?.kmType ?: KotlinType.any
        else -> classOf(concreteClass)
    }

private
val TypeOf<*>.kmTypeProjection: KmTypeProjection
    get() = KmTypeProjection(
        variance = when {
            upperBound != null -> KmVariance.OUT
            lowerBound != null -> KmVariance.IN
            else -> KmVariance.INVARIANT
        },
        type = kmType
    )


internal
inline fun <reified T> classOf(): KmType =
    classOf(T::class.java)


@Suppress("FunctionParameterNaming")
private
fun classOf(`class`: Class<*>) =
    classOf(`class`.internalName)


private
fun classOf(className: InternalName): KmType =
    newClassTypeOf(kotlinNameOf(className))


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


internal
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
