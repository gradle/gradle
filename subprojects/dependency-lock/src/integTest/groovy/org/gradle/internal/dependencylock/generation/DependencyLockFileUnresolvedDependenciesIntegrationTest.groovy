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

class DependencyLockFileUnresolvedDependenciesIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    def "does not write lock file if no dependencies were resolved"() {
        given:
        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "does not write lock file if all dependencies failed to be resolved"() {
        given:
        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "does not write lock file if at least one dependency of the same configuration is unresolvable"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'does.not:exist:1.2.3'
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "does not write lock file if one of many configuration is unresolvable"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations('a', 'b', 'c')
        buildFile << """
            dependencies {
                a 'foo:bar:1.5'
                b 'org:gradle:7.5'
                c 'my:prod:3.2.1'
            }
        """
        buildFile << copyLibsTask('a', 'b', 'c')

        when:
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }

    def "does not write lock file if resolution result contains errors for lenient configurations"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
                myConf 'does.not:exist:1.2.3'
            }
            
            task $COPY_LIBS_TASK_NAME(type: Copy) {
                from { configurations.myConf.resolvedConfiguration.lenientConfiguration.files }
                into "\$buildDir/libs"
            }
        """

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }
}
