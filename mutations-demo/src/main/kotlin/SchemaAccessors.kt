package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass

fun AnalysisSchema.hasAndroidPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.android.AndroidSoftware" }

val AnalysisSchema.androidApplication: DataClass
    get() = dataClassesByFqName.entries.single {
        it.key.qualifiedName == "org.gradle.api.experimental.android.application.AndroidApplication"
    }.value

val AnalysisSchema.androidLibrary: DataClass
    get() = dataClassesByFqName.entries.single {
        it.key.qualifiedName == "org.gradle.api.experimental.android.library.AndroidLibrary"
    }.value

val AnalysisSchema.androidSoftware: DataClass
    get() = dataClassesByFqName.entries.single {
        it.key.qualifiedName == "org.gradle.api.experimental.android.AndroidSoftware"
    }.value

val AnalysisSchema.testing: DataClass
    get() = dataClassesByFqName.entries.single {
        it.key.qualifiedName == "org.gradle.api.experimental.android.extensions.testing.Testing"
    }.value