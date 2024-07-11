package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember
import org.gradle.internal.declarativedsl.schemaUtils.propertyNamed
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

fun AnalysisSchema.hasAndroidPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.android.AndroidSoftware" }

val AnalysisSchema.androidApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.android.application.AndroidApplication")

val AnalysisSchema.androidLibrary: DataClass
    get() = typeByFqn("org.gradle.api.experimental.android.library.AndroidLibrary")

val AnalysisSchema.androidSoftware: DataClass
    get() = typeByFqn("org.gradle.api.experimental.android.AndroidSoftware")

val AnalysisSchema.testing: DataClass
    get() = typeByFqn("org.gradle.api.experimental.android.extensions.testing.Testing")

val AnalysisSchema.androidLint: TypedMember.TypedFunction
    get() = androidSoftware.singleFunctionNamed("lint")
