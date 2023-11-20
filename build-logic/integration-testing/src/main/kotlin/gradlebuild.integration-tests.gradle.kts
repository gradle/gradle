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

import gradlebuild.basics.testing.TestType
import gradlebuild.basics.testing.includeSpockAnnotation
import gradlebuild.integrationtests.addDependenciesAndConfigurations
import gradlebuild.integrationtests.addSourceSet
import gradlebuild.integrationtests.configureIde
import gradlebuild.integrationtests.createTasks
import gradlebuild.integrationtests.createTestTask
import gradlebuild.integrationtests.extension.IntegrationTestExtension

plugins {
    java
    id("gradlebuild.dependency-modules")
}

extensions.create<IntegrationTestExtension>("integTest").apply {
    usesJavadocCodeSnippets.convention(false)
    testJvmXmx.convention("512m")
}

val sourceSet = addSourceSet(TestType.INTEGRATION)
addDependenciesAndConfigurations(TestType.INTEGRATION.prefix)
createTasks(sourceSet, TestType.INTEGRATION)
configureIde(TestType.INTEGRATION)

createTestTask("integMultiVersionTest", "forking", sourceSet, TestType.INTEGRATION) {
    // This test task runs only multi-version tests and is intended to be used in the late pipeline to sweep up versions not previously tested
    includeSpockAnnotation("org.gradle.integtests.fixtures.compatibility.MultiVersionTestCategory")
    (options as JUnitPlatformOptions).includeEngines("spock")
}
