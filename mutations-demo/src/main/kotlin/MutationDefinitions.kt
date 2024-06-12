package org.gradle.client.demo.mutations

import org.gradle.api.experimental.android.application.AndroidApplication
import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.*
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutation.IfPresentBehavior
import org.gradle.internal.declarativedsl.dom.mutation.NestedScopeSelector.NestedObjectsOfType
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InAllNestedScopes
import org.gradle.internal.declarativedsl.dom.mutation.ScopeLocationElement.InNestedScopes
import org.gradle.internal.declarativedsl.schemaUtils.findTypeFor
import org.gradle.internal.declarativedsl.schemaUtils.typeFor

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
            val versionCode = androidApplication.propertyFromGetter(AndroidApplication::getVersionCode)

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