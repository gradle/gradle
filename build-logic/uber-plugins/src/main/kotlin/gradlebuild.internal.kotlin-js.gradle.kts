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

plugins {
    kotlin("js")
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("org.gradle.kotlin-dsl.ktlint-convention")
    id("gradlebuild.code-quality")
}

kotlin {
    js {
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
