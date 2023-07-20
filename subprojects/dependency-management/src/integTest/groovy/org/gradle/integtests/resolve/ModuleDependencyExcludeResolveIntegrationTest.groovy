/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.Issue

/**
 * Demonstrates the resolution of dependency excludes in published module metadata.
 */
@IgnoreIf({
    // This test is very expensive. Ideally we shouldn't need an integration test here, but lack the
    // infrastructure to simulate everything done here, so we're only going to execute this test in
    // embedded mode
    !GradleContextualExecuter.embedded
})
class ModuleDependencyExcludeResolveIntegrationTest extends AbstractModuleDependencyResolveTest {
    def setup() {
        buildFile << """
dependencies {
    conf 'a:a:1.0'
}

task check(type: Sync) {
    from configurations.conf
    into 'libs'
}
"""
    }

    protected void succeedsDependencyResolution() {
        succeeds 'check'
    }

    protected void assertResolvedFiles(List<String> files) {
        file('libs').assertHasDescendants(files as String[])
    }


    /**
    * Dependency exclude for a single artifact by using a combination of exclude rules.
    *
    * Dependency graph:
    * a -> b, c
    */
   def "dependency exclude that does not match transitive dependency is ignored"() {
       given:
       repository {
           'a:a:1.0' {
               dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: [excludeAttributes]
               dependsOn 'c:c:1.0'
           }
           'b:b:1.0' {}
           'c:c:1.0' {}
       }

       repositoryInteractions {
           'a:a:1.0' {expectResolve()}
           'b:b:1.0' {expectResolve()}
           'c:c:1.0' {expectResolve()}
       }

       when:
       succeeds "checkDep"

       then:
       resolve.expectGraph {
           root(":", ":test:") {
               module("a:a:1.0") {
                   module("b:b:1.0")
                   module("c:c:1.0")
               }
           }
       }

       where:
       condition             | excludeAttributes
       'non-matching module' | [module: 'other']
       'non-matching module' | [group: 'other']
       'sibling module'      | [module: 'c']
       'sibling group'       | [group: 'c']
       'self module'         | [module: 'b']
       'self group'          | [group: 'b']
   }


