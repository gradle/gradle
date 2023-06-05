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

dependencies {
    // ========================================================================
    // All subprojects, which has their own preconditions.
    // These projects should have their preconditions in the "src/testFixtures" sourceSet
    // ========================================================================
    testRuntimeOnly(testFixtures(project(":plugins")))
    testRuntimeOnly(testFixtures(project(":signing")))
    testRuntimeOnly(testFixtures(project(":test-kit")))
    testRuntimeOnly(testFixtures(project(":smoke-test")))

    // ========================================================================
    // Other, project-related dependencies
    // ========================================================================
    testRuntimeOnly(project(":distributions-core")) {
        because("Distribution is needed to execute cross-version tests")
    }

    // All of these configurations need the "test" source set
    // This is unusual, hence we need to explicitly add it here
    crossVersionTestImplementation(sourceSets.test.get().output)
    archTestImplementation(sourceSets.test.get().output)
}

// All configurations need access to all the precondition classes.
// Normally, "testRuntimeOnly" would be inherited by other test suites, but there are a few exceptions, where it's not:
configurations {
    crossVersionTestRuntimeOnly {
        extendsFrom(testRuntimeOnly.get())
    }

    archTestRuntimeOnly {
        extendsFrom(testRuntimeOnly.get())
    }
}

tasks {
    withType(Test::class) {
        testClassesDirs = sourceSets.test.get().output.classesDirs

        // These tests should not be impacted by the predictive selection
        predictiveSelection {
            enabled = false
        }

        // These tests should always run
        outputs.upToDateWhen { false }
    }
}
