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

import org.gradle.util.GFileUtils

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.*

class DependencyLockFileSubsequentBuildsIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    static final String OTHER_CONFIGURATION_NAME = 'other'

    def "does not update lock file if command line option isn't provides for subsequent build"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        succeeds(COPY_LIBS_TASK_NAME)
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)

        then:
        result.assertTaskSkipped(COPY_LIBS_TASK_PATH)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "does not update lock file for unchanged dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)

        then:
        result.assertTaskSkipped(COPY_LIBS_TASK_PATH)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "recreates lock file for newly declared and resolved dependencies of an existing configuration"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
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
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        locks.size() == 2
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        locks[1].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '972a625f8f26cb15c53a9962cdbc9230ae551aec'
    }

    def "recreates lock file for removed, resolved dependencies for an existing configuration"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
                myConf 'org:gradle:7.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 2
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        locks[1].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '972a625f8f26cb15c53a9962cdbc9230ae551aec'

        when:
        buildFile.text = mavenRepository(mavenRepo) + customConfigurations(MYCONF_CONFIGURATION_NAME) + """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """ + copyLibsTask(MYCONF_CONFIGURATION_NAME)

        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "merges resolved dependencies of newly introduced independent, configuration"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def parsedLockFile = parseLockFile()
        def myConfLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        buildFile << customConfigurations(OTHER_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                $OTHER_CONFIGURATION_NAME 'org:gradle:7.5'
            }
            
            ${COPY_LIBS_TASK_NAME}.from configurations.${OTHER_CONFIGURATION_NAME} 
        """
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        parsedLockFile = parseLockFile()
        myConfLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        def addedLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, OTHER_CONFIGURATION_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        addedLocks.size() == 1
        addedLocks[0].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '5dddb985e4b746fb5678cf1e6745c788dc3d7e5f'
    }

    def "merges resolved dependencies of newly introduced parent configuration"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def parsedLockFile = parseLockFile()
        def myConfLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        buildFile << customConfigurations(OTHER_CONFIGURATION_NAME)
        buildFile << configurationExtension(OTHER_CONFIGURATION_NAME, MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                $OTHER_CONFIGURATION_NAME 'org:gradle:7.5'
            }
            
            ${COPY_LIBS_TASK_NAME}.from configurations.${OTHER_CONFIGURATION_NAME} 
        """
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        parsedLockFile = parseLockFile()
        myConfLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        def addedLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, OTHER_CONFIGURATION_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        myConfLocks.size() == 2
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        myConfLocks[1].toString() == 'org:gradle:7.5 -> 7.5'
        addedLocks.size() == 1
        addedLocks[0].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '3a0963c3c19f24570226d537edd2139f04a90a7e'
    }

    def "merges resolved dependencies of newly introduced child configuration"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        def parsedLockFile = parseLockFile()
        def myConfLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        buildFile << customConfigurations(OTHER_CONFIGURATION_NAME)
        buildFile << configurationExtension(MYCONF_CONFIGURATION_NAME, OTHER_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                $OTHER_CONFIGURATION_NAME 'org:gradle:7.5'
            }
            
            ${COPY_LIBS_TASK_NAME}.from configurations.${OTHER_CONFIGURATION_NAME} 
        """
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        parsedLockFile = parseLockFile()
        myConfLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        def addedLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, OTHER_CONFIGURATION_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        addedLocks.size() == 2
        addedLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        addedLocks[1].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == 'bbc4db0fa9642c82e05b56762215749a7b396285'
    }

    def "does not modify existing lock file if same configuration is unresolvable"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def myConfLocks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        deleteMavenRepo()
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        myConfLocks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)

        then:
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "does not modify existing lock file if different configuration is unresolvable"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def myConfLocks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        buildFile << customConfigurations(OTHER_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                $OTHER_CONFIGURATION_NAME 'org:gradle:7.5'
            }
            
            ${COPY_LIBS_TASK_NAME}.from configurations.${OTHER_CONFIGURATION_NAME} 
        """
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)
        myConfLocks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)

        then:
        myConfLocks.size() == 1
        myConfLocks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    private void deleteMavenRepo() {
        GFileUtils.deleteDirectory(mavenRepo.rootDir)
    }
}
