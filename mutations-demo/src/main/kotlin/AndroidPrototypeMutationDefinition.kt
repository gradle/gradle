package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition

interface AndroidPrototypeMutationDefinition : MutationDefinition {
    override fun isCompatibleWithSchema(projectAnalysisSchema: AnalysisSchema): Boolean =
        projectAnalysisSchema.hasAndroidPrototype()
}