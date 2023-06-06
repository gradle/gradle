/*
 * Copyright 2023 the original author or authors.
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
    id("gradlebuild.internal.java")
}

description = "Internal project testing and collecting information about all the test preconditions."

// Special configuration containing all the precondition classes
val precondition by configurations.creating {
    description = "Configuration for all TestPrecondition classes dynamically loaded by the precondition tester"
    isCanBeResolved = false
    isCanBeConsumed = true
}

configurations {
    // All test runtime configurations should extend from the "precondition" configuration
    listOf(testRuntimeOnly, integTestRuntimeOnly, archTestRuntimeOnly, crossVersionTestRuntimeOnly).forEach {
        it.configure {
            extendsFrom(precondition)
        }
    }
}

dependencies {
    // ========================================================================
    // All subprojects, which has their own preconditions.
    // These projects should have their preconditions in the "src/testFixtures" sourceSet
    // ========================================================================
    // This whole project is for test support, i.e. it's "main" source set is used
    precondition(project(":internal-testing"))
    // This whole project is for test support, i.e. it's "main" source set is used
    precondition(project(":internal-integ-testing"))
    precondition(testFixtures(project(":plugins")))
    precondition(testFixtures(project(":signing")))
    precondition(testFixtures(project(":test-kit")))
    precondition(testFixtures(project(":smoke-test")))

    // This is a special dependency, as some of the preconditions might need a distribution.
    // E.g. see "IntegTestPreconditions.groovy"
    precondition(project(":distributions-core")) {
        because("Some preconditions might need a distribution to run against")
    }

    // ========================================================================
    // Other, project-related dependencies
    // ========================================================================
    testImplementation(libs.junit5JupiterApi) {
        because("Assume API comes from here")
    }
}

tasks {
    withType(Test::class) {
        // We only want to execute our special tests,
        // so we override what classes are going to run
        testClassesDirs = sourceSets.test.get().output.classesDirs
        // All test should have this project's "test" source set on their classpath
        classpath += sourceSets.test.get().output

        // These tests should not be impacted by the predictive selection
        predictiveSelection {
            enabled = false
        }

        // These tests should always run
        outputs.upToDateWhen { false }
    }
}

afterEvaluate {
    configurations.forEach(::println)
}
