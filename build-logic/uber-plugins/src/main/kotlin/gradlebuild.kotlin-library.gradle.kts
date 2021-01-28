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
import org.jlleitschuh.gradle.ktlint.KtlintCheckTask


plugins {
    kotlin("jvm")
    id("gradlebuild.java-library")
    id("org.gradle.kotlin-dsl.ktlint-convention")
}

val transitiveSourcesElements by configurations.getting {
    val main = sourceSets.main.get()
    main.kotlin.srcDirs.forEach {
        outgoing.artifact(it)
    }
}

tasks {
    withType<KotlinCompile>().configureEach {
        val launcher = javaToolchains.launcherFor(java.toolchain)
        configureKotlinCompilerForGradleBuild(launcher)
        kotlinOptions.allWarningsAsErrors = true
        if (name == "compileTestKotlin") {
            // Make sure the classes dir is used for test compilation (required by tests accessing internal methods) - https://github.com/gradle/gradle/issues/11501
            classpath = sourceSets.main.get().output.classesDirs + classpath - files(tasks.jar)
        }
    }

    val ktlintCheckTasks = withType<KtlintCheckTask>()

    named("codeQuality") {
        dependsOn(ktlintCheckTasks)
    }

    ktlintKotlinScriptCheck {
        // Only check the build files, not all *.kts files in the project
        setSource(files("build.gradle.kts", "settings.gradle.kts"))
    }

    withType<Test>().configureEach {

        shouldRunAfter(ktlintCheckTasks)

        // enables stricter ClassLoaderScope behaviour
        systemProperty(
            DefaultClassLoaderScope.STRICT_MODE_PROPERTY,
            true
        )
    }
}

fun KotlinCompile.configureKotlinCompilerForGradleBuild(launcher: Provider<JavaLauncher>) {
    kotlinOptions {
        incremental = true
        apiVersion = "1.4"
        languageVersion = "1.4"
        freeCompilerArgs += listOf(
            "-Xjsr305=strict",
            "-java-parameters",
            "-Xskip-runtime-version-check",
            "-Xskip-metadata-version-check"
        )
        jvmTarget = "1.8"
        jdkHome = launcher.get().metadata.installationPath.asFile.absolutePath
    }
}
