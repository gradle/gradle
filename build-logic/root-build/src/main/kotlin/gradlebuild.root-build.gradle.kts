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

plugins {
    id("lifecycle-base") // Make sure the basic lifecycle tasks are added through 'lifecycle-base'. Other plugins might sneakily apply that to the root project.
    id("gradlebuild.buildscan") // Reporting: Add more data through custom tags to build scans
    id("gradlebuild.ide") // Local development: Tweak IDEA import
    id("gradlebuild.dependency-analysis") // Auditing dependencies to find unused libraries
}

val baseLifecycleTasks = listOf(
    LifecycleBasePlugin.CLEAN_TASK_NAME,
    LifecycleBasePlugin.ASSEMBLE_TASK_NAME,
    LifecycleBasePlugin.BUILD_TASK_NAME
)

// See also 'gradlebuild.ci-lifecycle'
val lifecycleTasks = listOf(
    "test",
    "compileAllBuild",
    "sanityCheck",
    "quickTest",
    "platformTest",
    "allVersionsIntegMultiVersionTest",
    "parallelTest",
    "noDaemonTest",
    "configCacheTest",
    "watchFsTest",
    "forceRealizeDependencyManagementTest",
    "quickFeedbackCrossVersionTest",
    "allVersionsCrossVersionTest"
)

if (subprojects.isEmpty()) { // umbrella build
    baseLifecycleTasks.forEach { lifecycleTask ->
        tasks.named(lifecycleTask) {
            dependsOn(gradle.includedBuilds.map { it.task(":${lifecycleTask}") })
        }
    }
    lifecycleTasks.forEach { lifecycleTask ->
        tasks.register(lifecycleTask) {
            dependsOn(gradle.includedBuilds.map { it.task(":${lifecycleTask}") })
        }
    }
} else {
    baseLifecycleTasks.forEach { lifecycleTask ->
        tasks.named(lifecycleTask) {
            dependsOn(subprojects.map { "${it.name}:${lifecycleTask}" })
        }
    }
    lifecycleTasks.forEach { lifecycleTask ->
        tasks.register(lifecycleTask) {
            dependsOn(subprojects.map { "${it.name}:${lifecycleTask}" })
        }
    }
}
