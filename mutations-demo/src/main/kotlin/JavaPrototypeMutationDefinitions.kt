package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed

interface JavaPrototypeMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasJavaPrototype()
}

object SetJvmApplicationMainClass : JavaPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.jvm.application.setMainClass"
    override val name: String = "Set application main class"
    override val description: String = "Set the entry point class for the application"

    val mainClassNameParameter =
        MutationParameter("Main class name", "Fully qualified class name", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>> = listOf(mainClassNameParameter)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(hasJvmApplication),
                    ModelMutation.SetPropertyValue(
                        hasJvmApplication.propertyNamed("mainClass"),
                        NewValueNodeProvider.ArgumentBased { valueFromString("\"${it[mainClassNameParameter]}\"")!! }
                    )
                )
            )
        }
}

object SetJavaVersion : JavaPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.jvm.setJavaVersion"
    override val name: String = "Set Java version"
    override val description: String = "Set the target Java version"

    val javaVersionParameter =
        MutationParameter("New Java version", "The major Java version", MutationParameterKind.IntParameter)

    override val parameters: List<MutationParameter<*>> = listOf(javaVersionParameter)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(hasJavaTarget),
                    ModelMutation.SetPropertyValue(
                        hasJavaTarget.propertyNamed("javaVersion"),
                        NewValueNodeProvider.ArgumentBased { valueFromString("${it[javaVersionParameter]}")!! }
                    )
                )
            )
        }
}