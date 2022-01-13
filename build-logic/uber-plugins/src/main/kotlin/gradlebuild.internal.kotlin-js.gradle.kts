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

import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinJsCompilerType

plugins {
    kotlin("js")
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.code-quality")
    id("gradlebuild.ktlint")
}

kotlin {
    js(KotlinJsCompilerType.IR) {
        browser {
            webpackTask {
                sourceMaps = false
            }
            testTask {
                enabled = false
            }
        }
        binaries.executable()
    }
}

// Move yarn.lock to the build directory, out of VCS control
rootProject.run {
    plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
        configure<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension> {
            lockFileDirectory = layout.buildDirectory.file("kotlin-js-store").get().asFile
        }
    }
}

tasks {
    withType<KotlinJsCompile>().configureEach {
        kotlinOptions {
            allWarningsAsErrors = true
            metaInfo = false
            moduleKind = "plain"
        }
    }

    withType<Test>().configureEach {
        shouldRunAfter(ktlintCheck)
    }

    codeQuality {
        dependsOn(ktlintCheck)
    }

    register("quickTest") {
        dependsOn(named("test"))
        dependsOn(ktlintCheck)
    }

    register("platformTest")
}
