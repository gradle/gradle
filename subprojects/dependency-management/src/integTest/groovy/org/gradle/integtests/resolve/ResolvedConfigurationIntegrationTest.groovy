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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.FluidDependenciesResolveRunner
import org.junit.runner.RunWith

@RunWith(FluidDependenciesResolveRunner)
public class ResolvedConfigurationIntegrationTest extends AbstractIntegrationSpec {
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

                    def unresolved = compile.getUnresolvedModuleDependencies()
                    def resolved = compile.getFirstLevelModuleDependencies(Specs.SATISFIES_ALL)

                    assert resolved.size() == 3
                    assert resolved.find { it.moduleName == 'hiphop' }
                    assert resolved.find { it.moduleName == 'rock' }
                    assert resolved.find { it.moduleName == 'child' }

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
