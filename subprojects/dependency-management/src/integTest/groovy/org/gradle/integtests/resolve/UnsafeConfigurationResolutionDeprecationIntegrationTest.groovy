/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution

class UnsafeConfigurationResolutionDeprecationIntegrationTest extends AbstractDependencyResolutionTest {
    @ToBeFixedForInstantExecution(because = "Task.getProject() during execution")
    def "deprecation warning when configuration in another project is resolved unsafely"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
            include ":bar"
        """

        buildFile << """
            task resolve {
                doLast {
                    println project(':bar').configurations.bar.files
                }
            }
            
            project(':bar') {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                
                configurations {
                    bar
                }
                
                dependencies {
                    bar "test:test-jar:1.0"
                }
            }       
        """

        when:
        executer.expectDeprecationWarning()
        executer.withArgument("--parallel")
        succeeds(":resolve")

        then:
        outputContains("The configuration :bar:bar was resolved without accessing the project in a safe manner.")
    }

    @ToBeFixedForInstantExecution
    def "exception when configuration is resolved from a non-gradle thread"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
            include(':bar')
        """

        buildFile << """
            task resolve {
                def thread = new Thread({
                    file('bar') << project(':bar').configurations.bar.files
                })
                doFirst {
                    thread.start()
                    thread.join()
                    // this should fail
                    assert file('bar').exists()
                }
            }
           
            project(':bar') {
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                
                configurations {
                    bar
                }
                
                dependencies {
                    bar "test:test-jar:1.0"
                }
            }    
        """

        when:
        executer.withArgument("--parallel")
        executer.withStackTraceChecksDisabled()
        fails(":resolve")

        then:
        failure.assertHasErrorOutput("The configuration :bar:bar was resolved from a thread not managed by Gradle.")
    }

    def "deprecation warning when configuration is resolved while evaluating a different project"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
            include ":bar", ":baz"
        """

        buildFile << """   
            project(':baz') {  
                repositories {
                    maven { url '${mavenRepo.uri}' }
                }
                
                configurations {
                    baz
                }
                
                dependencies {
                    baz "test:test-jar:1.0"
                }  
            } 
            
            project(':bar') {
                println project(':baz').configurations.baz.files
            }       
        """

        when:
        executer.expectDeprecationWarning()
        executer.withArgument("--parallel")
        succeeds(":bar:help")

        then:
        outputContains("The configuration :baz:baz was resolved without accessing the project in a safe manner.")
    }

    def "no deprecation warning when configuration is resolved while evaluating same project"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
            include ":bar"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            
            configurations {
                foo
            }
            
            dependencies {
                foo "test:test-jar:1.0"
            }
            
            println configurations.foo.files      
        """

        expect:
        executer.withArgument("--parallel")
        succeeds(":bar:help")
    }

    def "no deprecation warning when configuration is resolved while evaluating afterEvaluate block"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            
            configurations {
                foo
            }
            
            dependencies {
                foo "test:test-jar:1.0"
            }
            
            afterEvaluate {
                println configurations.foo.files      
            }
        """

        expect:
        executer.withArgument("--parallel")
        succeeds(":help")
    }

    def "no deprecation warning when configuration is resolved while evaluating beforeEvaluate block"() {
        mavenRepo.module("test", "test-jar", "1.0").publish()

        settingsFile << """
            rootProject.name = "foo"
        """

        file('init-script.gradle') << """
            allprojects {
                beforeEvaluate {
                    repositories {
                        maven { url '${mavenRepo.uri}' }
                    }
                    
                    configurations {
                        foo
                    }
                    
                    dependencies {
                        foo "test:test-jar:1.0"
                    }
                
                    println configurations.foo.files      
                }
            }
        """

        expect:
        executer.withArguments("--parallel", "-I", "init-script.gradle")
        succeeds(":help")
    }
}
