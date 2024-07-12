package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

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

data class KotlinTargetInfo(
    val name: String,
    val targetAccessor: AnalysisSchema.() -> TypedMember.TypedFunction
)

data class KotlinSoftwareInfo(
    val kindName: String,
    val softwareTypeClass: AnalysisSchema.() -> DataClass,
    val targetsFunction: AnalysisSchema.() -> TypedMember.TypedFunction,
    val targetCommonSupertype: AnalysisSchema.() -> DataClass,
    val targets: List<KotlinTargetInfo>,
)

val kotlinSoftwareInfos = listOf(
    KotlinSoftwareInfo(
        "library", { kmpLibrary }, { kmpLibraryTargetsFunction }, { kmpLibraryTarget }, listOf(
            KotlinTargetInfo("JVM") { kmpLibraryTargetsJvmFunction },
            KotlinTargetInfo("Node.js") { kmpLibraryTargetsNodeJsFunction },
            KotlinTargetInfo("macOS ARM64") { kmpLibraryTargetsMacOsArm64Function },
        )
    ),
    KotlinSoftwareInfo(
        "application", { kmpApplication }, { kmpApplicationTargetsFunction }, { kmpApplicationTarget }, listOf(
            KotlinTargetInfo("JVM") { kmpApplicationTargetsJvmFunction },
            KotlinTargetInfo("Node.js") { kmpApplicationTargetsNodeJsFunction },
            KotlinTargetInfo("macOS ARM64") { kmpApplicationTargetsMacOsArm64Function },
        )
    ),
)

val kmpAddTargetMutations = kotlinSoftwareInfos.flatMap { softwareType ->
    softwareType.targets.map { target ->
        AddKotlinTargetMutation(
            softwareType.kindName,
            target.name,
            softwareType.softwareTypeClass,
            softwareType.targetsFunction,
            target.targetAccessor
        )
    }
}

val kmpAddDependencyMutations = kotlinSoftwareInfos.flatMap { softwareType ->
    softwareType.targets.map { target ->
        AddDependencyMutation(
            "org.gradle.client.demo.mutations.addDependency.kmp.${softwareType.kindName}.${target.name}",
            {
                with(softwareType) {
                    with(target) {
                        ScopeLocation.inAnyScope().inObjectsOfType(softwareTypeClass())
                            .inObjectsConfiguredBy(targetsFunction())
                            .inObjectsConfiguredBy(targetAccessor())
                    }
                }
            },
            {
                with(softwareType) {
                    targetCommonSupertype().singleFunctionNamed("dependencies")
                }
            }
        )
    }
}

object SetKmpJvmApplicationMainClass : KotlinPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.kmpJvm.application.setMainClass"
    override val name: String = "Set application main class"
    override val description: String = "Set the entry point class for the application"

    val mainClassNameParameter =
        MutationParameter("Main class name", "Fully qualified class name", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>> = listOf(mainClassNameParameter)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(kmpApplicationTargetsJvm),
                    ModelMutation.SetPropertyValue(
                        kmpApplicationTargetsJvm.propertyNamed("mainClass"),
                        NewValueNodeProvider.ArgumentBased { valueFromString("\"${it[mainClassNameParameter]}\"")!! }
                    )
                )
            )
        }
}

object SetKmpJvmApplicationJdkVersion : KotlinPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.kmpJvm.application.setJdkVersion"
    override val name: String = "Set JDK version"
    override val description: String = "Set the JDK version for target"

    val mainClassNameParameter =
        MutationParameter("New JDK version", "The Java major version, e.g. 21", MutationParameterKind.IntParameter)

    override val parameters: List<MutationParameter<*>> = listOf(mainClassNameParameter)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(kmpApplicationTargetsJvm),
                    ModelMutation.SetPropertyValue(
                        kmpApplicationTargetsJvm.propertyNamed("jdkVersion"),
                        NewValueNodeProvider.ArgumentBased { valueFromString("${it[mainClassNameParameter]}")!! }
                    )
                )
            )
        }
}

object SetKmpJvmLibraryJdkVersion : KotlinPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.kmpJvm.library.setJdkVersion"
    override val name: String = "Set JDK version"
    override val description: String = "Set the JDK version for target"

    val mainClassNameParameter =
        MutationParameter("New JDK version", "The Java major version, e.g. 21", MutationParameterKind.IntParameter)

    override val parameters: List<MutationParameter<*>> = listOf(mainClassNameParameter)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(kmpLibraryTargetsJvm),
                    ModelMutation.SetPropertyValue(
                        kmpLibraryTargetsJvm.propertyNamed("jdkVersion"),
                        NewValueNodeProvider.ArgumentBased { valueFromString("${it[mainClassNameParameter]}")!! }
                    )
                )
            )
        }
}
