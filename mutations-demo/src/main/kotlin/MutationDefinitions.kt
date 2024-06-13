package org.gradle.client.demo.mutations

import org.gradle.api.experimental.android.application.AndroidApplication
import org.gradle.api.experimental.android.extensions.testing.Testing
import org.gradle.api.experimental.android.library.AndroidLibrary
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation.IfPresentBehavior
import org.gradle.internal.declarativedsl.schemaUtils.findTypeFor
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed
import org.gradle.internal.declarativedsl.schemaUtils.typeFor

object SetVersionCodeMutation : MutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.versionCode"
    override val name: String = "Set version code"
    override val description: String = "Update versionCode in androidApplication"

    val versionCodeParam =
        MutationParameter("New version code", "New value for versionCode", MutationParameterKind.IntParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(versionCodeParam)

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.findTypeFor<AndroidApplication>() != null

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            val androidApplication = typeFor<AndroidApplication>()

            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(androidApplication),
                    ModelMutation.SetPropertyValue(
                        androidApplication.property("versionCode"),
                        NewValueNodeProvider.ArgumentBased { args -> valueFromString("" + args[versionCodeParam])!! },
                        IfPresentBehavior.Overwrite
                    ),
                )
            )
        }
}

object SetNamespaceMutation : MutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.namespace"
    override val name: String = "Set the library namespace"
    override val description: String = "Updates the namespace in an Android library"

    val newNamespaceParam =
        MutationParameter("New namespace", "New value for the namespace", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(newNamespaceParam)

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.findTypeFor<AndroidLibrary>() != null

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            val androidLibrary = typeFor<AndroidLibrary>()

            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(androidLibrary),
                    ModelMutation.SetPropertyValue(
                        androidLibrary.property("namespace"),
                        NewValueNodeProvider.ArgumentBased { args ->
                            valueFromString("\"" + args[newNamespaceParam] + "\"")!!
                        },
                        IfPresentBehavior.Overwrite
                    ),
                )
            )
        }
}

val addTestingDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.testing"
    ) {
        val androidLibrary = typeFor<AndroidLibrary>()

        ScopeLocation.fromTopLevel()
            .inObjectsOfType(androidLibrary)
            .inObjectsConfiguredBy(androidLibrary.singleFunctionNamed("testing"))
            .inObjectsConfiguredBy(typeFor<Testing>().singleFunctionNamed("dependencies"))
    }

val addTopLevelDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.topLevel"
    ) {
        val androidLibrary = typeFor<AndroidLibrary>()

        ScopeLocation.fromTopLevel()
            .inObjectsOfType(androidLibrary)
            .inObjectsConfiguredBy(androidLibrary.singleFunctionNamed("dependencies"))
    }

class AddDependencyMutation(override val id: String, private val scopeLocation: AnalysisSchema.() -> ScopeLocation) :
    MutationDefinition {
    override val name: String = "Add a dependency"
    override val description: String = "Add a dependency to the dependencies block"

    val dependencyCoordinatesParam =
        MutationParameter(
            "Dependency coordinates",
            "Coordinates of the dependency to add",
            MutationParameterKind.StringParameter
        )

    override val parameters: List<MutationParameter<*>>
        get() = listOf(dependencyCoordinatesParam)

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.findTypeFor<AndroidLibrary>() != null

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        listOf(
            ModelMutationRequest(
                scopeLocation(projectAnalysisSchema),
                ModelMutation.AddNewElement(
                    NewElementNodeProvider.ArgumentBased { args ->
                        elementFromString("implementation(\"" + args[dependencyCoordinatesParam] + "\")")!!
                    }
                )
            )
        )
}
