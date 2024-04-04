/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.resolve

import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.file.TestFile

/**
 * A test fixture that injects a task into a build that triggers a dependency resolution failure.
 */
class ResolveFailureTestFixture {
    String config
    private final TestFile buildFile

    ResolveFailureTestFixture(TestFile buildFile, String config = "compile") {
        this.buildFile = buildFile
        this.config = config
    }

    void prepare(String config = this.config) {
        buildFile << """
            allprojects {
                tasks.register("checkDeps") {
                    if (${GradleContextualExecuter.configCache}) {
                        def files = configurations.${config}
                        doLast {
                            files.forEach { }
                        }
                    } else {
                        doLast {
                            configurations.${config}.resolve()
                        }
                    }
                }
            }
        """
    }

    void assertFailurePresent(ExecutionFailure failure) {
        if (GradleContextualExecuter.configCache) {
            failure.assertHasDescription("Configuration cache state could not be cached")
        } else {
            failure.assertHasDescription("Execution failed for task")
        }
    }
}
