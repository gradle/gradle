/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.test.fixtures.file.TestFile

/**
 * A test fixture that injects a task into a build that resolves a dependency configuration and does some validation of the resulting graph.
 */
class ResolveTestFixture {
    private final TestFile buildFile
    private final String config

    ResolveTestFixture(TestFile buildFile, String config = "compile") {
        this.config = config
        this.buildFile = buildFile
    }

    /**
     * Injects the appropriate stuff into the build script.
     */
    void prepare() {
        buildFile << """
task checkDeps(dependsOn: configurations.${config}) {
    configurations.${config}.resolvedConfiguration.firstLevelModuleDependencies
}
"""
    }

    /**
     * Verifies the result of executing the task.
     */
    void expectGraph(Closure closure) {
//        closure.call()
    }
}
