package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass

fun AnalysisSchema.hasJavaPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.java.JavaLibrary" }

val AnalysisSchema.hasJvmApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.jvm.HasJvmApplication")

val AnalysisSchema.javaLibrary: DataClass
    get() = typeByFqn("org.gradle.api.experimental.java.JavaLibrary")

val AnalysisSchema.javaApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.java.JavaApplication")

val AnalysisSchema.hasJavaTarget: DataClass
    get() = typeByFqn("org.gradle.api.experimental.jvm.HasJavaTarget")

val AnalysisSchema.javaTesting: DataClass
    get() = typeByFqn("org.gradle.api.experimental.jvm.extensions.testing.Testing")