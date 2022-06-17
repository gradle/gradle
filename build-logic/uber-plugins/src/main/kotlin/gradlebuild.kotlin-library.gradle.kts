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

import gradlebuild.basics.accessors.kotlin
import org.gradle.api.internal.initialization.DefaultClassLoaderScope
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    kotlin("jvm")
    id("gradlebuild.java-library")
    id("gradlebuild.ktlint")
}

configurations.transitiveSourcesElements {
    val main = sourceSets.main.get()
    main.kotlin.srcDirs.forEach {
        outgoing.artifact(it)
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        configureKotlinCompilerForGradleBuild()
        if (name == "compileTestKotlin") {
            // Make sure the classes dir is used for test compilation (required by tests accessing internal methods) - https://github.com/gradle/gradle/issues/11501
            classpath = sourceSets.main.get().output.classesDirs + classpath - files(tasks.jar)
        }
    }

    codeQuality {
        dependsOn(ktlintCheck)
    }

    runKtlintCheckOverKotlinScripts {
        // Only check the build files, not all *.kts files in the project
        setIncludes(listOf("*.gradle.kts"))
    }

    withType<Test>().configureEach {

        shouldRunAfter(ktlintCheck)

        // enables stricter ClassLoaderScope behaviour
        systemProperty(
            DefaultClassLoaderScope.STRICT_MODE_PROPERTY,
            true
        )
    }
}

fun KotlinCompile.configureKotlinCompilerForGradleBuild() {
    kotlinOptions {
        incremental = true
        /*
          w: Flag is not supported by this version of the compiler: -Xskip-runtime-version-check
          w: Language version 1.4 is deprecated and its support will be removed in a future version of Kotlin
          e: warnings found and -Werror specified
         */
        // allWarningsAsErrors = true
        apiVersion = "1.4"
        languageVersion = "1.4"
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-java-parameters",
            "-Xskip-metadata-version-check",
            // TODO can be removed once we build against language version >= 1.5
            "-Xsuppress-version-warnings"
        )
        jvmTarget = "1.8"
    }
}
