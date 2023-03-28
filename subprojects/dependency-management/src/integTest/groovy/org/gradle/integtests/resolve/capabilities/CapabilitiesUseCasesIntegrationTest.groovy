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

class CapabilitiesUseCasesIntegrationTest extends AbstractModuleDependencyResolveTest {
    def setup() {
        buildFile << """
            configurations { conf }
        """
        executer.withStackTraceChecksDisabled()
    }

    /**
     * This use case corresponds to an external library which has 2 variants published at
     * different coordinates, and using both at the same time is illegal. The libraries
     * were not published using Gradle, so the consumer needs a way to express that and
     * enforce the use of only one of them at the same time.
     */
    def "can choose between cglib and cglib-nodep by declaring capabilities (#description)"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', details.id.version)
                         }
                     }
                }
            }

            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"

               components {
                  withModule('cglib:cglib-nodep', CapabilityRule)
               }
            }

            configurations.all {
                resolutionStrategy {
                    dependencySubstitution {
                        if ($fixConflict) {
                            substitute(module('cglib:cglib-nodep'))
                                .because('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                                .using(module('cglib:cglib:3.2.5'))
                        }
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib:3.2.5' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'cglib:cglib-nodep:3.2.5' {
                if (!fixConflict) {
                    expectGetMetadata()
                }
            }
        }
        if (fixConflict) {
            run ':checkDeps'
        } else {
            fails ':checkDeps'
        }

        then:
        if (fixConflict) {
            resolve.expectGraph {
                root(":", ":test:") {
                    module('cglib:cglib:3.2.5').selectedByRule('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                    edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5').selectedByRule('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                }
            }
        } else {
            def variant = 'runtime'
            if (!isGradleMetadataPublished() && useIvy()) {
                variant = 'default'
            }
            failure.assertHasCause("""Module 'cglib:cglib-nodep' has been rejected:
   Cannot select module with conflict on capability 'cglib:cglib:3.2.5' also provided by [cglib:cglib:3.2.5($variant)]""")
        }

        where:
        fixConflict | description
        false       | 'conflict fix not applied'
        true        | 'conflict fix applied'
    }

    /**
     * This use case corresponds to the case where a library maintainer publishes a library
     * either as individual modules, or using a fatjar. It's illegal to have both the fat jar and
     * individual modules on the classpath, so we need a way to declare that we prefer to use
     * the fat jar.
     *
     * This is from the consumer point of view, fixing the fact the library doesn't declare capabilities.
     */
    def "can select groovy-all over individual groovy-whatever (#description)"() {
        given:
        repository {
            'org.apache:groovy:1.0'()
            'org.apache:groovy-json:1.0'()
            'org.apache:groovy-xml:1.0'()
            'org.apache:groovy-all:1.0'()

            'org:a:1.0' {
                dependsOn 'org.apache:groovy:1.0'
                dependsOn 'org.apache:groovy-json:1.0'
            }
            'org:b:1.0' {
                dependsOn 'org.apache:groovy-all:1.0'
            }
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy', details.id.version)
                            addCapability('org.apache', 'groovy-json', details.id.version)
                        }
                     }
                }
            }

            dependencies {
               conf "org:a:1.0"
               conf "org:b:1.0"

               components {
                  withModule('org.apache:groovy-all', CapabilityRule)
               }

               // solution
               configurations.all {
                   resolutionStrategy {
                       dependencySubstitution {
                           if ($fixConflict) {
                              substitute module('org.apache:groovy') using module('org.apache:groovy-all:1.0')
                              substitute module('org.apache:groovy-json') using module('org.apache:groovy-all:1.0')
                           }
                       }
                   }
               }
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org:b:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org.apache:groovy-all:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            if (!fixConflict) {
                'org.apache:groovy:1.0' {
                    expectGetMetadata()
                }
                'org.apache:groovy-json:1.0' {
                    expectGetMetadata()
                }
            }
        }

        then:
        if (fixConflict) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org:a:1.0') {
                        edge('org.apache:groovy:1.0', 'org.apache:groovy-all:1.0')
                        edge('org.apache:groovy-json:1.0', 'org.apache:groovy-all:1.0') {
                            selectedByRule()
                        }
                    }
                    module('org:b:1.0') {
                        edge('org.apache:groovy-all:1.0', 'org.apache:groovy-all:1.0')
                    }
                }
            }
        } else {
            fails ':checkDeps'
            def variant = 'runtime'
            if (!isGradleMetadataPublished() && useIvy()) {
                variant = 'default'
            }
            failure.assertHasCause("""Module 'org.apache:groovy' has been rejected:
   Cannot select module with conflict on capability 'org.apache:groovy:1.0' also provided by [org.apache:groovy-all:1.0($variant)]""")
            failure.assertHasCause("""Module 'org.apache:groovy-json' has been rejected:
   Cannot select module with conflict on capability 'org.apache:groovy-json:1.0' also provided by [org.apache:groovy-all:1.0($variant)]""")
            failure.assertHasCause("""Module 'org.apache:groovy-all' has been rejected:
   Cannot select module with conflict on capability 'org.apache:groovy-json:1.0' also provided by [org.apache:groovy-json:1.0($variant)]""")
        }

        where:
        fixConflict | description
        false       | 'conflict fix not applied'
        true        | 'conflict fix applied'

    }

    /**
     * This use case corresponds to the case where a library maintainer publishes a library
     * either as individual modules, or using a fatjar. It's illegal to have both the fat jar and
     * individual modules on the classpath, so we need a way to declare that we prefer to use
     * the individual jars instead.
     *
     * It's worth mentioning that the _consumer_ must, in this case, make sure that all the features
     * actually used in the fatjar version are replaced with their appropriate module.
     *
     * This is from the consumer point of view, fixing the fact the library doesn't declare capabilities.
     */
    def "can select individual groovy-whatever over individual groovy-all (#description)"() {
        given:
        repository {
            'org.apache:groovy:1.0'()
            'org.apache:groovy-json:1.0'()
            'org.apache:groovy-xml:1.0'()
            'org.apache:groovy-all:1.0'()

            'org:a:1.0' {
                dependsOn 'org.apache:groovy:1.0'
                dependsOn 'org.apache:groovy-json:1.0'
            }
            'org:b:1.0' {
                dependsOn 'org.apache:groovy-all:1.0'
            }
        }

        buildFile << """
            class CapabilityRule implements ComponentMetadataRule {

                @Override
                void execute(ComponentMetadataContext context) {
                    def details = context.details
                    details.allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy', details.id.version)
                            addCapability('org.apache', 'groovy-json', details.id.version)
                        }
                    }
                }
            }

            dependencies {
               conf "org:a:1.0"
               conf "org:b:1.0"

               components {
                  withModule('org.apache:groovy-all', CapabilityRule)

                  // solution
                  configurations.all {
                      resolutionStrategy {
                          dependencySubstitution {
                              if ($fixConflict) { substitute module('org.apache:groovy-all') using module('org.apache:groovy-json:1.0') }
                          }
                      }
                  }
               }
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org:b:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org.apache:groovy:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org.apache:groovy-json:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org.apache:groovy-all:1.0' {
                if (!fixConflict) {
                    expectGetMetadata()
                }
            }
        }

        then:
        if (fixConflict) {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    module('org:a:1.0') {
                        edge('org.apache:groovy:1.0', 'org.apache:groovy:1.0')
                        edge('org.apache:groovy-json:1.0', 'org.apache:groovy-json:1.0') {
                            selectedByRule()
                        }
                    }
                    module('org:b:1.0') {
                        // this is not quite right, as we should replace with 2 edges
                        // one option to do it is to construct "adhoc" modules, and select an adhoc target in "prefer"
                        // where this adhoc target would have dependencies on groovy-json and groovy
                        edge('org.apache:groovy-all:1.0', 'org.apache:groovy-json:1.0')
                    }
                }
            }
        } else {
            fails ':checkDeps'
            def variant = 'runtime'
            if (!isGradleMetadataPublished() && useIvy()) {
                variant = 'default'
            }
            failure.assertHasCause("""Module 'org.apache:groovy' has been rejected:
   Cannot select module with conflict on capability 'org.apache:groovy:1.0' also provided by [org.apache:groovy-all:1.0($variant)]""")
            failure.assertHasCause("""Module 'org.apache:groovy-json' has been rejected:
   Cannot select module with conflict on capability 'org.apache:groovy-json:1.0' also provided by [org.apache:groovy-all:1.0($variant)]""")
            failure.assertHasCause("""Module 'org.apache:groovy-all' has been rejected:
   Cannot select module with conflict on capability 'org.apache:groovy-json:1.0' also provided by [org.apache:groovy-json:1.0($variant)]""")
        }

        where:
        fixConflict | description
        false       | 'conflict fix not applied'
        true        | 'conflict fix applied'
    }

    /**
     * This test highlights the case where published module declares a relocation. This is the drop-in replacement
     * for "replacedBy" rules when a module has been relocated, and that the publisher uses Gradle. There's the
     * ability to declare, in a newer version of the module, that it actually provides the same capability as an
     * older version published at different coordinates.
     *
     * This test also makes sure that the order in which dependencies are seen in the graph do not matter.
     */
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "published module can declare relocation (first in graph = #first, second in graph = #second, failOnVersionConflict=#failOnVersionConflict)"() {
        given:
        repository {
            // // there's an implicit capability for every component, corresponding to the component coordinates
            'asm:asm:3.0'()
            'org.ow2.asm:asm:4.0' {
                variant('runtime') {
                    capability('org.ow2.asm', 'asm', '4.0') // explicitly declare capability
                    capability('asm', 'asm', '4.0') // upgrades the asm capability
                }
            }
        }

        buildFile << """
            dependencies {
               conf "$first"
               conf "$second"
            }

            if ($failOnVersionConflict) {
               configurations.conf.resolutionStrategy.failOnVersionConflict()
            }
            configurations.conf.resolutionStrategy.capabilitiesResolution.all { selectHighestVersion() }
        """

        when:
        repositoryInteractions {
            'asm:asm:3.0' {
                expectGetMetadata()
            }
            'org.ow2.asm:asm:4.0' {
                failOnVersionConflict ? expectGetMetadata() : expectResolve()
            }
        }
        if (failOnVersionConflict) {
            fails ':checkDeps'
        } else {
            run ":checkDeps"
        }

        then:
        if (failOnVersionConflict) {
            failure.assertHasCause("Conflict(s) found for the following module(s):\n  - org.ow2.asm:asm latest version of capability asm:asm")
        } else {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge('asm:asm:3.0', 'org.ow2.asm:asm:4.0')
                        .byConflictResolution('latest version of capability asm:asm')
                    module('org.ow2.asm:asm:4.0')
                }
            }
        }

        where:
        first                 | second                | failOnVersionConflict
        'asm:asm:3.0'         | 'org.ow2.asm:asm:4.0' | false
        'org.ow2.asm:asm:4.0' | 'asm:asm:3.0'         | false
        'asm:asm:3.0'         | 'org.ow2.asm:asm:4.0' | true
        'org.ow2.asm:asm:4.0' | 'asm:asm:3.0'         | true
    }

    /**
     * This test illustrates that published modules can declare capabilities, which are then discovered
     * as we visit the graph. But using a module substitution rule, we can fix the problem.
     */

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "can express preference for capabilities declared in published modules (#description)"() {
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

            // fix the conflict between modules providing the same capability
            configurations.all {
                resolutionStrategy {
                   dependencySubstitution {
                      if ($fixConflict) { substitute module('org:testA') using module('org:testB:1.0') }
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:testB:1.0' {
                fixConflict ? expectResolve() : expectGetMetadata()
            }
            'org:testA:1.0' {
                if (!fixConflict) {
                    expectGetMetadata()
                }
            }
        }
        if (fixConflict) {
            run ":checkDeps"
        } else {
            fails ':checkDeps'
        }

        then:
        if (fixConflict) {
            resolve.expectGraph {
                root(":", ":test:") {
                    edge('org:testA:1.0', 'org:testB:1.0')
                        .selectedByRule()
                    module('org:testB:1.0')
                }
            }
        } else {
            failure.assertHasCause("""Module 'org:testA' has been rejected:
   Cannot select module with conflict on capability 'org.test:cap:1.0' also provided by [org:testB:1.0(runtime)]""")
            failure.assertHasCause("""Module 'org:testB' has been rejected:
   Cannot select module with conflict on capability 'org.test:cap:1.0' also provided by [org:testA:1.0(runtime)]""")
        }

        where:
        fixConflict | description
        false       | 'conflict fix not applied'
        true        | 'conflict fix applied'
    }
}
