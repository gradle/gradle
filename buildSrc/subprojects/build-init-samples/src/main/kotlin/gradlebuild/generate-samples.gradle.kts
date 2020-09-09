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

import gradlebuild.samples.tasks.GenerateSample
import org.gradle.buildinit.plugins.internal.modifiers.Language
import org.gradle.buildinit.plugins.internal.modifiers.Language.JAVA
import org.gradle.buildinit.plugins.internal.modifiers.Language.GROOVY
import org.gradle.buildinit.plugins.internal.modifiers.Language.SCALA
import org.gradle.buildinit.plugins.internal.modifiers.Language.KOTLIN
import org.gradle.buildinit.plugins.internal.modifiers.Language.SWIFT
import org.gradle.buildinit.plugins.internal.modifiers.Language.CPP
import org.gradle.docs.samples.Dsl

plugins {
    id("org.gradle.samples")
}

val singleProjectSampleLanguages = listOf(JAVA, GROOVY, SCALA, KOTLIN, SWIFT, CPP)

singleProjectSampleLanguages.forEach { language ->
    setupGeneratorTask(language, "library")
    setupGeneratorTask(language, "application")
}

fun setupGeneratorTask(language: Language, kind: String) {
    val buildInitType = "${language.name}-$kind"
    val capName = language.name.capitalize()
    val capKind = kind.capitalize().replace("y", "ie") + "s"
    val languageDisplayName = language.toString().replace("C++", "{cpp}")
    val sampleName = "building$capName$capKind"
    val generateSampleTask = project.tasks.register<GenerateSample>("generateSample${sampleName.capitalize()}") {
        readmeTemplates.convention(layout.projectDirectory.dir("src/samples/readme-templates"))
        target.convention(project.layout.buildDirectory.dir("generated-samples/$buildInitType"))
        type.set(buildInitType)
    }
    samples.publishedSamples.create(sampleName) {
        dsls.set(setOf(Dsl.GROOVY, Dsl.KOTLIN))
        sampleDirectory.set(generateSampleTask.flatMap { it.target })
        displayName.set("Building $languageDisplayName $capKind")
        description.set("Setup a $languageDisplayName library project step-by-step.")
        category.set(language.toString())
    }
}
