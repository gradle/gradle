package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

fun AnalysisSchema.hasKotlinPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.kotlin.KotlinJvmLibrary" }

val AnalysisSchema.kotlinJvmLibrary: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kotlin.KotlinJvmLibrary")

val AnalysisSchema.kmpLibrary: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpLibrary")

val AnalysisSchema.kmpApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplication")

val AnalysisSchema.kmpLibraryTargets: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpLibraryTargetContainer")

val AnalysisSchema.kmpLibraryTargetsFunction: TypedMember.TypedFunction
    get() = kmpLibrary.singleFunctionNamed("targets")

val AnalysisSchema.kmpApplicationTargets: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplicationTargetContainer")

val AnalysisSchema.kmpApplicationTargetsFunction: TypedMember.TypedFunction
    get() = kmpApplication.singleFunctionNamed("targets")

val AnalysisSchema.kmpLibraryTargetsJvm: TypedMember.TypedFunction
    get() = kmpLibraryTargets.singleConfiguringFunctionNamed("jvm")

val AnalysisSchema.kmpLibraryTargetsNodeJs: TypedMember.TypedFunction
    get() = kmpLibraryTargets.singleConfiguringFunctionNamed("nodeJs")

val AnalysisSchema.kmpLibraryTargetsMacOsArm64: TypedMember.TypedFunction
    get() = kmpLibraryTargets.singleConfiguringFunctionNamed("macOsArm64")

val AnalysisSchema.kmpApplicationTargetsJvm: TypedMember.TypedFunction
    get() = kmpApplicationTargets.singleConfiguringFunctionNamed("jvm")

val AnalysisSchema.kmpApplicationTargetsNodeJs: TypedMember.TypedFunction
    get() = kmpApplicationTargets.singleConfiguringFunctionNamed("nodeJs")

val AnalysisSchema.kmpApplicationTargetsMacOsArm64: TypedMember.TypedFunction
    get() = kmpApplicationTargets.singleConfiguringFunctionNamed("macOsArm64")

val AnalysisSchema.kotlinJvmLibraryLint: TypedMember.TypedFunction
    get() = kotlinJvmLibrary.singleFunctionNamed("lint")
