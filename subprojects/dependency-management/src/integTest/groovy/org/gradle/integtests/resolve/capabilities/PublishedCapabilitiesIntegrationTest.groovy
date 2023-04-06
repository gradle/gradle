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

package org.gradle.integtests.resolve.capabilities

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
class PublishedCapabilitiesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def "can consume published capabilities"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5' {
                variant("runtime") {
                    capability('cglib', 'cglib-nodep', '3.2.5')
                    capability('cglib', 'cglib', '3.2.5')
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.5' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Module 'cglib:cglib-nodep' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib:3.2.5(runtime)]""")
        failure.assertHasCause("""Module 'cglib:cglib' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib-nodep:3.2.5(runtime)]""")
    }

    def "can detect conflict with capability in different versions and upgrade to latest version (#rule)"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.4' {
                variant("runtime") {
                    capability('cglib', 'cglib-nodep', '3.2.4')
                    capability('cglib', 'cglib', '3.2.4')
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.4"
               conf "cglib:cglib:3.2.5"
            }

            configurations.conf.resolutionStrategy.capabilitiesResolution.$rule
        """

        when:
        repositoryInteractions {
            'cglib:cglib-nodep:3.2.4' {
                expectGetMetadata()
            }
            'cglib:cglib:3.2.5' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('cglib:cglib-nodep:3.2.4', 'cglib:cglib:3.2.5')
                    .byConflictResolution(reason)
                module('cglib:cglib:3.2.5')
            }
        }

        where:
        rule                                                                                  | reason
        'all { selectHighestVersion() }'                                                      | 'latest version of capability cglib:cglib'
        'withCapability("cglib:cglib") { selectHighestVersion() }'                            | 'latest version of capability cglib:cglib'
        'withCapability("cglib", "cglib") { selectHighestVersion() }'                         | 'latest version of capability cglib:cglib'
        'all { select(candidates.find { it.id.module == "cglib" }) because "custom reason" }' | 'On capability cglib:cglib custom reason'

    }

    def "can detect conflict between local project and capability from external dependency"() {
        given:
        repository {
            'org:test:1.0' {
                variant("runtime") {
                    capability('org', 'test', '1.0')
                    capability('org', 'capability', '1.0')
                }
            }
        }

        buildFile << """
            apply plugin: 'java-library'

            configurations.api.outgoing {
                capability 'org:capability:1.0'
            }

            dependencies {
                conf 'org:test:1.0'
            }

            configurations {
                conf.extendsFrom(api)
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Module 'org:test' has been rejected:
   Cannot select module with conflict on capability 'org:capability:1.0' also provided by [:test:unspecified(conf)]""")
    }

    /**
     * This test illustrates that published modules can declare capabilities, which are then discovered
     * as we visit the graph. And if no published module declares a preference, then build should fail.
     */
    def "fails with reasonable error message if no module express preference for conflict of modules that publish the same capability"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('cap')
                }
            }
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('cap')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:testA:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectGetMetadata()
            }
        }
        fails ":checkDeps"

        then:
        failure.assertHasCause("""Module 'org:testA' has been rejected:
   Cannot select module with conflict on capability 'org.test:cap:1.0' also provided by [org:testB:1.0(runtime)]""")
        failure.assertHasCause("""Module 'org:testB' has been rejected:
   Cannot select module with conflict on capability 'org.test:cap:1.0' also provided by [org:testA:1.0(runtime)]""")
    }

    def "considers all candidates for conflict resolution"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('org', 'cap', '1')
                }
            }
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('org', 'cap', '4')
                }
            }
            'org:testC:1.0' {
                dependsOn('org:testCC:1.0')
            }
            'org:testD:1.0' {
                dependsOn('org:testA:1.0') // must have a dependency on an evicted edge
            }
            'org:testCC:1.0' {
                variant('runtime') {
                    capability('org', 'testCC', '1.0')
                    capability('org', 'cap', '2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
                conf 'org:testC:1.0'
                conf 'org:testD:1.0'
            }

            configurations.conf.resolutionStrategy.capabilitiesResolution.all { selectHighestVersion() }

            tasks.register("dumpCapabilitiesFromArtifactView") {
                def artifactCollection = configurations.conf.incoming.artifactView {
                    attributes {
                        attribute(Attribute.of("artifactType", String), "jar")
                    }
                }.artifacts
                doFirst {
                    artifactCollection.artifacts.each {
                        println "Artifact: \${it.id.componentIdentifier.displayName}"
                        println "  - artifact: \${it.file}"

                        def capabilities = it.variant.capabilities
                        if (capabilities.isEmpty()) {
                            throw new IllegalStateException("Expected default capability to be explicit")
                        } else {
                            println "  - capabilities: " + capabilities.collect {
                                if (!(it instanceof org.gradle.internal.component.external.model.DefaultImmutableCapability)) {
                                    throw new IllegalStateException("Unexpected capability type: \$it")
                                }
                                "\${it}"
                            }
                        }
                        println("---------")
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:testA:1.0' {
                expectGetMetadata()
            }
            'org:testB:1.0' {
                expectResolve() // has the highest capability version
            }
            'org:testC:1.0' {
                expectResolve()
            }
            'org:testD:1.0' {
                expectResolve()
            }
            'org:testCC:1.0' {
                expectGetMetadata()
            }
        }
        succeeds ':checkDeps', ':dumpCapabilitiesFromArtifactView'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:testA:1.0', 'org:testB:1.0')
                    .byConflictResolution('latest version of capability org:cap')
                module('org:testB:1.0')
                module('org:testC:1.0') {
                    edge('org:testCC:1.0', 'org:testB:1.0')
                        .byConflictResolution('latest version of capability org:cap')
                }
                module('org:testD:1.0') {
                    edge('org:testA:1.0', 'org:testB:1.0')
                        .byConflictResolution('latest version of capability org:cap')
                }
            }
        }
        outputContains('capabilities: [capability group=\'org\', name=\'testB\', version=\'1.0\', capability group=\'org\', name=\'cap\', version=\'4\']')

    }

    def "can select a particular module participating do a capability conflict independently of the version (select #expected)"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('runtime') {
                    capability('org', 'testA', '1.0')
                    capability('org', 'cap', '1')
                }
            }
            'org:testB:1.0' {
                variant('runtime') {
                    capability('org', 'testB', '1.0')
                    capability('org', 'cap', '4')
                }
            }
            'org:testC:1.0' {
                dependsOn('org:testCC:1.0')
            }
            'org:testD:1.0' {
                dependsOn('org:testA:1.0') // must have a dependency on an evicted edge
            }
            'org:testCC:1.0' {
                variant('runtime') {
                    capability('org', 'testCC', '1.0')
                    capability('org', 'cap', '2')
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:testA:1.0'
                conf 'org:testB:1.0'
                conf 'org:testC:1.0'
                conf 'org:testD:1.0'
            }

            configurations.conf.resolutionStrategy.capabilitiesResolution.withCapability('org:cap') {
                select candidates.find { it.id.module == "$expected" }
                because "prefers module ${expected}"
            }
        """

        when:
        repositoryInteractions {
            'org:testA:1.0' {
                if (expected == 'testA') {
                    expectResolve()
                } else {
                    expectGetMetadata()
                }
            }
            'org:testB:1.0' {
                if (expected == 'testB') {
                    expectResolve()
                } else {
                    expectGetMetadata()
                }
            }
            'org:testC:1.0' {
                expectResolve()
            }
            'org:testD:1.0' {
                expectResolve()
            }
            'org:testCC:1.0' {
                expectGetMetadata()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                if (expected == 'testB') {
                    edge('org:testA:1.0', "org:$expected:1.0")
                        .byConflictResolution("On capability org:cap prefers module $expected")
                    module('org:testB:1.0')
                } else {
                    module('org:testA:1.0')
                    edge('org:testB:1.0', "org:testA:1.0")
                        .byConflictResolution("On capability org:cap prefers module $expected")
                }
                module('org:testC:1.0') {
                    edge('org:testCC:1.0', "org:$expected:1.0")
                        .byConflictResolution("On capability org:cap prefers module $expected")
                }
                module('org:testD:1.0') {
                    edge('org:testA:1.0', "org:$expected:1.0")
                        .byConflictResolution("On capability org:cap prefers module $expected")
                }
            }
        }

        where:
        expected << [
            'testA',
            'testB'
        ]

    }

}
