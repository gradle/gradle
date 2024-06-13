package org.gradle.client.demo.mutations

import org.gradle.api.experimental.android.application.AndroidApplication
import org.gradle.api.experimental.android.extensions.testing.Testing
import org.gradle.api.experimental.android.library.AndroidLibrary
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation.IfPresentBehavior
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.NestedObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InAllNestedScopes
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InNestedScopes
import org.gradle.internal.declarativedsl.schemaUtils.*

object SetVersionCodeMutation : MutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.versionCode"
    override val name: String = "Set version code"
    override val description: String = "Update versionCode in androidApplication"

    val versionCodeParam =
        MutationParameter("new version code", "new value for versionCode", MutationParameterKind.IntParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(versionCodeParam)

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.findTypeFor<AndroidApplication>() != null

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            val androidApplication = typeFor<AndroidApplication>()
            val versionCode = androidApplication.propertyNamed("versionCode")

            listOf(
                ModelMutationRequest(
                    ScopeLocation(listOf(InAllNestedScopes, InNestedScopes(NestedObjectsOfType(androidApplication)))),
                    ModelMutation.SetPropertyValue(
                        versionCode,
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
        MutationParameter("new namespace", "new value for versionCode", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(newNamespaceParam)

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.findTypeFor<AndroidLibrary>() != null

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            val androidLibrary = typeFor<AndroidLibrary>()
            val namespace = androidLibrary.propertyNamed("namespace")

            listOf(
                ModelMutationRequest(
                    ScopeLocation(listOf(InAllNestedScopes, InNestedScopes(NestedObjectsOfType(androidLibrary)))),
                    ModelMutation.SetPropertyValue(
                        namespace,
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
    AddDependencyMutation("org.gradle.client.demo.mutations.addDependency.testing") { schema ->
        val androidLibrary = schema.typeFor<AndroidLibrary>()
        val androidTesting = androidLibrary.singleFunctionNamed("testing")
        val testingType = schema.typeFor<Testing>()
        val testingDependencies = testingType.singleFunctionNamed("dependencies")

        ScopeLocation(
            listOf(
                InNestedScopes(NestedObjectsOfType(androidLibrary)),
                InNestedScopes(NestedScopeSelector.ObjectsConfiguredBy(androidTesting)),
                InNestedScopes(NestedScopeSelector.ObjectsConfiguredBy(testingDependencies))
            )
        )
    }

val addTopLevelDependencyMutation =
    AddDependencyMutation("org.gradle.client.demo.mutations.addDependency.topLevel") { schema ->
        val androidLibrary = schema.typeFor<AndroidLibrary>()
        val androidLibraryDependencies = androidLibrary.singleFunctionNamed("dependencies")

        ScopeLocation(
            listOf(
                InNestedScopes(NestedObjectsOfType(androidLibrary)),
                InNestedScopes(NestedScopeSelector.ObjectsConfiguredBy(androidLibraryDependencies))
            )
        )
    }

class AddDependencyMutation(override val id: String, private val scopeLocation: (AnalysisSchema) -> ScopeLocation) :
    MutationDefinition {
    override val name: String = "Add a dependency"
    override val description: String = "Add a dependency to the dependencies block"

    val dependencyCoordinatesParam =
        MutationParameter(
            "dependency coordinates",
            "coordinates of the dependency to add",
            MutationParameterKind.StringParameter
        )

    override val parameters: List<MutationParameter<*>>
        get() = listOf(dependencyCoordinatesParam)

    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.findTypeFor<AndroidLibrary>() != null

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    scopeLocation(projectAnalysisSchema),
                    ModelMutation.AddNewElement(
                        NewElementNodeProvider.ArgumentBased { args ->
                            elementFromString("implementation(\"" + args[dependencyCoordinatesParam] + "\")")!!
                        }
                    ),
                )
            )
        }
} 