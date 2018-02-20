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

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
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
                     prefer 'cglib:cglib' ${customReason ? "because '$customReason'" : ""}
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
                module('cglib:cglib:3.2.5').byReason(customReason ?: 'capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5').byReason(customReason ?: 'capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
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

    @Unroll
    def "provides a reasonable error message when module notation is wrong (providedBy=#providedBy, prefer=#prefer)"() {
        buildFile << """
            dependencies {
               capabilities {
                  capability('foo') {
                     providedBy '$providedBy'
                  
                     prefer '$prefer'
                  }
               }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause('Cannot convert the provided notation to an object of type ModuleIdentifier:')

        where:
        providedBy  | prefer
        'foo'       | 'foo:bar'
        'foo:bar'   | 'foo'
        'foo:bar:1' | 'foo:bar'
        'foo:bar'   | 'foo:bar:1'
    }


    def "fails with a reasonable error message when 2 modules provide the same capability"() {
        given:
        repository {
            'cglib:cglib-nodep:3.2.5'()
            'cglib:cglib:3.2.5'()
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               capabilities {
                  capability('cglib') {
                     providedBy 'cglib:cglib'
                     providedBy 'cglib:cglib-nodep'
                  }
               }
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
        failure.assertHasCause('Cannot choose between cglib:cglib or cglib:cglib-nodep because they provide the same capability: cglib')
    }

    @Unroll
    def "doesn't fail when 2 modules provide the same capability but only one is found in the graph"() {
        given:
        repository {
            'cglib:cglib-nodep:3.2.5'()
            'cglib:cglib:3.2.5'()
        }

        buildFile << """
            dependencies {
               conf "cglib:$variant:3.2.5"
            
               capabilities {
                  capability('cglib') {
                     providedBy 'cglib:cglib'
                     providedBy 'cglib:cglib-nodep'
                  }
               }
            }
        """

        when:
        repositoryInteractions {
            group('cglib') {
                module(variant) {
                    version('3.2.5') {
                        expectResolve()
                    }
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("cglib:$variant:3.2.5")
            }
        }

        where:
        variant << ['cglib', 'cglib-nodep']
    }

    def "capability conflict resolution doesn't prevent version conflict resolution from happening"() {
        repository {
            'cglib:cglib:3.1.0'()
            'cglib:cglib:3.2.5'()
            'cglib:cglib-nodep:3.2.5'()
            'org:test:1.0' {
                dependsOn 'cglib:cglib:3.2.5'
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib:3.1.0"
               conf "cglib:cglib-nodep:3.2.5"
               conf "org:test:1.0"
            
               capabilities {
                  capability('cglib') {
                     providedBy 'cglib:cglib'
                     providedBy 'cglib:cglib-nodep'
                     prefer 'cglib:cglib'
                  }
               }
            }
        """

        when:
        repositoryInteractions {
            'org:test:1.0' {
                expectResolve()
            }
            'cglib:cglib:3.2.5' {
                expectResolve()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('cglib:cglib:3.1.0', 'cglib:cglib:3.2.5')
                    .byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                    .byConflictResolution()
                edge('cglib:cglib-nodep:3.2.5', 'cglib:cglib:3.2.5')
                    .byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                module('org:test:1.0') {
                    module('cglib:cglib:3.2.5')
                        .byReason('capability cglib is provided by cglib:cglib and cglib:cglib-nodep')
                        .byConflictResolution()
                }
            }
        }
    }

    def "fails with a reasonable error message when there's no best selection for multiple capabilities"() {
        given:
        repository {
            'org:a:1.0'()
            'org:b:1.0'()
        }

        buildFile << """
            dependencies {
                conf 'org:a:1.0'
                conf 'org:b:1.0'
                
                capabilities {
                    capability('c1') {
                        providedBy 'org:a'
                        providedBy 'org:b'
                        
                        prefer 'org:a'
                    }
                    capability('c2') {
                        providedBy 'org:a'
                        providedBy 'org:b'
                        
                        prefer 'org:b'
                    }                
                }
            }
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Cannot choose between org:a or org:b because they provide the same capabilities (c1 and c2) but disagree on the preferred module")
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
            'asm:asm:3.0'()
            'org.ow2.asm:asm:4.0' {
                variant('api') {
                    capability('asm') {
                        providedBy 'asm:asm'
                        providedBy 'org.ow2.asm:asm'
                        prefer 'org.ow2.asm:asm'
                    }
                }
                variant('runtime') {
                    capability('asm') {
                        providedBy 'asm:asm'
                        providedBy 'org.ow2.asm:asm'
                        prefer 'org.ow2.asm:asm'
                    }
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
                    .byReason('capability asm is provided by asm:asm and org.ow2.asm:asm')
                module('org.ow2.asm:asm:4.0')
            }
        }

        where:
        first << ['asm:asm:3.0', 'org.ow2.asm:asm:4.0']
        second << ['org.ow2.asm:asm:4.0', 'asm:asm:3.0']
    }

    /**
     * This test highlights the case where published module declares a relocation. This is a different
     * from the case where the publisher expressed in the published module the fact that the module has
     * been relocated. Here, we want a 3rd party module to declare that actually those two things are the
     * same, and that one has been relocated.
     */
    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "external module can provide resolution of relocated module"() {
        given:
        repository {
            'asm:asm:3.0'()
            'org.ow2.asm:asm:4.0'()
            'org.test:platform:1.0' {
                capability('asm') {
                    providedBy 'asm:asm'
                    providedBy 'org.ow2.asm:asm'
                    prefer 'org.ow2.asm:asm'
                }
            }
        }

        buildFile << """

            dependencies {
               conf 'org.test:platform:1.0'
               conf "asm:asm:3.0"
               conf "org.ow2.asm:asm:4.0"
            }
        """

        when:
        repositoryInteractions {
            'org.test:platform:1.0' {
                expectResolve()
            }
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
                module('org.test:platform:1.0')
                edge('asm:asm:3.0', 'org.ow2.asm:asm:4.0')
                    .byReason('capability asm is provided by asm:asm and org.ow2.asm:asm')
                module('org.ow2.asm:asm:4.0')
            }
        }
    }

    /**
     * This test illustrates that published modules can declare capabilities, which are then discovered
     * as we visit the graph. And if no published module declares a preference, then build should fail.
     */
    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "fails with reasonable error message if no module express preference for conflict of modules that publish the same capability"() {
        given:
        repository {
            'org:testA:1.0' {
                capability('cap') {
                    providedBy 'org:testA'
                }
            }
            'org:testB:1.0' {
                capability('cap') {
                    providedBy 'org:testB'
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
        failure.assertHasCause("Cannot choose between org:testA or org:testB because they provide the same capability: cap")
    }

    /**
     * This test highlights the case where published module declares a relocation. This is a different
     * from the case where the publisher expressed in the published module the fact that the module has
     * been relocated. Here, we want a 3rd party module to declare that actually those two things are the
     * same, and that one has been relocated, but also show that this 3rd party module can be in a transitive
     * dependency.
     */
    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "external module can provide resolution of relocated module via transitive dependency"() {
        given:
        repository {
            'org:a:1.0' {
                dependsOn 'asm:asm:3.0'
            }
            'org:b:1.0' {
                dependsOn 'org.ow2.asm:asm:4.0'
            }
            'org:c:1.0' {
                dependsOn 'org.test:platform:1.0'
            }
            'asm:asm:3.0'()
            'org.ow2.asm:asm:4.0'()
            'org.test:platform:1.0' {
                capability('asm') {
                    providedBy 'asm:asm'
                    providedBy 'org.ow2.asm:asm'
                    prefer 'org.ow2.asm:asm'
                }
            }
        }

        buildFile << """

            dependencies {
               conf 'org:a:1.0'
               conf "org:b:1.0"
               conf "org:c:1.0"
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
            'org:c:1.0' {
                expectResolve()
            }
            'org.test:platform:1.0' {
                expectResolve()
            }
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
                module('org:a:1.0') {
                    edge('asm:asm:3.0', 'org.ow2.asm:asm:4.0')
                        .byReason('capability asm is provided by asm:asm and org.ow2.asm:asm')
                }
                module('org:b:1.0') {
                    module('org.ow2.asm:asm:4.0')
                }
                module('org:c:1.0') {
                    module('org.test:platform:1.0')
                }
            }
        }
    }


    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    @Unroll
    def "resolution from one configuration doesn't leak into a different configuration  (first in graph = #first, second in graph = #second)"() {
        given:
        repository {
            'asm:asm:3.0' {
                capability('asm') {
                    providedBy 'asm:asm'
                }
            }
            'org.ow2.asm:asm:4.0' {
                capability('asm') {
                    providedBy 'org.ow2.asm:asm'
                }
            }
            'org.test:platform:0.9' {
                capability('asm') {
                    prefer 'asm:asm'
                }
            }
            'org.test:platform:1.0' {
                capability('asm') {
                    prefer 'org.ow2.asm:asm'
                }
            }
        }

        buildFile << """

            configurations {
                common
                conf.extendsFrom(common)
                conf2.extendsFrom(common)
                conf3.extendsFrom(common)
            }

            dependencies {
               common "$first"
               common "$second"

               conf 'org.test:platform:1.0'
               conf2 'org.test:platform:0.9'
            }
            
            task checkConfigurations {
                doLast {
                    def first = configurations.conf.incoming.resolutionResult
                    def second = configurations.conf2.incoming.resolutionResult
                    
                    // first configuration chooses org.ow2.asm:asm
                    assert first.allComponents.find { (it.id instanceof ModuleComponentIdentifier) && it.id.group == 'org.ow2.asm' }
                    assert !first.allComponents.find { (it.id instanceof ModuleComponentIdentifier) && it.id.group == 'asm' }
                    
                    // second one chooses asm:asm
                    assert !second.allComponents.find { (it.id instanceof ModuleComponentIdentifier) && it.id.group == 'org.ow2.asm' }
                    assert second.allComponents.find { (it.id instanceof ModuleComponentIdentifier) && it.id.group == 'asm' }
                    
                    // conf3 should fail
                    try { 
                        def third = configurations.conf3.incoming.resolutionResult
                        assert false : "resolution should have failed"
                    } catch (ex) {
                        assert ex.cause.message.contains('Cannot choose between asm:asm or org.ow2.asm:asm because they provide the same capability: asm')
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'asm:asm:3.0' {
                allowAll()
            }
            'org.ow2.asm:asm:4.0' {
                allowAll()
            }
            'org.test:platform:0.9' {
                allowAll()
            }
            'org.test:platform:1.0' {
                allowAll()
            }
        }
        run "checkConfigurations"

        then:
        noExceptionThrown()

        where:
        first << ['asm:asm:3.0', 'org.ow2.asm:asm:4.0']
        second << ['org.ow2.asm:asm:4.0', 'asm:asm:3.0']
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    )
    def "can express preference for capabilities declared in published modules"() {
        given:
        repository {
            'cglib:cglib-nodep:3.2.5' {
                capability('cglib') {
                    providedBy 'cglib:cglib-nodep'
                }
            }
            'cglib:cglib:3.2.5' {
                capability('cglib') {
                    providedBy 'cglib:cglib'
                }
            }
        }

        buildFile << """
            dependencies {
               conf "cglib:cglib-nodep:3.2.5"
               conf "cglib:cglib:3.2.5"
            
               capabilities {
                  capability('cglib') {
                     prefer 'cglib:cglib'
                  }
               }
            }
        """

        when:
        repositoryInteractions {
            'cglib:cglib:3.2.5' {
                expectResolve()
            }
            'cglib:cglib-nodep:3.2.5' {
                expectGetMetadata()
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
}

