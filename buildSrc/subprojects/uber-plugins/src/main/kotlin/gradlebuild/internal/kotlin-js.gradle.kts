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
package gradlebuild.internal

import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jlleitschuh.gradle.ktlint.KtlintCheckTask
import org.jlleitschuh.gradle.ktlint.KtlintFormatTask

plugins {
    id("kotlin2js")
    id("gradlebuild.repositories")
    id("gradlebuild.unittest-and-compile")
    id("org.gradle.kotlin-dsl.ktlint-convention")
}

apply(from = "$rootDir/gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")

tasks {
    withType<Kotlin2JsCompile>().configureEach {
        kotlinOptions.allWarningsAsErrors = true
    }

    withType<KtlintFormatTask>().configureEach {
        enabled = false
    }

    val ktlintCheckTasks = withType<KtlintCheckTask>()

    withType<Test>().configureEach {
        shouldRunAfter(ktlintCheckTasks)
    }

    named("codeQuality") {
        dependsOn(ktlintCheckTasks)
    }
}
