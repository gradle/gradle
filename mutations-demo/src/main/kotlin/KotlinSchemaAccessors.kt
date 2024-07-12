package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass
import org.gradle.internal.declarativedsl.dom.mutation.TypedMember
import org.gradle.internal.declarativedsl.schemaUtils.singleFunctionNamed

fun AnalysisSchema.hasKotlinPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.kotlin.KotlinJvmLibrary" }

val AnalysisSchema.kotlinJvmLibrary: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kotlin.KotlinJvmLibrary")

val AnalysisSchema.kotlinJvmApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kotlin.KotlinJvmApplication")

val AnalysisSchema.kmpLibrary: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpLibrary")

val AnalysisSchema.kmpLibraryTarget: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpLibraryTarget")

val AnalysisSchema.kmpApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplication")

val AnalysisSchema.kmpApplicationTarget: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplicationTarget")

val AnalysisSchema.kmpLibraryTargets: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpLibraryTargetContainer")

val AnalysisSchema.kmpLibraryTargetsFunction: TypedMember.TypedFunction
    get() = kmpLibrary.singleFunctionNamed("targets")

val AnalysisSchema.kotlinTesting: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kotlin.testing.Testing")

val AnalysisSchema.kmpApplicationTargets: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplicationTargetContainer")

val AnalysisSchema.kmpApplicationTargetsFunction: TypedMember.TypedFunction
    get() = kmpApplication.singleFunctionNamed("targets")

val AnalysisSchema.kmpApplicationTargetsJvm: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplicationJvmTarget")

val AnalysisSchema.kmpApplicationTargetsNative: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpApplicationNativeTarget")

val AnalysisSchema.kmpLibraryTargetsJvm: DataClass
    get() = typeByFqn("org.gradle.api.experimental.kmp.KmpLibraryJvmTarget")

val AnalysisSchema.kmpLibraryTargetsJvmFunction: TypedMember.TypedFunction
    get() = kmpLibraryTargets.singleConfiguringFunctionNamed("jvm")

val AnalysisSchema.kmpLibraryTargetsNodeJsFunction: TypedMember.TypedFunction
    get() = kmpLibraryTargets.singleConfiguringFunctionNamed("nodeJs")

val AnalysisSchema.kmpLibraryTargetsMacOsArm64Function: TypedMember.TypedFunction
    get() = kmpLibraryTargets.singleConfiguringFunctionNamed("macOsArm64")

val AnalysisSchema.kmpApplicationTargetsJvmFunction: TypedMember.TypedFunction
    get() = kmpApplicationTargets.singleConfiguringFunctionNamed("jvm")

val AnalysisSchema.kmpApplicationTargetsNodeJsFunction: TypedMember.TypedFunction
    get() = kmpApplicationTargets.singleConfiguringFunctionNamed("nodeJs")

val AnalysisSchema.kmpApplicationTargetsMacOsArm64Function: TypedMember.TypedFunction
    get() = kmpApplicationTargets.singleConfiguringFunctionNamed("macOsArm64")

val AnalysisSchema.kotlinJvmLibraryLint: TypedMember.TypedFunction
    get() = kotlinJvmLibrary.singleFunctionNamed("lint")
