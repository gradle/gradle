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

class DependencyLockFileResolvedDependenciesIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

    def "can create locks for dependencies with concrete version"() {
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
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 1
        locks[0].toString() == 'foo:bar -> 1.5'
    }

    def "can create locks for all supported formats of dynamic dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()
        mavenRepo.module('dep', 'range', '1.7.1').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                myConf 'org:gradle:+'
                myConf 'my:prod:latest.release'
                myConf 'dep:range:[1.0,2.0]'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 4
        locks[0].toString() == 'foo:bar -> 1.3'
        locks[1].toString() == 'org:gradle -> 7.5'
        locks[2].toString() == 'my:prod -> 3.2.1'
        locks[3].toString() == 'dep:range -> 1.7.1'
    }

    def "only creates locks for resolved dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME, 'unresolved')
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                unresolved 'org:gradle:+'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 1
        locks[0].toString() == 'foo:bar -> 1.3'
    }

    def "can create locks for all multiple resolved configurations"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations('a', 'b', 'c')
        buildFile << """
            dependencies {
                a 'foo:bar:1.+'
                b 'org:gradle:7.5'
                c 'my:prod:latest.release'
            }
        """
        buildFile << copyLibsTask('a', 'b', 'c')

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def parsedLockFile = parseLockFile()
        def aLocks = parseLockFile().getLocks(ROOT_PROJECT_PATH, 'a')
        aLocks.size() == 1
        aLocks[0].toString() == 'foo:bar -> 1.3'
        def bLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, 'b')
        bLocks.size() == 1
        bLocks[0].toString() == 'org:gradle -> 7.5'
        def cLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, 'c')
        cLocks.size() == 1
        cLocks[0].toString() == 'my:prod -> 3.2.1'
    }

    def "can create locks for first-level and transitive resolved dependencies"() {
        given:
        def fooThirdModule = mavenRepo.module('foo', 'third', '1.5').publish()
        def fooSecondModule = mavenRepo.module('foo', 'second', '1.6.7').dependsOn(fooThirdModule).publish()
        mavenRepo.module('foo', 'first', '1.5').dependsOn(fooSecondModule).publish()
        def barThirdModule = mavenRepo.module('bar', 'third', '2.5').publish()
        def barSecondModule = mavenRepo.module('bar', 'second', '2.6.7').dependsOn(barThirdModule).publish()
        mavenRepo.module('bar', 'first', '2.5').dependsOn(barSecondModule).publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'bar:first:2.+'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 6
        locks[0].toString() == 'foo:first -> 1.5'
        locks[1].toString() == 'foo:second -> 1.6.7'
        locks[2].toString() == 'foo:third -> 1.5'
        locks[3].toString() == 'bar:first -> 2.5'
        locks[4].toString() == 'bar:second -> 2.6.7'
        locks[5].toString() == 'bar:third -> 2.5'
    }

    def "can create lock for conflict-resolved dependency"() {
        given:
        def fooSecondModule = mavenRepo.module('foo', 'second', '1.6.7').publish()
        mavenRepo.module('foo', 'first', '1.5').dependsOn(fooSecondModule).publish()
        mavenRepo.module('foo', 'second', '1.9').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'foo:second:1.9'
            }
        """
        buildFile << copyLibsTask(MYCONF_CONFIGURATION_NAME)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, MYCONF_CONFIGURATION_NAME)
        locks.size() == 2
        locks[0].toString() == 'foo:first -> 1.5'
        locks[1].toString() == 'foo:second -> 1.9'
    }

    def "only creates locks for resolvable configurations"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << """
            apply plugin: 'java-library'

            dependencies {
                api 'foo:bar:1.5'
                implementation 'org:gradle:7.5'
            }
        """
        buildFile << copyLibsTask('compileClasspath')

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileExist()
        def locks = parseLockFile().getLocks(ROOT_PROJECT_PATH, 'compileClasspath')
        locks.size() == 2
        locks[0].toString() == 'foo:bar -> 1.5'
        locks[1].toString() == 'org:gradle -> 7.5'
    }

    def "does not write lock file for resolved, detached configuration"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CONFIGURATION_NAME)
        buildFile << """
            def dep = dependencies.create('foo:bar:1.5')
            def detachedConfiguration = configurations.detachedConfiguration(dep)
            
            task $COPY_LIBS_TASK_NAME(type: Copy) {
                from detachedConfiguration
                into "\$buildDir/libs"
            }
        """

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        assertLockFileAndHashFileDoNotExist()
    }
}
