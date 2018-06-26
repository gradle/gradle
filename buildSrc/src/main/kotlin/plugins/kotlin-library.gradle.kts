/*
 * Copyright 2018 the original author or authors.
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

import accessors.kotlin

import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import org.gradle.api.internal.initialization.DefaultClassLoaderScope
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
    //id("org.gradle.kotlin.ktlint-convention")
}

kotlin {
    experimental.coroutines = Coroutines.ENABLE
}

tasks {

    withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs += listOf(
                "-Xjsr305=strict",
                "-Xskip-runtime-version-check")
        }
    }

    withType<Test> {

        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }

        // sets the Gradle Test Kit user home to a known constant dir
        systemProperty(
            "org.gradle.testkit.dir",
            "$rootDir/.gradle/testKitGradleUserHome")

        // enables stricter ClassLoaderScope behaviour
        systemProperty(
            DefaultClassLoaderScope.STRICT_MODE_PROPERTY,
            true)

        // sets the memory limits for test workers
        jvmArgs("-Xms64m", "-Xmx128m")
    }
}

tasks.register("quickTest", Test::class.java) {
    exclude(
        "**/*IntegrationTest.class",
        "**/*SampleTest.class"
    )
}
