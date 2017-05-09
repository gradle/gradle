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

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith
import spock.lang.Unroll

@RunWith(FluidDependenciesResolveRunner)
class ResolvedConfigurationIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        buildFile << """
            allprojects {
                apply plugin: "java"
            }
            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }
        """
    }

    @Unroll
    def "resolves strictly for dependency resolve failures when #expression is used"() {
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown');
        def m3 = mavenHttpRepo.module('org.foo', 'broken');
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'org.foo:unknown:1.0' //does not exist
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

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetBroken()
        m4.allowAll()

        expect:
        fails "validate"
        outputContains("evaluating:") // ensure the failure happens when querying the resolved configuration
        failure.assertHasCause("Could not find org.foo:unknown:1.0.")
        failure.assertHasCause("Could not resolve org.foo:broken:1.0.")

        where:
        expression                                 | _
        "firstLevelModuleDependencies"             | _
        "getFirstLevelModuleDependencies { true }" | _
        "files { true }"                           | _
        "files"                                    | _
        "resolvedArtifacts"                        | _
    }

    @Unroll
    def "resolves strictly for artifact resolve failures when #expression is used"() {
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown').publish();
        def m3 = mavenHttpRepo.module('org.foo', 'broken').publish();
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'org.foo:unknown:1.0' //does not exist
                compile project(":child")

                compile 'org.foo:rock:1.0' //contains unresolved transitive dependency
            }

            task validate {
                doLast {
                    def compile = configurations.compile.resolvedConfiguration

                    assert !compile.hasError() // all dependencies resolved ok
                    assert compile.lenientConfiguration.unresolvedModuleDependencies.empty
                    assert compile.resolvedArtifacts.size() == 5 // Does not filter broken or missing files

                    println "evaluating:"
                    compile.${expression}
                }
            }
        """

        m1.allowAll()
        m2.pom.expectGet()
        m2.artifact.expectGetMissing()
        m3.pom.expectGet()
        m3.artifact.expectGetBroken()
        m4.allowAll()

        expect:
        fails "validate"
        outputContains("evaluating:") // ensure the failure happens when querying the resolved configuration
        failure.assertHasCause("Could not find unknown.jar (org.foo:unknown:1.0).")
        failure.assertHasCause("Could not download broken.jar (org.foo:broken:1.0)")

        where:
        expression                                 | _
        "files { true }"                           | _
        "files"                                    | _
    }

    def "resolves leniently for dependency resolve failures"() {
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown');
        def m3 = mavenHttpRepo.module('org.foo', 'broken');
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'org.foo:unknown:1.0' //does not exist
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
                    assert unresolved.find { it.selector.group == 'org.foo' && it.selector.name == 'unknown' && it.selector.version == '1.0' }
                    assert unresolved.find { it.selector.name == 'broken' }
                }
            }
        """

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetBroken()
        m4.allowAll()

        expect:
        succeeds "validate"
    }

    def "lenient for both dependency and artifact resolve and download failures"() {
        settingsFile << "include 'child'"
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown');
        def m3 = mavenHttpRepo.module('org.foo', 'broken');
        def m4 = mavenHttpRepo.module('org.foo', 'rock').dependsOn(m3).publish()

        buildFile << """
            dependencies {
                compile 'org.foo:hiphop:1.0'
                compile 'org.foo:unknown:1.0' //does not exist
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
                    assert unresolved.find { it.selector.group == 'org.foo' && it.selector.name == 'unknown' && it.selector.version == '1.0' }
                    assert unresolved.find { it.selector.name == 'broken' }
                }
            }
        """

        m1.allowAll()
        m2.allowAll()
        m3.pom.expectGetBroken()
        m4.pom.expectGet()
        m4.artifact.expectGetBroken()
        // TODO: should only query once and reuse the result
        m4.artifact.expectGetBroken()
        m4.artifact.expectGetBroken()
        m4.artifact.expectGetBroken()

        expect:
        succeeds "validate"
    }

    def "resolves leniently from mixed confs"() {
        def m1 = mavenHttpRepo.module('org.foo', 'hiphop').publish()
        def m2 = mavenHttpRepo.module('org.foo', 'unknown');

        buildFile << """
            configurations {
                someConf
            }

            dependencies {
                compile 'org.foo:hiphop:1.0'
                someConf 'org.foo:unknown:1.0' //does not exist
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
                    assert unresolved.find { it.selector.name == 'unknown' }
                }
            }
        """

        m1.allowAll()
        m2.allowAll()

        expect:
        succeeds "validate"
    }
}
