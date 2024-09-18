package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation.SetPropertyValue
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

interface AgpMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasAgp()
}

val agpAddDependency = AddDependencyMutation(
    "org.gradle.client.demo.mutations.addDependency.agp.implementation",
    AnalysisSchema::hasAgp,
    { ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary) },
    { agpAndroidLibrary.singleFunctionNamed("dependenciesDcl") }
)

val agpAddTestDependency = run {
    val mutation = AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.agp.testImplementation",
        AnalysisSchema::hasAgp,
        { ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary) },
        { agpAndroidLibrary.singleFunctionNamed("dependenciesDcl") },
        { notation -> elementFromString("testImplementation(\"${notation.value}\")") }
    )

    object : MutationDefinition by mutation {
        override val name: String
            get() = "Add a test dependency"
    }
}

val agpAddAndroidTestDependency = run {
    val mutation = AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.agp.androidTestImplementation",
        AnalysisSchema::hasAgp,
        { ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary) },
        { agpAndroidLibrary.singleFunctionNamed("dependenciesDcl") },
        { notation -> elementFromString("androidTestImplementation(\"${notation.value}\")") }
    )

    object : MutationDefinition by mutation {
        override val name: String
            get() = "Add a device test dependency"
    }
}

val enableCompose = run {
    val addComposeDependency = AddDependencyMutation(
        "",
        AnalysisSchema::hasAgp,
        { ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary) },
        { agpAndroidLibrary.singleFunctionNamed("dependenciesDcl") },
        { _ -> elementFromString("implementation(\"androidx.compose.material3:material3:1.3.0\")") }
    )

    object : AgpMutationDefinition {
        override val name: String
            get() = "Enable Compose"

        override val description: String
            get() = "Enable Jetpack Compose in this project"

        override val id: String
            get() = "org.gradle.client.demo.mutations.agp.compose"

        override val parameters: List<MutationParameter<*>>
            get() = emptyList()

        override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema) = with(projectAnalysisSchema) {
            addComposeDependency.defineModelMutationSequence(projectAnalysisSchema) + listOf(
                ModelMutationRequest(
                    ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary),
                    ModelMutation.AddConfiguringBlockIfAbsent(
                        agpAndroidLibrary.singleFunctionNamed("buildFeatures")
                    )
                ),
                ModelMutationRequest(
                    ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary)
                        .inObjectsConfiguredBy(agpAndroidLibrary.singleFunctionNamed("buildFeatures")),
                    SetPropertyValue(
                        buildFeatures.propertyNamed("compose"),
                        NewValueNodeProvider.Constant(valueFromString("true")!!)
                    )
                )
            )
        }
    }
}

val addBuildConfigField = object : AgpMutationDefinition {
    override val id: String
        get() = "org.gradle.client.demo.mutations.agp.buildConfig.addField"
    override val description: String
        get() = "Add a field to the build config"
    override val name: String
        get() = "Add a build config field"

    private val typeArg =
        MutationParameter("Field type", "build config field type", MutationParameterKind.StringParameter)
    private val keyArg = MutationParameter("Field key", "field key", MutationParameterKind.StringParameter)
    private val valueArg = MutationParameter("Field value", "field value", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>>
        get() {
            return listOf(
                typeArg,
                keyArg,
                valueArg
            )
        }

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema) = with(projectAnalysisSchema) {
        listOf(
            ModelMutationRequest(
                ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary),
                ModelMutation.AddConfiguringBlockIfAbsent(
                    agpAndroidLibrary.singleFunctionNamed("buildFeatures")
                )
            ),
            ModelMutationRequest(
                ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary)
                    .inObjectsConfiguredBy(agpAndroidLibrary.singleFunctionNamed("buildFeatures")),
                SetPropertyValue(
                    buildFeatures.propertyNamed("buildConfig"),
                    NewValueNodeProvider.Constant(valueFromString("true")!!)
                )
            ),
            ModelMutationRequest(
                ScopeLocation.fromTopLevel().inObjectsOfType(agpAndroidLibrary)
                    .inObjectsConfiguredBy(agpAndroidLibrary.singleFunctionNamed("defaultConfig")),
                ModelMutation.AddNewElement(NewElementNodeProvider.ArgumentBased { args ->
                    val type = args[typeArg]
                    val key = args[keyArg]
                    val value = args[valueArg]
                    elementFromString("buildConfigField(\"$type\", \"$key\", \"$value\")")!!
                })
            )
        )
    }
}

val setAgpNamespaceMutation = object : AgpMutationDefinition {
    override val id: String = "org.gradle.client.demo.mutations.agp.namespace"
    override val name: String = "Set the namespace"
    override val description: String = "Update the namespace in the project"

    private val newNamespaceParam =
        MutationParameter("New namespace", "New value for the namespace", MutationParameterKind.StringParameter)

    override val parameters: List<MutationParameter<*>>
        get() = listOf(newNamespaceParam)

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.inAnyScope().inObjectsOfType(agpAndroidLibrary),
                    SetPropertyValue(
                        agpAndroidLibrary.propertyNamed("namespace"),
                        NewValueNodeProvider.ArgumentBased { args ->
                            valueFromString("\"" + args[newNamespaceParam] + "\"")!!
                        }
                    ),
                )
            )
        }
}
