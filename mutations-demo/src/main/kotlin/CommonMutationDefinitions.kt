package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

val addCommonLibraryDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.toLibrary",
        { hasCommonPrototype() },
        { ScopeLocation.fromTopLevel().inObjectsOfType(hasLibraryDependencies) },
        { hasLibraryDependencies.singleFunctionNamed("dependencies") }
    )

val addCommonApplicationDependencyMutation =
    AddDependencyMutation(
        "org.gradle.client.demo.mutations.addDependency.toApplication",
        { hasCommonPrototype() },
        { ScopeLocation.fromTopLevel().inObjectsOfType(hasApplicationDependencies) },
        { hasApplicationDependencies.singleFunctionNamed("dependencies") }
    )

abstract class EnableLintMutation(
    val lintOwner: AnalysisSchema.() -> DataClass,
    val lintFunction: AnalysisSchema.() -> TypedMember.TypedFunction,
    val lintEnabledProperty: AnalysisSchema.() -> TypedMember.TypedProperty
) : MutationDefinition {
    override val name: String = "Enable Lint"
    override val description: String = "Enable linting for this software"

    override val parameters: List<MutationParameter<*>> =
        emptyList()

    override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
        with(projectAnalysisSchema) {
            listOf(
                ModelMutationRequest(
                    ScopeLocation.fromTopLevel().inObjectsOfType(lintOwner()),
                    ModelMutation.AddConfiguringBlockIfAbsent(lintFunction())
                ),
                ModelMutationRequest(
                    ScopeLocation.fromTopLevel().inObjectsOfType(lintOwner()).inObjectsConfiguredBy(lintFunction()),
                    ModelMutation.SetPropertyValue(
                        lintEnabledProperty(),
                        NewValueNodeProvider.Constant(valueFromString("true")!!)
                    )
                )
            )
        }
}

val addTestingDependencyMutations: List<MutationDefinition> = run {
    fun mutationForOwner(
        idSuffix: String,
        isCompatible: AnalysisSchema.() -> Boolean,
        testingOwnerType: AnalysisSchema.() -> DataClass,
        testingType: AnalysisSchema.() -> DataClass
    ) = run {
        val addDependency = AddDependencyMutation(
            "org.gradle.client.demo.mutations.addDependency.toTesting.$idSuffix",
            { isCompatible() },
            { ScopeLocation.fromTopLevel().inObjectsOfType(testingOwnerType()).inObjectsOfType(testingType()) },
            { testingType().singleFunctionNamed("dependencies") }
        )

        object : MutationDefinition by addDependency {
            override val name: String
                get() = "Add a test dependency"

            override fun defineModelMutationSequence(projectAnalysisSchema: AnalysisSchema): List<ModelMutationRequest> =
                with(projectAnalysisSchema) {
                    listOf(
                        ModelMutationRequest(
                            ScopeLocation.inAnyScope().inObjectsOfType(testingOwnerType()),
                            ModelMutation.AddConfiguringBlockIfAbsent(testingOwnerType().singleFunctionNamed("testing"))
                        )
                    ) + addDependency.defineModelMutationSequence(projectAnalysisSchema)
                }
        }
    }

    listOf(
        mutationForOwner("javaLibrary", { hasJavaPrototype() }, { javaLibrary }, { javaTesting }),
        mutationForOwner("javaApplication", { hasJavaPrototype() }, { javaApplication }, { javaTesting }),
        mutationForOwner("kotlinLibrary", { hasKotlinPrototype() }, { kotlinJvmLibrary }, { kotlinTesting }),
        mutationForOwner("kotlinApplication", { hasKotlinPrototype() }, { kotlinJvmApplication }, { kotlinTesting })
    )
}