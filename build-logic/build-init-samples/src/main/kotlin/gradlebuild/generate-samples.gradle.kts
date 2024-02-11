/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gradlebuild

import gradlebuild.basics.capitalize
import gradlebuild.samples.tasks.GenerateSample
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.Language.CPP
import org.gradle.buildinit.plugins.internal.modifiers.Language.GROOVY
import org.gradle.buildinit.plugins.internal.modifiers.Language.JAVA
import org.gradle.buildinit.plugins.internal.modifiers.Language.KOTLIN
import org.gradle.buildinit.plugins.internal.modifiers.Language.SCALA
import org.gradle.buildinit.plugins.internal.modifiers.Language.SWIFT
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption
import org.gradle.docs.samples.Dsl

plugins {
    id("org.gradle.samples")
}

val singleProjectSampleLanguages = listOf(JAVA, GROOVY, SCALA, KOTLIN, SWIFT, CPP)
val multiProjectApplicationSampleLanguages = listOf(JAVA, GROOVY, SCALA, KOTLIN)

singleProjectSampleLanguages.forEach { language ->
    setupGeneratorTask(language, "library", ModularizationOption.SINGLE_PROJECT)
    setupGeneratorTask(language, "application", ModularizationOption.SINGLE_PROJECT)
}

multiProjectApplicationSampleLanguages.forEach { language ->
    setupGeneratorTask(language, "application", ModularizationOption.WITH_LIBRARY_PROJECTS)
}

fun setupGeneratorTask(language: Language, kind: String, modularizationOption: ModularizationOption) {
    val buildInitType = "${language.getName()}-$kind"
    val capName = language.getName().capitalize()
    val capKind = kind.capitalize().replace("y", "ie") + "s"
    val languageDisplayName = language.toString().replace("C++", "{cpp}")
    val sampleName = "building$capName$capKind" + if (modularizationOption.isMulti()) "MultiProject" else ""
    val multiProjectSuffix = if (modularizationOption.isMulti()) " with libraries" else ""
    val generateSampleTask = project.tasks.register<GenerateSample>("generateSample${sampleName.capitalize()}") {
        readmeTemplates.convention(layout.projectDirectory.dir("src/samples/readme-templates"))
        target.convention(layout.buildDirectory.dir("generated-samples/$buildInitType" + if (modularizationOption.isMulti()) "-with-libraries" else ""))
        type = buildInitType
        modularization = modularizationOption
    }
    samples.publishedSamples.create(sampleName) {
        dsls = setOf(Dsl.GROOVY, Dsl.KOTLIN)
        sampleDirectory = generateSampleTask.flatMap { it.target }
        displayName = "Building $languageDisplayName $capKind$multiProjectSuffix"
        description = "Setup a $languageDisplayName $kind project$multiProjectSuffix step-by-step."
        category = language.toString()
    }
}

fun ModularizationOption.isMulti() = this == ModularizationOption.WITH_LIBRARY_PROJECTS
