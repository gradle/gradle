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

class DependencyLockFileSubsequentBuildsIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    def "subsequent builds do not recreate lock file for unchanged dependencies"() {
        given:
        requireOwnGradleUserHomeDir()
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)

        then:
        result.assertTaskSkipped(COPY_LIBS_TASK_PATH)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "recreates lock file for newly declared and resolved dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        buildFile << """
            dependencies {
                myConf 'org:gradle:7.5'
            }
        """

        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        locks.size() == 2
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        locks[1].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '972a625f8f26cb15c53a9962cdbc9230ae551aec'
    }

    def "recreates lock file for removed, resolved dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
                myConf 'org:gradle:7.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 2
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        locks[1].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '972a625f8f26cb15c53a9962cdbc9230ae551aec'

        when:
        buildFile.text = mavenRepository(mavenRepo) + customConfigurations(MYCONF_CUSTOM_CONFIGURATION) + """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """ + copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }
}
