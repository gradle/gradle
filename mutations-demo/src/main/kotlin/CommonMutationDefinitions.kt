package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.*

abstract class EnableLintMutation(
    val lintOwner: AnalysisSchema.() -> DataClass,
    val lintFunction: AnalysisSchema.() -> TypedMember.TypedFunction,
    val lintEnabledProperty: AnalysisSchema.() -> TypedMember.TypedProperty
): MutationDefinition {
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
