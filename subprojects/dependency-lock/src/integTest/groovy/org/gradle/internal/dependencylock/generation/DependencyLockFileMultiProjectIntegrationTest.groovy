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

class DependencyLockFileMultiProjectIntegrationTest extends AbstractDependencyLockFileGenerationIntegrationTest {

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
        assertLockFileAndHashFileExist()
        def parsedLockFile = parseLockFile()
        def conf1Locks = parsedLockFile.getLocks(':a', 'conf1')
        conf1Locks.size() == 1
        conf1Locks[0].toString() == 'my:dep -> 1.5'
        def conf2Locks = parsedLockFile.getLocks(':b', 'conf2')
        conf2Locks.size() == 1
        conf2Locks[0].toString() == 'foo:bar -> 2.3.1'
        def conf3Locks = parsedLockFile.getLocks(':c', 'conf3')
        conf3Locks.size() == 1
        conf3Locks[0].toString() == 'other:company -> 5.2'
    }

    def "does not record project dependencies in lock file"() {
        given:
        mavenRepo.module('my', 'dep', '1.5').publish()
        mavenRepo.module('foo', 'bar', '2.3.1').publish()

        buildFile << """
            subprojects {
                apply plugin: 'java'
                ${mavenRepository(mavenRepo)}
            }

            project(':a') {
                dependencies {
                    compile 'my:dep:1.5'
                }
            }

            project(':b') {
                dependencies {
                    compile 'foo:bar:2.3.1'
                    compile project(':a')
                }
            }
        """
        settingsFile << "include 'a', 'b'"
        file('a/src/main/java/ClassA.java') << javaClass('ClassA')
        file('b/src/main/java/ClassB.java') << javaClass('ClassB')

        when:
        succeedsWithEnabledDependencyLocking('compileJava')

        then:
        assertLockFileAndHashFileExist()
        def parsedLockFile = parseLockFile()
        def compileLocksProjectA = parsedLockFile.getLocks(':a', 'compileClasspath')
        compileLocksProjectA.size() == 1
        compileLocksProjectA[0].toString() == 'my:dep -> 1.5'
        def compileLocksProjectB = parsedLockFile.getLocks(':b', 'compileClasspath')
        compileLocksProjectB.size() == 2
        compileLocksProjectB[0].toString() == 'foo:bar -> 2.3.1'
        compileLocksProjectB[1].toString() == 'my:dep -> 1.5'
    }

    static String javaClass(String className) {
        """
            public class $className {}
        """
    }
}
