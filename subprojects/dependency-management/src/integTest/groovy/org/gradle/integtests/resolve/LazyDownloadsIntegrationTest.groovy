/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.server.http.CyclicBarrierHttpServer
import org.junit.Rule

class LazyDownloadsIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @Rule
    CyclicBarrierHttpServer sync = new CyclicBarrierHttpServer()

    def "does not download anything when task dependencies are calculated for configuration that is used as a task input"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        settingsFile << "include 'child'"
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
                configurations {
                    compile
                    create('default').extendsFrom compile
                }
            }
            
            dependencies {
                compile project(':child')
            }
            project(':child') {
                task jar { 
                    outputs.files file('thing.jar')
                }
                artifacts {
                    compile file: jar.outputs.files.singleFile, builtBy: jar
                }
                dependencies {
                    compile 'test:test:1.0'
                }                
            }
            gradle.taskGraph.whenReady {
                new URL('$sync.uri').text
            }
            task useCompileConfiguration { 
                inputs.files configurations.compile
                outputs.file file('output.txt')
                doLast { }
            }
            
"""

        when:
        executer.withTasks('useCompileConfiguration')
        def build = executer.start()
        // Task graph calculated but nothing executed yet
        sync.waitFor()

        then:
        // Nothing downloaded
        server.resetExpectations()

        when:
        // Expect downloads once execution starts
        module.pom.expectGet()
        module.artifact.expectGet()
        sync.release()
        def result = build.waitForFinish()

        then:
        result.assertTasksExecuted(":child:jar", ":useCompileConfiguration")
    }

    def "downloads metadata only when dependency graph is queried"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        settingsFile << "include 'child'"
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
                configurations {
                    compile
                    create('default').extendsFrom compile
                }
            }
            
            dependencies {
                compile project(':child')
            }
            project(':child') {
                dependencies {
                    compile 'test:test:1.0'
                }                
            }
            
            configurations.compile.incoming.resolutionResult
"""

        expect:
        module.pom.expectGet()
        succeeds()
    }
}
