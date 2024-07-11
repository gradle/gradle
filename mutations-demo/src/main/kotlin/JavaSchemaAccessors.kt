package org.gradle.client.demo.mutations

import org.gradle.declarative.dsl.schema.AnalysisSchema
import org.gradle.declarative.dsl.schema.DataClass

fun AnalysisSchema.hasJavaPrototype(): Boolean =
    dataClassesByFqName.keys.any { it.qualifiedName == "org.gradle.api.experimental.java.JavaLibrary" }

val AnalysisSchema.hasJvmApplication: DataClass
    get() = typeByFqn("org.gradle.api.experimental.jvm.HasJvmApplication")

val AnalysisSchema.hasJavaTarget: DataClass
    get() = typeByFqn("org.gradle.api.experimental.jvm.HasJavaTarget")

