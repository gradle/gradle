package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed

val AnalysisSchema.lintEnabled: TypedMember.TypedProperty
    get() = typeByFqn("org.gradle.api.experimental.common.extensions.Lint").propertyNamed("enabled")