    /**
     * Exclude of transitive dependency with a single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> e
     *
     * Exclude is applied to dependency a->b
     */
    def "dependency exclude for group or module applies to child module of dependency (#excluded)"() {
        given:
        def expectResolved = ['a', 'b', 'c', 'd', 'e'] - expectExcluded
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: [excludeAttributes]
                dependsOn 'c:c:1.0'
            }
            'b:b:1.0' {
                dependsOn 'd:d:1.0'
            }
            'c:c:1.0' {
                dependsOn 'e:e:1.0'
            }
            'd:d:1.0'()
            'e:e:1.0'()
        }

        repositoryInteractions {
            expectResolved.each {
                "${it}:${it}:1.0" { expectResolve() }
            }
        }

        when:
        succeedsDependencyResolution()

        then:
        def resolvedJars = expectResolved.collect { it + '-1.0.jar'}
        assertResolvedFiles(resolvedJars)

        where:
        excluded                 | excludeAttributes | expectExcluded
        'non-matching module'    | [module: 'other'] | []
        'non-matching group'     | [group: 'other']  | []
        'child name of sibling'  | [module: 'e']     | []
        'child group of sibling' | [group: 'e']      | []
        'all child modules'      | [module: '*']     | ['d']
        'all child groups'       | [group: '*']      | ['d']
        'name child module'      | [module: 'd']     | ['d']
        'name child group'       | [group: 'd']      | ['d']
    }

    /**
     * Wildcard exclude of all transitive dependencies.
     *
     * Dependency graph:
     * a -> b
     * b -> c, d
     *
     * Wildcard exclude is applied to dependency a->b
     */
    @Issue("https://issues.gradle.org/browse/GRADLE-3243")
    def "wildcard exclude of group and module results in non-transitive dependency"() {
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: [[group: '*', module: '*']]
            }
            'b:b:1.0' {
                dependsOn 'c:c:1.0'
                dependsOn 'd:d:1.0'
            }
            'c:c:1.0'()
            'd:d:1.0'()
        }

        repositoryInteractions {
            'a:a:1.0' {expectResolve()}
            'b:b:1.0' {expectResolve()}
        }

        when:
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("a:a:1.0") {
                    module("b:b:1.0")
                }
            }
        }
    }

    /**
     * Exclude of selected transitive dependencies.
     *
     * Dependency graph:
     * a -> b
     * b -> c, d
     *
     * Selective exclusions are applied to dependency a->b
     */
    def "can exclude transitive dependencies (#condition)"() {
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: excludes
            }
            'b:b:1.0' {
                dependsOn 'c:c:1.0'
                dependsOn 'd:d:1.0'
            }
            'c:c:1.0'()
            'd:d:1.0'()
        }

        repositoryInteractions {
            expectResolved.each {
                "${it}:${it}:1.0" { expectResolve() }
            }
        }

        when:
        succeedsDependencyResolution()

        then:
        def resolvedJars = expectResolved.collect { it + '-1.0.jar'}
        assertResolvedFiles(resolvedJars)

        where:
        condition         | excludes                       | expectResolved
        'exclude c'       | [[module: 'c']]                | ['a', 'b', 'd']
        'exclude d'       | [[module: 'd']]                | ['a', 'b', 'c']
        'exclude both'    | [[module: 'c'], [module: 'd']] | ['a', 'b']
        'exclude neither' | [[module: 'other']]            | ['a', 'b', 'c', 'd']
    }

    /**
     * Exclude of transitive dependency involved in a dependency cycle.
     *
     * Dependency graph:
     * a -> b -> c -> d -> c
     *
     * 'c' is excluded on dependency a->b
     */
    def "can excluded module involved in dependency cycle"() {
        given:
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: [[module: 'c']]
            }
            'b:b:1.0' {
                dependsOn 'c:c:1.0'
            }
            'c:c:1.0' {
                dependsOn 'd:d:1.0'
            }
            'd:d:1.0'() {
                dependsOn 'c:c:1.0'
            }
        }
        repositoryInteractions {
            'a:a:1.0' { expectResolve() }
            'b:b:1.0' { expectResolve() }
        }


        when:
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("a:a:1.0") {
                    module("b:b:1.0")
                }
            }
        }
    }

    /**
     * When a module artifact is depended on via multiple paths and excluded on one of those paths, it is not excluded.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    def "when a module artifact is depended on via multiple paths it is only excluded if excluded on all paths"() {
        given:
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: bExcludes
                dependsOn group: 'c', artifact: 'c', version: '1.0', exclusions: cExcludes
            }
            'b:b:1.0' {
                dependsOn 'd:d:1.0'
            }
            'c:c:1.0' {
                dependsOn 'd:d:1.0'
            }
            'd:d:1.0'()
        }
        repositoryInteractions {
            expectResolved.each {
                "${it}:${it}:1.0" { expectResolve() }
            }
        }

        when:
        succeedsDependencyResolution()

        then:
        def resolvedJars = expectResolved.collect { it + '-1.0.jar'}
        assertResolvedFiles(resolvedJars)

        where:
        condition          | bExcludes       | cExcludes       | expectResolved
        'excluded on b'    | [[module: 'd']] | []              | ['a', 'b', 'c', 'd']
        'excluded on c'    | []              | [[module: 'd']] | ['a', 'b', 'c', 'd']
        'excluded on both' | [[module: 'd']] | [[module: 'd']] | ['a', 'b', 'c']
    }

    /**
     * When a module is depended on via a single chained path, it is excluded if excluded on any of the links in that path.
     *
     * Dependency graph:
     * a -> b -> c -> d
     */
    def "when a module is depended on via a single chained path, it is excluded if excluded on any of the links in that path (#condition)"() {
        given:
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: bExcludes
            }
            'b:b:1.0' {
                dependsOn group: 'c', artifact: 'c', version: '1.0', exclusions: cExcludes
            }
            'c:c:1.0' {
                dependsOn 'd:d:1.0'
            }
            'd:d:1.0'()
        }
        repositoryInteractions {
            expectResolved.each {
                "${it}:${it}:1.0" { expectResolve() }
            }
        }

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(expectResolved.collect { it + '-1.0.jar'})

        where:
        condition             | bExcludes       | cExcludes       | expectResolved
        'excluded on b'       | [[module: 'd']] | []              | ['a', 'b', 'c']
        'excluded on c'       | []              | [[module: 'd']] | ['a', 'b', 'c']
        'excluded on both'    | [[module: 'd']] | [[module: 'd']] | ['a', 'b', 'c']
        'excluded on neither' | []              | []              | ['a', 'b', 'c', 'd']
    }

    def "excludes are retained in cached module metadata"() {
        given:
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: [[module: 'c']]
            }
            'b:b:1.0' {
                dependsOn 'c:c:1.0'
            }
            'c:c:1.0'()
        }

        repositoryInteractions {
            'a:a:1.0' {expectResolve()}
            'b:b:1.0' {expectResolve()}
        }

        and: // Initial request to cache metadata
        succeeds "checkDeps"

        when:
        server.resetExpectations()
        succeeds "checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("a:a:1.0") {
                    module("b:b:1.0")
                }
            }
        }
    }

    // this test documents current behavior; it is debatable if it is also the desired behavior
    def "excludes of conflicting versions are merged"() {
        given:
        repository {
            'a:a:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '1.0', exclusions: [[module: 'e']]
                dependsOn 'c:c:1.0'
            }
            'c:c:1.0' {
                dependsOn group: 'b', artifact: 'b', version: '2.0', exclusions: [[module: 'd'], [module: 'e']]
            }
            'b:b:1.0' {
                dependsOn 'd:d:1.0'
                dependsOn 'e:e:1.0'
            }
            'b:b:2.0' {
                dependsOn 'd:d:1.0'
                dependsOn 'e:e:1.0'
            }

            'd:d:1.0'()
            'e:e:1.0'()
        }
        repositoryInteractions {
            'a:a:1.0' { expectResolve() }
            'b:b:1.0' { expectGetMetadata() }
            'b:b:2.0' { expectResolve() }
            'c:c:1.0' { expectResolve() }
            'd:d:1.0' { expectResolve() }
        }

        when:
        succeeds "checkDep"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("a:a:1.0") {
                    edge("b:b:1.0", "b:b:2.0")
                    module("c:c:1.0") {
                        module("b:b:2.0") {
                            byConflictResolution("between versions 2.0 and 1.0")
                            // 'd' is NOT excluded, because the exclude in 'a:a:1.0 --depends-on--> b:b:1.0' only excludes 'e'
                            module("d:d:1.0")
                        }
                    }
                }
            }
        }
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'dependency with same selector but different excludes is taken into account'() {
        given:
        repository {
            'org.test:platform:1.0' {
                constraint('org.test:depA:1.0')
                asPlatform()
            }

            'org.test:depA:1.0'()
            'org.test:depB:1.0' {
                dependsOn  'org.test:depA'
            }
            'org.test:depC:1.0' {
                dependsOn 'org.test:depB:1.0'
            }
            'org.test:depD:1.0' {
                dependsOn  group:'org.test', artifact: 'depC', version: '1.0', exclusions: [[module: 'depA']]
            }
            'org.test:depE:1.0' {
                dependsOn  'org.test:depC:1.0'
            }
            'org.test:depF:1.0' {
                dependsOn  'org.test:depE:1.0'
            }
        }

        repositoryInteractions {
            'org.test:platform:1.0' {
                expectGetMetadata()
            }
            'org.test:depA:1.0' {
                expectResolve()
            }
            'org.test:depB:1.0' {
                expectResolve()
            }
            'org.test:depC:1.0' {
                expectResolve()
            }
            'org.test:depD:1.0' {
                expectResolve()
            }
            'org.test:depE:1.0' {
                expectResolve()
            }
            'org.test:depF:1.0' {
                expectResolve()
            }
        }

        buildFile << """
            configurations {
                conf.dependencies.clear()
            }

            dependencies {
                conf(platform('org.test:platform:1.0'))
                conf 'org.test:depD:1.0'
                conf 'org.test:depF:1.0'
            }
"""
        when:
        succeeds 'checkDeps'

        then:
        def plainMaven = !isGradleMetadataPublished()
        resolve.expectGraph {
            root(":", ":test:") {
                module('org.test:platform:1.0') {
                    if (plainMaven) {
                        configuration("platform-runtime")
                    }
                    noArtifacts()
                    constraint('org.test:depA:1.0')
                }
                module('org.test:depD:1.0') {
                    module('org.test:depC:1.0') {
                        module('org.test:depB:1.0') {
                            module('org.test:depA:1.0') {
                                byConstraint()
                            }
                        }
                    }
                }
                module('org.test:depF:1.0') {
                    module('org.test:depE:1.0') {
                        edge('org.test:depC:1.0', 'org.test:depC:1.0')
                    }
                }
            }
        }
    }


    /**
     * In the project, dependency `c` will be rewritten to dependency `b`.
     * If we exclude dependency b, both the direct request dependency `b`
     * and the dependency rewritten from `c` will be excluded
     * with their transitive dependencies.
     *
     * Dependency graph:
     * a -> b, c, f, g
     * b -> d
     * c -> e
     *
     * Exclude is applied to configuration conf
     */
    def "ensure renamed dependencies are exclude correctly"() {
        given:
        buildFile << """
configurations {
    conf {
        exclude group: 'b', module: 'b'
        resolutionStrategy {
            dependencySubstitution {
                all {
                    if (it.requested instanceof ModuleComponentSelector) {
                        if (it.requested.group == 'c' && it.requested.module == 'c') {
                            it.useTarget group: 'b', name: 'b', version: '1.0'
                        }
                    }
                }
            }
        }
    }
}
"""

        def expectResolved = ['a', 'f', 'g']
        repository {
            'a:a:1.0' {
                dependsOn 'b:b:1.0'
                dependsOn 'c:c:1.0'
                dependsOn 'f:f:1.0'
                dependsOn 'g:g:1.0'
            }
            'b:b:1.0' {
                dependsOn 'd:d:1.0'
            }
            'c:c:1.0' {
                dependsOn 'e:e:1.0'
            }
            'd:d:1.0'()
            'e:e:1.0'()
            'f:f:1.0'()
            'g:g:1.0'()
        }

        repositoryInteractions {
            expectResolved.each {
                "${it}:${it}:1.0" { expectResolve() }
            }
        }

        when:
        succeedsDependencyResolution()

        then:
        def resolvedJars = expectResolved.collect { it + '-1.0.jar'}
        assertResolvedFiles(resolvedJars)
    }
}
