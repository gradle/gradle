package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.*

interface KotlinPrototypeMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasKotlinPrototype()
}

object EnableKotlinLintMutation :
    EnableLintMutation({ kotlinJvmLibrary }, { kotlinJvmLibraryLint }, { lintEnabled }),
    KotlinPrototypeMutationDefinition {

    override val id: String = "org.gradle.client.demo.mutations.lintEnable.kotlinJvm"
}


class AddKotlinTargetMutation(
    owner: String,
    targetName: String,
    private val targetContainerOwner: AnalysisSchema.() -> DataClass,
    private val targetContainer: AnalysisSchema.() -> TypedMember.TypedFunction,
    private val targetFunction: AnalysisSchema.() -> TypedMember.TypedFunction,
) : KotlinPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.kotlin.$owner.target.add.$targetName"
    override val name: String = "Add $targetName target"
    override val description: String = "Add $targetName KMP target"
    override val parameters: List<MutationParameter<*>> = emptyList()

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(targetContainerOwner()),
                    ModelMutation.AddConfiguringBlockIfAbsent(targetContainer())
                ),
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(targetContainerOwner())
                        .inObjectsConfiguredBy(targetContainer()),
                    ModelMutation.AddConfiguringBlockIfAbsent(targetFunction())
                )
            )
        }
}

val addKmpLibraryJvmTarget = AddKotlinTargetMutation(
    "library",
    "JVM",
    { kmpLibrary },
    { kmpLibraryTargetsFunction },
    { kmpLibraryTargetsJvm }
)

val addKmpLibraryNodeJsTarget = AddKotlinTargetMutation(
    "library",
    "Node.js",
    { kmpLibrary },
    { kmpLibraryTargetsFunction },
    { kmpLibraryTargetsNodeJs })

val addKmpLibraryMacosArm64Target = AddKotlinTargetMutation(
    "library",
    "macOS ARM64",
    { kmpLibrary },
    { kmpLibraryTargetsFunction },
    { kmpLibraryTargetsMacOsArm64 })

val addKmpApplicationJvmTarget = AddKotlinTargetMutation(
    "application",
    "JVM",
    { kmpApplication },
    { kmpApplicationTargetsFunction },
    { kmpApplicationTargetsJvm })

val addKmpApplicationNodeJsTarget = AddKotlinTargetMutation(
    "application",
    "Node.js",
    { kmpApplication },
    { kmpApplicationTargetsFunction },
    { kmpApplicationTargetsNodeJs })

val addKmpApplicationMacosArm64Target = AddKotlinTargetMutation(
    "application",
    "macOS ARM64",
    { kmpApplication },
    { kmpApplicationTargetsFunction },
    { kmpApplicationTargetsMacOsArm64 })