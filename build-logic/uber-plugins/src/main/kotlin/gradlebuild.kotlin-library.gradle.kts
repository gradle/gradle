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

tasks {
    withType<KotlinCompile>().configureEach {
        configureKotlinCompilerForGradleBuild()
    }

    withType<Test>().configureEach {
        // enables stricter ClassLoaderScope behaviour
        systemProperty(
            DefaultClassLoaderScope.STRICT_MODE_PROPERTY,
            true
        )
    }
}
