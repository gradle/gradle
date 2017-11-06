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

import spock.lang.Unroll

import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.*

class DependencyLockFileResolutionRulesIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    @Unroll
    def "lock file does not reflect dependency resolve rule for #description"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('foo', 'bar', '2.9').publish()
        mavenRepo.module('other', 'dep', '2.9').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            configurations.myConf {
                resolutionStrategy.eachDependency { DependencyResolveDetails details ->
                    if (details.requested.group == 'foo' && details.requested.name == 'bar') {
                        $replacement
                    }
                }
            }

            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 1
        locks[0].toString() == 'foo:bar -> 1.5'

        where:
        replacement                         | description
        "details.useVersion '2.9'"          | 'different version of same module'
        "details.useTarget 'other:dep:2.9'" | 'different module'
    }

    @Unroll
    def "lock file does not reflect #type dependency substitution rule"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('other', 'dep', '2.9').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            configurations.myConf {
                resolutionStrategy.dependencySubstitution { 
                    substitute $sourceComponent with $targetComponent
                }
            }

            dependencies {
                myConf 'foo:bar:1.5'
            }

            project(':fooBar') {
                apply plugin: 'java-library'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)
        settingsFile << "include 'fooBar'"

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 1
        locks[0].toString() == 'foo:bar -> 1.5'

        where:
        sourceComponent         | targetComponent           | type
        "module('foo:bar:1.5')" | "module('other:dep:2.9')" | 'module -> module'
        "module('foo:bar')"     | "project(':fooBar')"      | 'module -> project'
        "project(':fooBar')"    | "module('foo:bar:1.5')"   | 'project -> module'
    }
}
