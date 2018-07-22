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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.test.fixtures.ivy.IvyModule
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Unroll

@IgnoreIf({
    // This test is very expensive. Ideally we shouldn't need an integration test here, but lack the
    // infrastructure to simulate everything done here, so we're only going to execute this test in
    // embedded mode
    !GradleContextualExecuter.embedded
})
class RichVersionConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    void "can declare a strict dependency onto an external component"() {
        given:
        repository {
            'org:foo:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version {
                      strictly '1.0'
                   }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }
    }

    void "should fail if transitive dependency version is not compatible with the strict dependency version"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
            }
            'org:bar:1.0' {
                dependsOn('org:foo:1.1')
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                       strictly '1.0'
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo' {
                '1.0' {
                    expectGetMetadata()
                }
                '1.1' {
                    expectGetMetadata()
                }
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo' strictly '1.0'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo' prefers '1.1'""")

    }

    void "should pass if transitive dependency version matches exactly the strict dependency version"() {
        given:
        repository {
            'org:foo:1.0'()
            'org:bar:1.0' {
                dependsOn 'org:foo:1.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                        strictly '1.0'
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
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
                edge "org:foo:1.0", "org:foo:1.0"
                edge("org:bar:1.0", "org:bar:1.0") {
                    edge "org:foo:1.0", "org:foo:1.0"
                }
            }
        }
    }

    void "can upgrade a non-strict dependency"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
            }
            'org:bar:1.0' {
                dependsOn 'org:foo:1.0'
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                       strictly '1.1'
                    }
                }
                conf('org:bar:1.0')
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.1' {
                expectGetMetadata()
                expectGetArtifact()
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
                edge "org:foo:1.1", "org:foo:1.1"
                edge("org:bar:1.0", "org:bar:1.0") {
                    edge("org:foo:1.0", "org:foo:1.1").byConflictResolution("between versions 1.1 and 1.0")
                }
            }
        }
    }

    @Unroll
    void "should pass if transitive dependency version (#transitiveDependencyVersion) matches a strict dependency version (#directDependencyVersion)"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
                '1.2'()
                '1.3'()
            }
            'org:bar:1.0' {
                dependsOn("org:foo:$transitiveDependencyVersion")
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                       strictly '$directDependencyVersion'
                    }
                }
                conf('org:bar:1.0')
            }
                          
        """

        when:
        repositoryInteractions {
            'org:foo' {
                if (listVersions) {
                    expectVersionListing()
                }
                '1.2' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                if (resolve13) {
                    '1.3' {
                        expectGetMetadata()
                    }
                }
            }
            'org:bar:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        noExceptionThrown()

        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:$directDependencyVersion", "org:foo:1.2")
                edge("org:bar:1.0", "org:bar:1.0") {
                    edge("org:foo:$transitiveDependencyVersion", "org:foo:1.2")
                }
            }
        }

        where:
        directDependencyVersion | transitiveDependencyVersion | listVersions | resolve13
        '[1.0,1.3]'             | '1.2'                       | true         | true
        '1.2'                   | '[1.0,1.3]'                 | false        | false
        '[1.0,1.2]'             | '[1.0, 1.3]'                | true         | false
        '[1.0,1.3]'             | '[1.0,1.2]'                 | true         | true
    }

    def "should not downgrade dependency version when a transitive dependency has strict version"() {
        given:
        repository {
            'org:foo:15'()
            'org:foo:17'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:17')
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            $repositoryDeclaration

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version {
                        strictly '15'
                    }
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        repositoryInteractions {
            'org:foo:15' {
                maybeGetMetadata()
            }
            'org:foo:17' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo' prefers '17'
   Dependency path ':test:unspecified' --> 'test:other:unspecified' --> 'org:foo' strictly '15'""")

    }

    def "should fail if 2 strict versions disagree"() {
        given:
        repository {
            'org:foo:15'()
            'org:foo:17'()
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                        strictly '17'
                    }
                }
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            $repositoryDeclaration

            configurations {
                conf
            }
            dependencies {
                conf('org:foo:15') {
                    version {
                        strictly '15'
                    }
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        repositoryInteractions {
            'org:foo' {
                '15' {
                    maybeGetMetadata()
                }
                '17' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo' strictly '17'
   Dependency path ':test:unspecified' --> 'test:other:unspecified' --> 'org:foo' strictly '15'""")

    }

    def "should fail if 2 non overlapping strict versions ranges disagree"() {
        given:
        repository {
            'org:foo:15'()
            'org:foo:16'()
            'org:foo:17'()
            'org:foo:18'()
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                        strictly '[15,16]'
                    }
                }
                conf project(path: 'other', configuration: 'conf')
            }                       
        """
        file("other/build.gradle") << """
            $repositoryDeclaration

            configurations {
                conf
            }
            dependencies {
                conf('org:foo') {
                    version {
                        strictly '[17,18]'
                    }
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '16' {
                    expectGetMetadata()
                }
                '18' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo' strictly '[15,16]'
   Dependency path ':test:unspecified' --> 'test:other:unspecified' --> 'org:foo' strictly '[17,18]'""")

    }

    void "should pass if strict version ranges overlap"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
                '1.2'()
                '1.3'()
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo') {
                    version {
                        strictly '[1.0,1.2]'
                    }
                }
                conf project(path:'other', configuration: 'conf')
            }
                                  
        """
        file("other/build.gradle") << """
            $repositoryDeclaration

            configurations {
                conf
            }
            dependencies {
                conf('org:foo:[1.1,1.3]') {
                    version {
                        strictly '[1.1,1.3]'
                    }
                }
            }       
        """
        settingsFile << "\ninclude 'other'"

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.2' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:[1.0,1.2]", "org:foo:1.2")
                project(':other', 'test:other:') {
                    configuration = 'conf'
                    noArtifacts()
                    edge("org:foo:[1.1,1.3]", "org:foo:1.2")
                }
            }
        }
    }


    void "can reject dependency versions of an external component"() {
        given:
        repository {
            'org:foo:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version {
                      reject '1.1'
                   }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }
    }

    @Unroll
    void "honors rejection using dynamic versions using dependency notation #notation"() {
        given:
        repository {
            'org:foo:1.0' {
                withModule(IvyModule) {
                    withStatus('release')
                }
            }
            'org:foo:1.1' {
                withModule(IvyModule) {
                    withStatus('release')
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:$notation') {
                   version {
                      reject '1.1'
                   }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                if (requiresMetadata) {
                    '1.1' {
                        expectGetMetadata()
                    }
                }
                '1.0' {
                    expectResolve()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:$notation", "org:foo:1.0").byReason("rejected version 1.1")
            }
        }

        where:
        notation         | requiresMetadata
        '1+'             | false
        '1.+'            | false
        '[1.0,)'         | false
        'latest.release' | true
    }

    def "should fail during conflict resolution when one module rejects version"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
                '1.1'()
            }
            'org:bar:1.0' {
                dependsOn('org:foo:1.1') // transitive dependency on rejected version
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version {
                      reject '1.1'
                   }
                }
                conf 'org:bar:1.0'
            }           
        """

        when:
        repositoryInteractions {
            'org:foo' {
                '1.0' {
                    expectGetMetadata()
                }
                '1.1' {
                    expectGetMetadata()
                }
            }
            'org:bar:1.0' {
                expectGetMetadata()
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:foo' prefers '1.0', rejects '1.1'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' --> 'org:foo' prefers '1.1'""")
    }

    def "can reject a version range"() {
        given:
        repository {
            (0..5).each {
                "org:foo:1.$it"()
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:[1.0,)') {
                   version {
                      reject '[1.2, 1.5]'
                   }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.1' {
                    expectResolve()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:[1.0,)", "org:foo:1.1").byReason("rejected versions 1.5, 1.4, 1.3, 1.2")
            }
        }
    }

    void "can reject multiple versions of an external component"() {
        given:
        repository {
            'org:foo:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version {
                      reject '1.1', '1.2'
                   }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:foo:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module("org:foo:1.0")
            }
        }
    }

    @Unroll
    void "honors multiple rejections #rejects using dynamic versions using dependency notation #notation"() {
        given:
        repository {
            (0..5).each {
                "org:foo:1.$it" {
                    withModule(IvyModule) {
                        withStatus('release')
                    }
                }
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:$notation') {
                   version {
                      reject $rejects
                   }
                }
            }           
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                if (requiresMetadata) {
                    5.downto(selected + 1) {
                        "1.$it" {
                            expectGetMetadata()
                        }
                    }
                }
                "1.$selected" {
                    expectResolve()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                String rejectedVersions = (selected+1..5).collect { "1.${it}" }.reverse().join(", ")
                edge("org:foo:$notation", "org:foo:1.$selected").byReason("rejected versions ${rejectedVersions}")
            }
        }

        where:
        notation         | rejects                      | selected | requiresMetadata
        '1+'             | "'1.4', '1.5'"               | 3        | false
        '1.+'            | "'1.4', '1.5'"               | 3        | false
        '[1.0,)'         | "'1.4', '1.5'"               | 3        | false
        'latest.release' | "'1.4', '1.5'"               | 3        | true

        '1+'             | "'[1.2,)', '1.5'"            | 1        | false
        '1.+'            | "'[1.2,)', '1.5'"            | 1        | false
        '[1.0,)'         | "'[1.2,)', '1.5'"            | 1        | false
        'latest.release' | "'[1.2,)', '1.5'"            | 1        | true

        '1+'             | "'1.5', '[1.1, 1.3]', '1.4'" | 0        | false
        '1.+'            | "'1.5', '[1.1, 1.3]', '1.4'" | 0        | false
        '[1.0,)'         | "'1.5', '[1.1, 1.3]', '1.4'" | 0        | false
        'latest.release' | "'1.5', '[1.1, 1.3]', '1.4'" | 0        | true
    }

    def "adding rejectAll on a dependency is pointless and make it fail"() {
        given:
        repository {
            'org:foo' {
                '1.0'()
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0') {
                   version {
                      rejectAll()
                   }
                }
            }           
        """

        when:
        fails ':checkDeps'

        then:
        failure.assertHasCause("Module 'org:foo' has been rejected")
    }

    /**
     * Test demonstrates incorrect behaviour where we are incorrectly upgrading a constraint with
     *  `version { strictly 'x'}`  during conflict resolution.
     *
     * When 2 different constraints choose the same version, only one of these constraints is considered when conflict resolution
     * applies with a 3rd constraint.
     */
    @Issue("gradle/gradle#4608")
    def "conflict resolution should consider all constraints for each candidate"() {
        repository {
            'org:foo:2' {
                dependsOn("org:bar:2")
            }
            'org:bar:1'()
            'org:bar:2'()
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:bar') {
                        version {
                            strictly '1'
                        }
                    }
                }
                conf 'org:bar:1'
                conf 'org:foo:2' // Brings in org:bar:2, which is chosen over org:bar:1 in conflict resolution
            }
"""
        when:
        repositoryInteractions {
            'org:bar' {
                '1' {
                    expectGetMetadata()
                }
                '2' {
                    expectGetMetadata()
                }
            }
            'org:foo:2' {
                expectGetMetadata()
            }
        }

        fails ":checkDeps"

        then:
        failure.assertHasCause("""Cannot find a version of 'org:bar' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:bar' prefers '1'
   Constraint path ':test:unspecified' --> 'org:bar' strictly '1'
   Dependency path ':test:unspecified' --> 'org:foo:2' --> 'org:bar' prefers '2'""")
    }

}
