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

tasks.registerEarlyFeedbackLifecycleTasks()
tasks.named("quickTest") {
    dependsOn("test")
}
tasks.named("platformTest") {
    dependsOn("test")
}
tasks.configureCIIntegrationTestDistributionLifecycleTasks()
tasks.configureCICrossVersionTestDistributionLifecycleTasks()

val ciGroup = "CI Lifecycle"

/**
 * Basic compile and check lifecycle tasks.
 */
fun TaskContainer.registerEarlyFeedbackLifecycleTasks() {
    register("compileAllBuild") {
        description = "Initialize CI Pipeline by priming the cache before fanning out"
        group = ciGroup
        dependsOn("compileAll")
    }

    register("sanityCheck") {
        description = "Run all basic checks (without tests) - to be run locally and on CI for early feedback"
        group = "verification"
        dependsOn("compileAll", "codeQuality")
    }
}

fun TaskContainer.configureCIIntegrationTestDistributionLifecycleTasks() {
    named("quickTest") {
        dependsOn("embeddedIntegTest")
    }

    named("platformTest") {
        dependsOn("forkingIntegTest")
    }

    named("allVersionsIntegMultiVersionTest") {
        dependsOn("integMultiVersionTest")
    }

    named("parallelTest") {
        dependsOn("parallelIntegTest")
    }

    named("noDaemonTest") {
        dependsOn("noDaemonIntegTest")
    }

    named("configCacheTest") {
        dependsOn("configCacheIntegTest")
    }

    named("watchFsTest") {
        dependsOn("watchFsIntegTest")
    }

    named("forceRealizeDependencyManagementTest") {
        dependsOn("integForceRealizeTest")
    }
}

fun TaskContainer.configureCICrossVersionTestDistributionLifecycleTasks() {
    named("quickTest") {
        dependsOn("embeddedCrossVersionTest")
    }

    named("platformTest") {
        dependsOn("forkingCrossVersionTest")
    }

    named("quickFeedbackCrossVersionTest") {
        dependsOn("quickFeedbackCrossVersionTests")
    }

    named("allVersionsCrossVersionTest") {
        dependsOn("allVersionsCrossVersionTests")
    }
}
