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
import org.gradle.integtests.fixtures.RequiredFeatures
import spock.lang.Issue
import spock.lang.Unroll

@RequiredFeatures(
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
)
class PublishedRichVersionConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {
    def "should not downgrade dependency version when an external transitive dependency has strict version"() {
        given:
        repository {
            'org:foo' {
                '15'()
                '17'()
            }
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '15', strictly: '15', reason: 'what not')
            }
        }

        buildFile << """
            dependencies {
                conf 'org:foo:17'
                conf 'org:bar:1.0'
            }
        """

        when:
        repositoryInteractions {
            'org:foo:17' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo:17'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo' strictly '15' because of the following reason: what not""")

    }

    void "should pass if strict version ranges overlap using external dependencies"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
                '1.2'()
                '1.3'()
            }
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '[1.1,1.3]', strictly: '[1.1,1.3]')
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version { strictly '[1.0,1.2]' }
                }
                conf 'org:bar:1.0'
            }
                         
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:{strictly [1.0,1.2]}", "org:foo:1.2")
                edge('org:bar:1.0', 'org:bar:1.0') {
                    edge("org:foo:{strictly [1.1,1.3]}", "org:foo:1.2")
                }
            }
        }
    }

    @Issue("gradle/gradle#4186")
    def "should choose highest when multiple prefer versions disagree"() {
        repository {
            'org:bar:1' {
                dependsOn(group: 'org', artifact: 'foo', prefers: '1.1')
            }
            'org:baz:1' {
                dependsOn(group: 'org', artifact: 'foo', prefers: '1.0')
            }
            'org:foo' {
                '1.0'()
                '1.1'()
                '1.2'()
                '2.0'()
            }
        }

        buildFile << """
            dependencies {
                conf 'org:bar:1'
                conf 'org:baz:1'
                conf 'org:foo:[1.0,2.0)'
            }
        """

        when:
        repositoryInteractions {
            'org:bar:1' {
                expectResolve()
            }
            'org:baz:1' {
                expectResolve()
            }
            'org:foo' {
                expectVersionListing()
                '1.1' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                '1.2' {
                    expectGetMetadata()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:bar:1') {
                    edge("org:foo:{prefer 1.1}", "org:foo:1.1")
                }
                module('org:baz:1') {
                    edge("org:foo:{prefer 1.0}", "org:foo:1.1")
                }
                edge("org:foo:[1.0,2.0)", "org:foo:1.1")
            }
        }
    }

    def "should fail if 2 strict versions disagree (external)"() {
        given:
        repository {
            'org:foo:15'()
            'org:foo:17'()
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '15', strictly: '15')
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                       strictly '17'
                    }
                }
                conf 'org:bar:1.0'
            }                       
        """

        when:
        repositoryInteractions {
            'org:foo:17' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }

        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo' strictly '17'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo' strictly '15'""")

    }

    def "should fail during conflict resolution transitive dependency rejects"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
            }
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '1.0', rejects: ['1.1']) // transitive dependency rejects version
            }
        }

        buildFile << """
            dependencies {
                conf 'org:foo:1.1'
                conf 'org:bar:1.0'
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.1' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo:1.1'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo:1.0' rejects '1.1'""")
    }

    @Unroll
    void "honors multiple rejections #rejects using dynamic versions using dependency notation #notation"() {
        given:
        repository {
            (0..5).each {
                "org:foo:1.$it"()
            }
            'org:bar:1.0' {
                dependsOn(group: 'org', artifact: 'foo', version: '1.0', rejects: rejects) // transitive dependency rejects version
            }
        }

        buildFile << """
            dependencies {
                conf 'org:foo:1.1'
                conf 'org:bar:1.0'
            }           
        """

        when:
        repositoryInteractions {
            'org:bar:1.0' {
                expectGetMetadata()
            }
            'org:foo:1.1' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo:1.1'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo:1.0' rejects any of "${rejects.collect {"'$it'"}.join(', ')}\"""")

        where:
        rejects << [
            ['1.4', '1.5', '1.1'],
            ['[1.1,)', '1.5'],
            ['1.5', '[1.1, 1.3]', '1.4'],
        ]

    }

    def "should fail if required module is rejected"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:bar:1.0' {
                constraint(group: 'org', artifact: 'foo', rejects: ['+'])
            }
        }

        buildFile << """
            dependencies {
                conf 'org:foo:1.0'
                conf 'org:bar:1.0'
            }                       
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }

        fails ':checkDeps'

        then:
        failure.assertHasCause("""Module 'org:foo' has been rejected:
   Dependency path ':test:unspecified' --> 'org:foo:1.0'
   Constraint path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo' rejects all versions""")

    }

    def "shows only one path to dependency when node is already visited"() {
        given:
        repository {
            'org' {
                'a:1.0' {
                    dependsOn (group: 'org', artifact: 'b', version: '1.0', strictly: '1.0', reason: 'Not following semantic versioning')
                }
                'b' {
                    '1.0'()
                    '1.1'()
                }
                'c:1.0' {
                    dependsOn 'org:b:1.1'
                }
                'd:1.0' {
                    dependsOn 'org:c:1.0' // adds another path to `c`
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:a:1.0'
                conf 'org:c:1.0'
                conf 'org:d:1.0'
            }                       
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
            }
            'org:b:1.0' {
                expectGetMetadata()
            }
            'org:b:1.1' {
                expectGetMetadata()
            }
            'org:c:1.0' {
                expectGetMetadata()
            }
            'org:d:1.0' {
                expectGetMetadata()
            }
        }

        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:b' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:a:1.0' --> 'org:b' strictly '1.0' because of the following reason: Not following semantic versioning
   Dependency path ':test:unspecified' --> 'org:c:1.0' --> 'org:b:1.1'""")

        and:
        failure.assertHasNoCause("Dependency path ':test:unspecified' --> 'org:d:1.0' --> 'org:c:1.0' --> 'org:b:1.1'")
    }

    def "handles dependency cycles"() {
        given:
        repository {
            'org' {
                'a:1.0' {
                    dependsOn (group: 'org', artifact: 'b', version: '1.0', strictly: '1.0', reason: 'Not following semantic versioning')
                }
                'b' {
                    '1.0'()
                    '1.1'()
                }
                'c:1.0' {
                    dependsOn 'org:b:1.1'
                    dependsOn 'org:d:1.0' // creates a cycle
                }
                'd:1.0' {
                    dependsOn 'org:c:1.0' // adds another path to `c`
                }
            }
        }

        buildFile << """
            dependencies {
                conf 'org:a:1.0'
                conf 'org:c:1.0'
                conf 'org:d:1.0'
            }                       
        """

        when:
        repositoryInteractions {
            'org:a:1.0' {
                expectGetMetadata()
            }
            'org:b:1.0' {
                expectGetMetadata()
            }
            'org:b:1.1' {
                expectGetMetadata()
            }
            'org:c:1.0' {
                expectGetMetadata()
            }
            'org:d:1.0' {
                expectGetMetadata()
            }
        }

        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:b' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:a:1.0' --> 'org:b' strictly '1.0' because of the following reason: Not following semantic versioning
   Dependency path ':test:unspecified' --> 'org:c:1.0' --> 'org:b:1.1'""")

        and:
        failure.assertHasNoCause("Dependency path ':test:unspecified' --> 'org:d:1.0' --> 'org:c:1.0' --> 'org:b:1.1'")
    }

}
