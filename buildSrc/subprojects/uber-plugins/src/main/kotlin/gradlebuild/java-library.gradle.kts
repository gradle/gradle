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
package gradlebuild

import org.gradle.kotlin.dsl.*

plugins {
    `java-library`
    id("gradlebuild.repositories")
    id("gradlebuild.minify")
    id("gradlebuild.reproducible-archives")
    id("gradlebuild.unittest-and-compile")
    id("gradlebuild.test-fixtures")
    id("gradlebuild.distribution-testing")
    id("gradlebuild.incubation-report")
    id("gradlebuild.task-properties-validation")
    id("gradlebuild.strict-compile")
    id("gradlebuild.classycle")
}

apply(from = "$rootDir/gradle/shared-with-buildSrc/code-quality-configuration.gradle.kts")

if (file("src/integTest").isDirectory) {
    apply(plugin = "gradlebuild.integration-tests")
}

if (file("src/crossVersionTest").isDirectory) {
    apply(plugin = "gradlebuild.cross-version-tests")
}

if (file("src/performanceTest").isDirectory) {
    apply(plugin = "gradlebuild.performance-test")
}

if (file("src/jmh").isDirectory) {
    apply(plugin = "gradlebuild.jmh")
}

tasks.named("check").configure {
    dependsOn(":docs:checkstyleApi")
    dependsOn("codeQuality")
}
