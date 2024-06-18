/*
 * Copyright 2024 the original author or authors.
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

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true // preconfigure defaults
    config.setFrom(project.isolated.rootProject.projectDirectory.file("gradle/detekt.yml")) // point to your custom config defining rules to run, overwriting default behavior
}

// TODO: need custom task to check kts build files

pluginManager.withPlugin("gradlebuild.code-quality") {
    tasks {
        named("codeQuality") {
            dependsOn(detekt)
        }
    }
}

