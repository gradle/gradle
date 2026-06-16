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

import gradlebuild.basics.accessors.kotlinMainSourceSet
import gradlebuild.basics.kotlindsl.configureKotlinCompilerForGradleBuild
import gradlebuild.basics.testing.TestType
import gradlebuild.integrationtests.configureTestSourceSetInIde
import org.gradle.api.internal.initialization.DefaultClassLoaderScope
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    id("gradlebuild.jvm-library")
    id("gradlebuild.detekt")
    id("gradlebuild.private-javadoc")
}

configurations.transitiveSourcesElements {
    (kotlinMainSourceSet.srcDirs + sourceSets.main.get().resources.srcDirs).forEach {
        outgoing.artifact(it)
    }
}

kotlin {
    target.compilations.named("testFixtures") {
        associateWith(target.compilations["main"])
    }
    target.compilations.named("test") {
        associateWith(target.compilations["main"])
        associateWith(target.compilations["testFixtures"])
    }
    target.compilations.named("integTest") {
        associateWith(target.compilations["main"])
        associateWith(target.compilations["testFixtures"])
    }
}

val kotlinModuleName = project.name
// Pin the module name temporarily because it changed between Kotlin 2.4.0-Beta2 and 2.4.0-RC
// see: https://youtrack.jetbrains.com/issue/KT-69701
// It is a problem while transitioning from one embedded version to another, because
// we can't make the architecture tests work both on the build and in dogfooding tests.
// Once the wrapper will embed 2.4.0-RC or later, we can remove this and just go with the default
// (adjust architecture tests accordingly).

tasks {
    withType<KotlinCompile>().configureEach {
        configureKotlinCompilerForGradleBuild(kotlinModuleName)
    }

    withType<Test>().configureEach {
        // enables stricter ClassLoaderScope behaviour
        systemProperty(
            DefaultClassLoaderScope.STRICT_MODE_PROPERTY,
            true
        )
    }
}

configureTestSourceSetInIde(sourceSets.getByName("${TestType.INTEGRATION.prefix}Test"))
