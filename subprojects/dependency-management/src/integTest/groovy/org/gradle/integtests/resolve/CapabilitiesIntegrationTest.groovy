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

import spock.lang.Unroll

class CapabilitiesIntegrationTest extends AbstractModuleDependencyResolveTest {

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
    @Unroll
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
            
               capabilities {
                  capability('cglib') {
                     providedBy 'cglib:cglib'
                     providedBy 'cglib:cglib-nodep'
                     prefer 'cglib:cglib' ${customReason?"because '$customReason'":""}
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
                module('cglib:cglib:3.2.5').byReason(customReason?:'capability cglib is provided by cglib:cglib-nodep and cglib:cglib')
                edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5').byReason(customReason?:'capability cglib is provided by cglib:cglib-nodep and cglib:cglib')
            }
        }

        where:
        customReason << [null, 'avoids a conflict with ASM']
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
            
               capabilities {
                  capability('groovy') {
                     providedBy 'org.apache:groovy'
                     prefer 'org.apache:groovy-all'
                  }
                  capability('groovy-json') {
                     providedBy 'org.apache:groovy-json'
                     prefer 'org.apache:groovy-all'
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
                expectGetMetadata()
            }
            'org.apache:groovy-json:1.0' {
                expectGetMetadata()
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
            
               capabilities {
                  capability('groovy-json') {
                     providedBy 'org.apache:groovy-json'
                     providedBy 'org.apache:groovy-all'
                  
                     prefer 'org.apache:groovy-json'
                  }
                  
                  capability('groovy') {
                     providedBy 'org.apache:groovy'
                     providedBy 'org.apache:groovy-all'
                  
                     prefer 'org.apache:groovy'
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
}
