/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock.generation

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.*

class DependencyLockFileBuildOptionIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    def setup() {
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)
    }

    def "does not generate lock file if no build option was provided"() {
        when:
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "does not generate lock file if command line option is disabled"() {
        when:
        withDisabledDependencyLockingCommandLineOption()
        succeeds(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "can generate lock file if command line option is enabled"() {
        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
    }
}
