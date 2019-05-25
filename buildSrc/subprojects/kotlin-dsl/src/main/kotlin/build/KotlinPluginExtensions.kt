package build

import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet

import org.gradle.kotlin.dsl.withConvention

import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet


val SourceSet.kotlin: SourceDirectorySet
    get() = withConvention(KotlinSourceSet::class) { kotlin }


fun SourceSet.kotlin(action: SourceDirectorySet.() -> Unit) =
    kotlin.action()
