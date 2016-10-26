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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import spock.lang.Unroll

class ConfigurationBuildDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        settingsFile << "include 'child'"
        buildFile << """
            allprojects {
                configurations {
                    compile
                    create('default').extendsFrom compile
                }
            }
            task useCompileConfiguration { 
                inputs.files configurations.compile
                outputs.file file('output.txt')
                doLast { }
            }
"""
    }

    @Unroll
    def "reports failure to calculate build dependencies of artifact - fluid: #fluid"() {
        makeFluid(fluid)
        buildFile << """
            dependencies {
                compile project(':child')
            }
            project(':child') {
                artifacts {
                    compile file: file('thing.txt'), builtBy: { throw new RuntimeException('broken') }
                }
            }
"""

        expect:
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause('broken')

        where:
        fluid << [true, false]
    }

    @Unroll
    def "reports failure to calculate build dependencies of file dependency - fluid: #fluid"() {
        makeFluid(fluid)
        buildFile << """
            dependencies {
                compile project(':child')
            }
            project(':child') {
                dependencies {
                    compile files({ throw new RuntimeException('broken') })
                }
            }
"""

        expect:
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause('broken')

        where:
        fluid << [true, false]
    }

    @NotYetImplemented
    def "reports failure to find build dependencies of file dependency when using fluid dependencies"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        makeFluid(true)
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
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
"""

        expect:
        module.pom.expectGetBroken()
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not get resource '${module.pom.uri}'")
    }

    def "does not download anything when task dependencies are calculated for configuration that is used as a task input"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
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
"""

        when:
        executer.withArgument("--dry-run")
        run 'useCompileConfiguration'

        then:
        server.resetExpectations()

        when:
        // Expect downloads when task executed
        module.pom.expectGet()
        module.artifact.expectGet()
        run 'useCompileConfiguration'

        then:
        result.assertTasksExecuted(":child:jar", ":useCompileConfiguration")
    }

    void makeFluid(boolean fluid) {
        if (fluid) {
            buildFile << """
allprojects { configurations.all { resolutionStrategy.assumeFluidDependencies() } }
"""
        }
    }
}
