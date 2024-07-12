package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed

val AnalysisSchema.lintEnabled: TypedMember.TypedProperty
    get() = typeByFqn("org.gradle.api.experimental.common.extensions.Lint").propertyNamed("enabled")

val AnalysisSchema.hasLibraryDependencies: DataClass
    get() = typeByFqn("org.gradle.api.experimental.common.HasLibraryDependencies")

val AnalysisSchema.hasApplicationDependencies: DataClass
    get() = typeByFqn("org.gradle.api.experimental.common.HasApplicationDependencies")

fun AnalysisSchema.hasCommonPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.common.LibraryDependencies" }

interface CommonPrototypeMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasCommonPrototype()
}

class AddDependencyMutation(
    override val id: String,
    private val dependenciesOwnerScope: AnalysisSchema.() -> ScopeLocation,
    private val dependenciesConfiguringFunction: AnalysisSchema.() -> TypedMember.TypedFunction,
) : CommonPrototypeMutationDefinition {
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
        val scopeForDependenciesBlock = dependenciesOwnerScope(projectAnalysisSchema)
        val dependenciesFunction = dependenciesConfiguringFunction(projectAnalysisSchema)

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
