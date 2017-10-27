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

package org.gradle.internal.dependencylock

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.initialization.StartParameterBuildOptions.DependencyLockOption
import static org.gradle.internal.dependencylock.fixtures.DependencyLockFixture.*

class DependencyLockFileGenerationIntegrationTest extends AbstractIntegrationSpec {

    private static final String MYCONF_CUSTOM_CONFIGURATION = 'myConf'

    TestFile lockFile
    TestFile sha1File

    def setup() {
        lockFile = file('gradle/dependencies.lock')
        sha1File = file('gradle/dependencies.lock.sha1')
    }

    def "does not write lock file if no dependencies were resolved"() {
        given:
        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        !lockFile.exists()
        !sha1File.exists()
    }

    def "does not write lock file if all dependencies failed to be resolved"() {
        given:
        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        !lockFile.exists()
        !sha1File.exists()
    }

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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    def "can write locks if at least one dependency is resolvable"() {
        given:
        mavenRepo.module('foo', 'bar', '1.5').publish()

        buildFile << mavenRepository(mavenRepo)
        buildFile << customConfigurations(MYCONF_CUSTOM_CONFIGURATION)
        buildFile << """
            dependencies {
                myConf 'foo:bar:1.5'
                myConf 'does.not:exist:1.2.3'
            }
        """
        buildFile << copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        when:
        failsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.+",
              "lockedVersion": "1.3"
            },
            {
              "moduleId": "org:gradle",
              "requestedVersion": "+",
              "lockedVersion": "7.5"
            },
            {
              "moduleId": "my:prod",
              "requestedVersion": "latest.release",
              "lockedVersion": "3.2.1"
            },
            {
              "moduleId": "dep:range",
              "requestedVersion": "[1.0,2.0]",
              "lockedVersion": "1.7.1"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.+",
              "lockedVersion": "1.3"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "a",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.+",
              "lockedVersion": "1.3"
            }
          ]
        },
        {
          "name": "b",
          "dependencies": [
            {
              "moduleId": "org:gradle",
              "requestedVersion": "7.5",
              "lockedVersion": "7.5"
            }
          ]
        },
        {
          "name": "c",
          "dependencies": [
            {
              "moduleId": "my:prod",
              "requestedVersion": "latest.release",
              "lockedVersion": "3.2.1"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:first",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            },
            {
              "moduleId": "foo:second",
              "requestedVersion": "1.6.7",
              "lockedVersion": "1.6.7"
            },
            {
              "moduleId": "foo:third",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            },
            {
              "moduleId": "bar:first",
              "requestedVersion": "2.+",
              "lockedVersion": "2.5"
            },
            {
              "moduleId": "bar:second",
              "requestedVersion": "2.6.7",
              "lockedVersion": "2.6.7"
            },
            {
              "moduleId": "bar:third",
              "requestedVersion": "2.5",
              "lockedVersion": "2.5"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:first",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            },
            {
              "moduleId": "foo:second",
              "requestedVersion": "1.9",
              "lockedVersion": "1.9"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == 'bfb3d6afb5b5b08b1dac96586ab76d014b3ba517'
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "compileClasspath",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            },
            {
              "moduleId": "org:gradle",
              "requestedVersion": "7.5",
              "lockedVersion": "7.5"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == '60509fcde47220bc2bbd808a5ecb8a5839fe323c'
    }

    def "can create locks for multi-project builds"() {
        given:
        mavenRepo.module('my', 'dep', '1.5').publish()
        mavenRepo.module('foo', 'bar', '2.3.1').publish()
        mavenRepo.module('other', 'company', '5.2').publish()

        buildFile << """
            subprojects {
                ${mavenRepository(mavenRepo)}
            }

            project(':a') {
                ${customConfigurations('conf1')}
    
                dependencies {
                    conf1 'my:dep:1.5'
                }

                ${copyLibsTask('conf1')}
            }

            project(':b') {
                ${customConfigurations('conf2')}

                dependencies {
                    conf2 'foo:bar:2.3.1'
                }

                ${copyLibsTask('conf2')}
            }

            project(':c') {
                ${customConfigurations('conf3')}

                dependencies {
                    conf3 'other:company:5.2'
                }

                ${copyLibsTask('conf3')}
            }
        """
        settingsFile << "include 'a', 'b', 'c'"

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":a",
      "configurations": [
        {
          "name": "conf1",
          "dependencies": [
            {
              "moduleId": "my:dep",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    },
    {
      "path": ":b",
      "configurations": [
        {
          "name": "conf2",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "2.3.1",
              "lockedVersion": "2.3.1"
            }
          ]
        }
      ]
    },
    {
      "path": ":c",
      "configurations": [
        {
          "name": "conf3",
          "dependencies": [
            {
              "moduleId": "other:company",
              "requestedVersion": "5.2",
              "lockedVersion": "5.2"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == 'ba328f04e6f66725791db639c04f9b09fe0b69da'
    }

    def "subsequent builds do not recreate lock file for unchanged dependencies"() {
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
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTaskSkipped(COPY_LIBS_TASK_PATH)
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'

        when:
        buildFile << """
            dependencies {
                myConf 'org:gradle:7.5'
            }
        """

        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            },
            {
              "moduleId": "org:gradle",
              "requestedVersion": "7.5",
              "lockedVersion": "7.5"
            }
          ]
        }
      ]
    }
  ]
}"""
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
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            },
            {
              "moduleId": "org:gradle",
              "requestedVersion": "7.5",
              "lockedVersion": "7.5"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == '972a625f8f26cb15c53a9962cdbc9230ae551aec'

        when:
        buildFile.text = mavenRepository(mavenRepo) + customConfigurations(MYCONF_CUSTOM_CONFIGURATION) + """
            dependencies {
                myConf 'foo:bar:1.5'
            }
        """ + copyLibsTask(MYCONF_CUSTOM_CONFIGURATION)

        succeedsWithEnabledDependencyLocking(COPY_LIBS_TASK_NAME)

        then:
        result.assertTasksExecuted(COPY_LIBS_TASK_PATH)
        lockFile.text == """{
${commentAndLockFileVersionJson()}
  "projects": [
    {
      "path": ":",
      "configurations": [
        {
          "name": "myConf",
          "dependencies": [
            {
              "moduleId": "foo:bar",
              "requestedVersion": "1.5",
              "lockedVersion": "1.5"
            }
          ]
        }
      ]
    }
  ]
}"""
        sha1File.text == '47ec5ad9e745cef18cea6adc42e4be3624572c9f'
    }

    private void succeedsWithEnabledDependencyLocking(String... tasks) {
        withEnabledDependencyLocking()
        succeeds(tasks)
    }

    private void failsWithEnabledDependencyLocking(String... tasks) {
        withEnabledDependencyLocking()
        fails(tasks)
    }

    private void withEnabledDependencyLocking() {
        args("--$DependencyLockOption.LONG_OPTION")
    }
}
