package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass

fun AnalysisSchema.hasAgp(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "com.android.build.gradle.LibraryExtensionInternal" }

val AnalysisSchema.agpAndroidLibrary: DataClass
    get() = typeByFqn("com.android.build.gradle.LibraryExtensionInternal")

val AnalysisSchema.dependenciesExtension: DataClass
    get() = typeByFqn("com.android.build.gradle.internal.DependenciesExtension")

val AnalysisSchema.buildFeatures: DataClass
    get() = typeByFqn("com.android.build.api.dsl.BuildFeatures")