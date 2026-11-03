/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.testing

import gradlebuild.basics.testDistributionEnabled
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test


fun Test.isUnitTest() = listOf("test", "writePerformanceScenarioDefinitions", "writeTmpPerformanceScenarioDefinitions").contains(name)


fun Project.isPerformanceProject() = setOf("build-scan-performance", "performance").contains(name)


fun Project.isNativeProject() = name.contains("native")


fun Project.isKotlinDslToolingBuilders() = name.contains("kotlin-dsl-tooling-builders")


/**
 * Whether this task runs its tests with Test Distribution, rather than in local test JVMs.
 */
fun Test.usesTestDistribution(): Boolean =
    project.testDistributionEnabled && !isUnitTest() && !project.isPerformanceProject() && !project.isNativeProject() && !project.isKotlinDslToolingBuilders()
