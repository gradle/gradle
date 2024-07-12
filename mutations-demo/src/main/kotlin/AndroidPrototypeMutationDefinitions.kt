package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

interface AndroidPrototypeMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasAndroidPrototype()
}

object EnableAndroidLintMutation :
    EnableLintMutation({ androidSoftware }, { androidLint }, { lintEnabled }),
    AndroidPrototypeMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.lintEnable.android"
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

val addTestingDependencyMutation = run {
    val addDependency = AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.testing",
        {
            ScopeLocation.fromTopLevel()
                .inObjectsOfType(androidSoftware)
                .inObjectsConfiguredBy(androidSoftware.singleFunctionNamed("testing"))
        },
        { testing.singleFunctionNamed("dependencies") }
    )
    
    object : MutationDefinition by addDependency {
        override val name: String
            get() = "Add a test dependency"

        override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
            with(projectAnalysisSchema) {
                listOf(
                    ModelMutationRequest(
                        ScopeLocation.fromTopLevel().inObjectsOfType(androidSoftware),
                        ModelMutation.AddConfiguringBlockIfAbsent(androidSoftware.singleFunctionNamed("testing"))
                    )
                ) + addDependency.defineModelMutationSequence(projectAnalysisSchema)
            }
    }
}


val addLibraryDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.topLevel.library",
        { ScopeLocation.fromTopLevel().inObjectsOfType(androidLibrary) },
        { androidLibrary.singleFunctionNamed("dependencies") }
    )

val addApplicationDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.topLevel.application",
        { ScopeLocation.fromTopLevel().inObjectsOfType(androidApplication) },
        { androidApplication.singleFunctionNamed("dependencies") }
    )