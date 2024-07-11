package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

interface AndroidPrototypeMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasAndroidPrototype()
}

object EnableLintMutation : AndroidPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.lintEnable"
    override val name: String = "Enable Lint"
    override val description: String = "Enable linting for Android"

    override val parameters: List<MutationParameter<*>> =
        emptyList()

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.fromTopLevel().inObjectsOfType(androidSoftware),
                    ModelMutation.AddConfiguringBlockIfAbsent(androidLint)
                ),
                ModelMutationRequest(
                    ScopeLocation.fromTopLevel().inObjectsOfType(androidSoftware).inObjectsConfiguredBy(androidLint),
                    ModelMutation.SetPropertyValue(
                        androidLintEnabled,
                        NewValueNodeProvider.Constant(valueFromString("true")!!)
                    )
                )
            )
        }
}

object SetVersionCodeMutation : AndroidPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.versionCode"
    override val name: String = "Set version code"
    override val description: String = "Update versionCode in androidApplication"

    val versionCodeParam =
        MutationParameter("New version code", "New value for versionCode", MutationParameterKind.IntParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(versionCodeParam)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(androidApplication),
                    ModelMutation.SetPropertyValue(
                        androidApplication.propertyNamed("versionCode"),
                        NewValueNodeProvider.ArgumentBased { args -> valueFromString("" + args[versionCodeParam])!! }
                    ),
                )
            )
        }
}

object SetNamespaceMutation : AndroidPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.namespace"
    override val name: String = "Set the namespace"
    override val description: String = "Updates the namespace in an Android project"

    val newNamespaceParam =
        MutationParameter("New namespace", "New value for the namespace", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(newNamespaceParam)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(androidSoftware),
                    ModelMutation.SetPropertyValue(
                        androidSoftware.propertyNamed("namespace"),
                        NewValueNodeProvider.ArgumentBased { args ->
                            valueFromString("\"" + args[newNamespaceParam] + "\"")!!
                        }
                    ),
                )
            )
        }
}

val addTestingDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.testing",
        { testing.singleFunctionNamed("dependencies") },
        {
            ScopeLocation.fromTopLevel()
                .inObjectsOfType(androidSoftware)
                .inObjectsConfiguredBy(androidSoftware.singleFunctionNamed("testing"))
        }
    )

val addLibraryDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.topLevel.library",
        { androidLibrary.singleFunctionNamed("dependencies") },
        { ScopeLocation.fromTopLevel().inObjectsOfType(androidLibrary) }
    )

val addApplicationDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.topLevel.application",
        { androidApplication.singleFunctionNamed("dependencies") },
        { ScopeLocation.fromTopLevel().inObjectsOfType(androidApplication) }
    )

class AddDependencyMutation(
    override val id: String,
    private val dependenciesOwnerFunction: AnalysisSchema.() -> TypedMember.TypedFunction,
    private val dependenciesScope: AnalysisSchema.() -> ScopeLocation,
) : AndroidPrototypeMutationDefinition {
    override val name: String = "Add a dependency"
    override val description: String = "Add a dependency to the dependencies block"

    val dependencyCoordinatesParam =
        MutationParameter(
            "Dependency to add",
            "Maven coordinates",
            MutationParameterKind.StringParameter
        )

    override val parameters: List<MutationParameter<*>>
        get() = listOf(dependencyCoordinatesParam)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> {
        val scopeForDependenciesBlock = dependenciesScope(projectAnalysisSchema)
        val dependenciesFunction = dependenciesOwnerFunction(projectAnalysisSchema)

        return listOf(
            ModelMutationRequest(
                scopeForDependenciesBlock,
                ModelMutation.AddConfiguringBlockIfAbsent(dependenciesFunction)
            ),
            ModelMutationRequest(
                scopeForDependenciesBlock.inObjectsConfiguredBy(dependenciesFunction),
                ModelMutation.AddNewElement(
                    NewElementNodeProvider.ArgumentBased { args ->
                        elementFromString("implementation(\"" + args[dependencyCoordinatesParam] + "\")")!!
                    }
                )
            )
        )
    }
}
