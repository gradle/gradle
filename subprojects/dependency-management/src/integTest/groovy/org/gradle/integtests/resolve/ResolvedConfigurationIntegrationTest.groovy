/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith
import spock.lang.Unroll

@RunWith(FluidDependenciesResolveRunner)
class ResolvedConfigurationIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        buildFile << """
            allprojects {
                apply plugin: "java"
            }
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
        """
    }

    @Unroll
    def "resolves strictly for #expression"() {
        settingsFile << "include 'child'"
        mavenRepo.module('org.foo', 'hiphop').publish()
        mavenRepo.module('org.foo', 'rock').dependsOnModules("some unresolved dependency").publish()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'unresolved.org:hiphopxx:3.0' //does not exist
                compile project(":child")

                compile 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task validate {
                doLast {
                    def compile = configurations.compile.resolvedConfiguration
                    assert compile.hasError()
                    println "evaluating:"
                    compile.${expression}
                }
            }
        """

        expect:
        fails "validate"
        outputContains("evaluating:") // ensure the failure happens when querying the resolved configuration
        failure.assertHasCause("Could not find unresolved.org:hiphopxx:3.0.")

        where:
        expression                                 | _
        "firstLevelModuleDependencies"             | _
        "getFirstLevelModuleDependencies { true }" | _
        "files { true }"                           | _
        "files"                                    | _
        "resolvedArtifacts"                        | _
    }

    def "resolves leniently"() {
        settingsFile << "include 'child'"
        mavenRepo.module('org.foo', 'hiphop').publish()
        mavenRepo.module('org.foo', 'rock').dependsOnModules("some unresolved dependency").publish()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'unresolved.org:hiphopxx:3.0' //does not exist
                compile project(":child")

                compile 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compile.resolvedConfiguration.lenientConfiguration

                    def resolved = compile.firstLevelModuleDependencies

                    assert resolved.size() == 3
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']
                    
                    resolved = compile.getFirstLevelModuleDependencies { true }
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']

                    def files = compile.files
                    
                    assert files.size() == 3
                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']
                    
                    files = compile.getFiles { true }
                    
                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']
                    
                    def artifacts = compile.artifacts

                    assert artifacts.size() == 3
                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']

                    artifacts = compile.getArtifacts { true }

                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar', 'rock-1.0.jar']

                    def unresolved = compile.unresolvedModuleDependencies
                    assert unresolved.size() == 2
                    assert unresolved.find { it.selector.group == 'unresolved.org' && it.selector.name == 'hiphopxx' && it.selector.version == '3.0' }
                    assert unresolved.find { it.selector.name == 'some unresolved dependency' }
                }
            }
        """

        expect:
        succeeds "validate"
    }

    def "lenient for both dependency and artifact resolve and download failures"() {
        settingsFile << "include 'child'"
        mavenRepo.module('org.foo', 'hiphop').publish()
        def m = mavenRepo.module('org.foo', 'rock').dependsOnModules("some unresolved dependency").publish()
        m.artifactFile.delete()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'unresolved.org:hiphopxx:3.0' //does not exist
                compile project(":child")

                compile 'org.foo:rock:1.0' //contains unresolved transitive dependency, plus missing jar
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compile.resolvedConfiguration.lenientConfiguration

                    def resolved = compile.firstLevelModuleDependencies

                    assert resolved.size() == 3
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']
                    
                    resolved = compile.getFirstLevelModuleDependencies { true }
                    assert resolved.collect { it.moduleName } == ['hiphop', 'child', 'rock']

                    def files = compile.files
                    
                    assert files.size() == 2
                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar']
                    
                    files = compile.getFiles { true }
                    
                    assert files.collect { it.name } == ['hiphop-1.0.jar', 'child.jar']
                    
                    def artifacts = compile.artifacts

                    assert artifacts.size() == 2
                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar']

                    artifacts = compile.getArtifacts { true }

                    assert artifacts.collect { it.file.name } == ['hiphop-1.0.jar', 'child.jar']

                    def unresolved = compile.unresolvedModuleDependencies
                    assert unresolved.size() == 2
                    assert unresolved.find { it.selector.group == 'unresolved.org' && it.selector.name == 'hiphopxx' && it.selector.version == '3.0' }
                    assert unresolved.find { it.selector.name == 'some unresolved dependency' }
                }
            }
        """

        expect:
        succeeds "validate"
    }

    def "resolves leniently from mixed confs"() {
        mavenRepo.module('org.foo', 'hiphop').publish()
        mavenRepo.module('org.foo', 'rock').dependsOnModules("some unresolved dependency").publish()

        buildFile << """
            configurations {
                someConf
            }

            dependencies {
                compile 'org.foo:hiphop:1.0'
                someConf 'org.foo:hiphopxx:1.0' //does not exist
            }

            task validate {
                doLast {
                    LenientConfiguration compile = configurations.compile.resolvedConfiguration.lenientConfiguration

                    def unresolved = compile.getUnresolvedModuleDependencies()
                    def resolved = compile.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

                    assert resolved.size() == 1
                    assert resolved.find { it.moduleName == 'hiphop' }
                    assert unresolved.size() == 0

                    LenientConfiguration someConf = configurations.someConf.resolvedConfiguration.lenientConfiguration

                    unresolved = someConf.getUnresolvedModuleDependencies()
                    resolved = someConf.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

                    assert resolved.size() == 0
                    assert unresolved.size() == 1
                    assert unresolved.find { it.selector.name == 'hiphopxx' }
                }
            }
        """

        expect:
        succeeds "validate"
    }
}
