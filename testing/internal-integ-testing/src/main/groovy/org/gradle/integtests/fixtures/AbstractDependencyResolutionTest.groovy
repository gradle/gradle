/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.maven.MavenFileRepository

abstract class AbstractDependencyResolutionTest extends AbstractIntegrationSpec {

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    IvyFileRepository ivyRepo(def dir = 'ivy-repo') {
        return ivy(dir)
    }

    MavenFileRepository mavenRepo(String name = "repo") {
        return maven(name)
    }

    /**
     * Asserts that the task that performs dependency resolution has failed.
     * With Configuration Cache, the task is expected to fail at serialization time.
     * In vintage mode, the task is expected to fail at execution time.
     *
     * @param taskSelector the qualified name of the task (including leading {@code :})
     */
    void assertResolutionTaskFailed(String taskSelector) {
        if (GradleContextualExecuter.configCache) {
            failureDescriptionContains("Configuration cache state could not be cached:")
            failureDescriptionContains(taskSelector)
        } else {
            def description = "Execution failed for task '${taskSelector}'."
            failure.assertHasDescription(description)
        }
    }
}
