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
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Unroll

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
    def "can choose between cglib and cglib-nodep by declaring capabilities"() {
        given:
        repository {
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               components {
                  withModule('cglib:cglib') {
                     allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', '3.2.5')
                         }
                     }
                  }
                  withModule('cglib:cglib-nodep') {
                     allVariants {
                         withCapabilities {
                             addCapability('cglib', 'cglib', '3.2.5')
                         }
                     }
                  }
               }
            }
            
            configurations.all {
                resolutionStrategy {
                    dependencySubstitution {
                        substitute(module('cglib:cglib-nodep'))
                            .because('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                            .with(module('cglib:cglib:3.2.5'))
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib:3.2.5' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('cglib:cglib:3.2.5').byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5').byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
            }
        }

    }

    /**
     * This use case corresponds to the case where a library maintainer publishes a library
     * either as individual modules, or using a fatjar. It's illegal to have both the fat jar and
     * individual modules on the classpath, so we need a way to declare that we prefer to use
     * the fat jar.
     *
     * This is from the consumer point of view, fixing the fact the library doesn't declare capabilities.
     */
    def "can select groovy-all over individual groovy-whatever"() {
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
            dependencies {
               conf "org:a:1.0"
               conf "org:b:1.0"
            
               components {
                  withModule('org.apache:groovy') {
                     allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy', '1.0')
                        }
                     }
                  }
                  withModule('org.apache:groovy-json') {
                     allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy-json', '1.0')
                        }
                     }
                  }
                  withModule('org.apache:groovy-all') {
                     allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy', '1.0')
                            addCapability('org.apache', 'groovy-json', '1.0')
                        }
                     }
                  }
               }               

               // solution
               configurations.all {
                   resolutionStrategy {
                       dependencySubstitution {
                           substitute module('org.apache:groovy') with module('org.apache:groovy-all:1.0')
                           substitute module('org.apache:groovy-json') with module('org.apache:groovy-all:1.0')
                       }
                   }
               }
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectResolve()
            }
            'org:b:1.0' {
                expectResolve()
            }
            'org.apache:groovy-all:1.0' {
                expectResolve()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:a:1.0') {
                    edge('org.apache:groovy:1.0', 'org.apache:groovy-all:1.0')
                    edge('org.apache:groovy-json:1.0', 'org.apache:groovy-all:1.0')
                }
                module('org:b:1.0') {
                    edge('org.apache:groovy-all:1.0', 'org.apache:groovy-all:1.0')
                }
            }
        }

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
    def "can select individual groovy-whatever over individual groovy-all"() {
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
            dependencies {
               conf "org:a:1.0"
               conf "org:b:1.0"
            
               components {
                  withModule('org.apache:groovy') {
                     allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy', '1.0')
                        }
                     }
                  }
                  withModule('org.apache:groovy-json') {
                     allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy-json', '1.0')
                        }
                     }
                  }
                  withModule('org.apache:groovy-all') {
                     allVariants {
                        withCapabilities {
                            addCapability('org.apache', 'groovy', '1.0')
                            addCapability('org.apache', 'groovy-json', '1.0')
                        }
                     }
                  }
                  
                  // solution
                  configurations.all {
                      resolutionStrategy {
                          dependencySubstitution {
                              substitute module('org.apache:groovy-all') with module('org.apache:groovy-json:1.0')
                          }
                      }
                  }
               } 
            }
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectResolve()
            }
            'org:b:1.0' {
                expectResolve()
            }
            'org.apache:groovy:1.0' {
                expectResolve()
            }
            'org.apache:groovy-json:1.0' {
                expectResolve()
            }
        }

        then:
        run ':checkDeps'
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:a:1.0') {
                    edge('org.apache:groovy:1.0', 'org.apache:groovy:1.0')
                    edge('org.apache:groovy-json:1.0', 'org.apache:groovy-json:1.0')
                }
                module('org:b:1.0') {
                    // this is not quite right, as we should replace with 2 edges
                    // one option to do it is to construct "adhoc" modules, and select an adhoc target in "prefer"
                    // where this adhoc target would have dependencies on groovy-json and groovy
                    edge('org.apache:groovy-all:1.0', 'org.apache:groovy-json:1.0')
                }
            }
        }
    }

    /**
     * This test highlights the case where published module declares a relocation. This is the drop-in replacement
     * for "replacedBy" rules when a module has been relocated, and that the publisher uses Gradle. There's the
     * ability to declare, in a newer version of the module, that it actually provides the same capability as an
     * older version published at different coordinates.
     *
     * This test also makes sure that the order in which dependencies are seen in the graph do not matter.
     */
    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll
    def "published module can declare relocation (first in graph = #first, second in graph = #second)"() {
        given:
        repository {
            'asm:asm:3.0' {
                variant('runtime') {
                    capability('asm', 'asm', '3.0')
                }
            }
            'org.ow2.asm:asm:4.0' {
                variant('runtime') {
                    capability('asm', 'asm', '4.0') // upgrades the asm capability
                    capability('org.ow2.asm', 'asm', '4.0') // self capability
                }
            }
        }

        buildFile << """
            dependencies {
               conf "$first"
               conf "$second"
            }
        """

        when:
        repositoryInteractions {
            'asm:asm:3.0' {
                expectGetMetadata()
            }
            'org.ow2.asm:asm:4.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('asm:asm:3.0', 'org.ow2.asm:asm:4.0')
                    .byConflictResolution()
                module('org.ow2.asm:asm:4.0')
            }
        }

        where:
        first << ['asm:asm:3.0', 'org.ow2.asm:asm:4.0']
        second << ['org.ow2.asm:asm:4.0', 'asm:asm:3.0']
    }


    /**
     * This test illustrates that published modules can declare capabilities, which are then discovered
     * as we visit the graph. But using a module substitution rule, we can fix the problem.
     */

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "can express preference for capabilities declared in published modules"() {
        given:
        repository {
            'org:testA:1.0' {
                variant('runtime') {
                    capability('cap')
                }
            }
            'org:testB:1.0' {
                variant('runtime') {
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
                      substitute module('org:testA') with module('org:testB:1.0')
                   }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:testB:1.0' {
                expectResolve()
            }
        }
        run ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:testA:1.0', 'org:testB:1.0')
                    .selectedByRule()
                module('org:testB:1.0')
            }
        }
    }

}
