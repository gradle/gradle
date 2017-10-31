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
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "can write locks for resolvable dependencies even if at least one dependency is unresolvable"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'does.not:exist:1.2.3'
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "can create locks for all supported formats of dynamic dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()
        mavenRepo.module('my', 'prod', '3.2.1').publish()
        mavenRepo.module('dep', 'range', '1.7.1').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                myConf 'org:gradle:+'
                myConf 'my:prod:latest.release'
                myConf 'dep:range:[1.0,2.0]'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 4
        locks[0].toString() == 'foo:bar:1.+ -> 1.3'
        locks[1].toString() == 'org:gradle:+ -> 7.5'
        locks[2].toString() == 'my:prod:latest.release -> 3.2.1'
        locks[3].toString() == 'dep:range:[1.0,2.0] -> 1.7.1'
        sha1File.text == 'e1694e2aaafe588b76b0acb82b258a06b853a494'
    }

    def "only creates locks for resolved dependencies"() {
        given:
        mavenRepo.module('foo', 'bar', '1.3').publish()
        mavenRepo.module('org', 'gradle', '7.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION, 'unresolved')
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.+'
                unresolved 'org:gradle:+'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 1
        locks[0].toString() == 'foo:bar:1.+ -> 1.3'
        sha1File.text == 'a9996480fc8669d8dbb61c8352a2525cc5c554e9'
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
        def parsedLockFile = parseLockFile()
        def aLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, 'a')
        aLocks.size() == 1
        aLocks[0].toString() == 'foo:bar:1.+ -> 1.3'
        def bLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, 'b')
        bLocks.size() == 1
        bLocks[0].toString() == 'org:gradle:7.5 -> 7.5'
        def cLocks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, 'c')
        cLocks.size() == 1
        cLocks[0].toString() == 'my:prod:latest.release -> 3.2.1'
        sha1File.text == '59578372a1c61109e5af9ad4d915c8ec8c62a330'
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
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'bar:first:2.+'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 6
        locks[0].toString() == 'foo:first:1.5 -> 1.5'
        locks[1].toString() == 'foo:second:1.6.7 -> 1.6.7'
        locks[2].toString() == 'foo:third:1.5 -> 1.5'
        locks[3].toString() == 'bar:first:2.+ -> 2.5'
        locks[4].toString() == 'bar:second:2.6.7 -> 2.6.7'
        locks[5].toString() == 'bar:third:2.5 -> 2.5'
        sha1File.text == 'c9dede601fb738575952da5dae120b2a35a9ff0d'
    }

    def "can create lock for conflict-resolved dependency"() {
        given:
        def fooSecondModule = mavenRepo.module('foo', 'second', '1.6.7').publish()
        mavenRepo.module('foo', 'first', '1.5').dependsOn(fooSecondModule).publish()
        mavenRepo.module('foo', 'second', '1.9').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:first:1.5'
                myConf 'foo:second:1.9'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, MYCONF_CUSTOM_CONFIGURATION)
        locks.size() == 3
        locks[0].toString() == 'foo:first:1.5 -> 1.5'
        locks[1].toString() == 'foo:second:1.6.7 -> 1.9'
        locks[2].toString() == 'foo:second:1.9 -> 1.9'
        sha1File.text == '964969098a6e2d647dd2fad40841ae9467d2687a'
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
        def parsedLockFile = parseLockFile()
        def locks = parsedLockFile.getLocks(ROOT_PROJECT_PATH, 'compileClasspath')
        locks.size() == 2
        locks[0].toString() == 'foo:bar:1.5 -> 1.5'
        locks[1].toString() == 'org:gradle:7.5 -> 7.5'
        sha1File.text == '60509fcde47220bc2bbd808a5ecb8a5839fe323c'
    }
}
