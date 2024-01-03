/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.integtests.resolve.versions

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.ivy.IvyModule
import spock.lang.Issue

abstract class AbstractRichVersionConstraintsIntegrationTest extends AbstractModuleDependencyResolveTest {

    void "can declare a strict dependency onto an external component"() {
        given:
        repository {
            'org:foo:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo:1.0!!')
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
                edge("org:foo:{strictly 1.0}", "org:foo:1.0")
            }
        }
    }

    void "can declare a strict dependency constraint onto an external component"() {
        given:
        repository {
            'org:foo:1.0'()
        }

        buildFile << """
            dependencies {
                conf('org:foo')
                constraints {
                    conf('org:foo:1.0!!')
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
                edge("org:foo", "org:foo:1.0") {
                    byConstraint()
                }
                constraint("org:foo:{strictly 1.0}", "org:foo:1.0")
            }
        }
    }

    @Issue("gradle/gradle#4186")
    def "should choose highest when multiple prefer versions disagree"() {
        repository {
            'org:foo' {
                '1.0.0'()
                '1.1.0'()
                '1.2.0'()
                '2.0.0'()
            }
        }

        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo') {
                        version { prefer '1.1.0' }
                    }
                    conf('org:foo') {
                        version { prefer '1.0.0' }
                    }
                }
                conf 'org:foo:[1.0.0,2.0.0)'
            }
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.1.0' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                '1.2.0' {
                    expectGetMetadata()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint("org:foo:{prefer 1.0.0}", "org:foo:1.1.0")
                constraint("org:foo:{prefer 1.1.0}", "org:foo:1.1.0")
                edge("org:foo:[1.0.0,2.0.0)", "org:foo:1.1.0") {
                    notRequested()
                    byConstraint()
                    byReason("didn't match version 2.0.0")
                }
            }
        }
    }

    def "can combine required and preferred version in single dependency definition"() {
        repository {
            'org:foo' {
                '1.0.0'()
                '1.1.0'()
                '1.2.0'()
                '2.0.0'()
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:0.9')
                conf('org:foo:[1.0.0,2.0.0)') {
                    version {
                        prefer '1.1.0'
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.1.0' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                '1.2.0' {
                    expectGetMetadata()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:{require [1.0.0,2.0.0); prefer 1.1.0}", "org:foo:1.1.0")
                edge("org:foo:0.9", "org:foo:1.1.0").byConflictResolution("between versions 0.9 and 1.1.0")
            }
        }
    }

    def "can combine strict and preferred version in single dependency definition"() {
        repository {
            'org:foo' {
                '1.0.0'()
                '1.1.0'()
                '1.2.0'()
                '2.0.0'()
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:[1.0.0,2.0.0)!!1.1.0')
            }
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '1.1.0' {
                    expectGetMetadata()
                    expectGetArtifact()
                }
                '1.2.0' {
                    expectGetMetadata()
                }
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:foo:{strictly [1.0.0,2.0.0); prefer 1.1.0}", "org:foo:1.1.0")
            }
        }
    }

    void "a strict dependency version takes precedence over a higher transitive version"() {
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
                    expectResolve()
                }
            }
            'org:bar:1.0' {
                expectResolve()
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo:{strictly 1.0}', 'org:foo:1.0')
                module('org:bar:1.0') {
                    edge('org:foo:1.1', 'org:foo:1.0') {
                        byAncestor()
                    }
                }
            }
        }

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
                conf('org:foo:1.0!!')
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
                edge ("org:foo:{strictly 1.0}", "org:foo:1.0") {
                    byAncestor()
                }
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
                edge "org:foo:{strictly 1.1}", "org:foo:1.1"
                edge("org:bar:1.0", "org:bar:1.0") {
                    edge("org:foo:1.0", "org:foo:1.1").byAncestor()
                }
            }
        }
    }

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
                "$resolvedVersion" {
                    expectResolve()
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
                edge("org:foo:{strictly $directDependencyVersion}", "org:foo:$resolvedVersion")
                edge("org:bar:1.0", "org:bar:1.0") {
                    edge("org:foo:$transitiveDependencyVersion", "org:foo:$resolvedVersion") {
                        if (transitiveDependencyVersion != resolvedVersion) {
                            byAncestor()
                        }
                        maybeRequested()
                        maybeByReason("didn't match version 1.3")
                    }
                }
            }
        }

        where:
        directDependencyVersion | transitiveDependencyVersion | listVersions | resolvedVersion
        '[1.0,1.3]'             | '1.2'                       | true         | '1.3' // should probably choose 1.2 instead
        '1.2'                   | '[1.0,1.3]'                 | false        | '1.2'
        '[1.0,1.2]'             | '[1.0, 1.3]'                | true         | '1.2'
        '[1.0,1.3]'             | '[1.0,1.2]'                 | true         | '1.3' // should probably choose 1.2 instead
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
   Dependency path ':test:unspecified' --> 'org:foo:17'
   Dependency path ':test:unspecified' --> 'test:other:unspecified' (conf) --> 'org:foo:{strictly 15}'""")

    }

    def "should fail if 2 1st-level strict versions disagree"() {
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
                conf('org:foo') {
                    version {
                        strictly '15'
                    }
                }
            }
        """
        createDirs("other")
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
   Dependency path ':test:unspecified' --> 'org:foo:{strictly 17}'
   Dependency path ':test:unspecified' --> 'org:foo:{strictly 15}'""")

    }


    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "should fail if 1st-level version disagrees with transitive strict version"() {
        given:
        repository {
            'org:foo:15'()
            'org:foo:17'()
            'org:bar:1' {
                dependsOn(group: 'org', artifact: 'foo', strictly: '15')
            }
        }

        buildFile << """
            dependencies {
                conf('org:foo:17')
                conf('org:bar:1')
            }
        """
        createDirs("other")
        settingsFile << "\ninclude 'other'"

        when:
        repositoryInteractions {
            'org:bar:1' {
                expectGetMetadata()
            }
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
   Dependency path ':test:unspecified' --> 'org:foo:17'
   Dependency path ':test:unspecified' --> 'org:bar:1' (runtime) --> 'org:foo:{strictly 15}'""")

    }

    def "strict range defined as 1st level dependency wins over transitive one"() {
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
                    expectResolve()
                }
            }
        }
        succeeds ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge('org:foo:{strictly [15,16]}', 'org:foo:16')
                project(':other', 'test:other:') {
                    configuration 'conf'
                    edge('org:foo:{strictly [17,18]}', 'org:foo:16') {
                        notRequested()
                        byAncestor()
                        byReason("didn't match versions 18, 17")
                    }
                    noArtifacts()
                }
            }
        }

    }

    def "should fail and include path to unresolvable strict version range"() {
        given:
        repository {
            'org:foo:15'()
        }

        buildFile << """
            dependencies {
                conf 'org:foo:15'
                conf('org:foo') {
                    version {
                        strictly '[0,1]'
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:foo' {
                expectVersionListing()
                '15' {
                    expectGetMetadata()
                }
            }
        }
        fails ':checkDeps'

        then:
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:foo:15'
   Dependency path ':test:unspecified' --> 'org:foo:{strictly [0,1]}'""")
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
                edge("org:foo:{require 1.0; reject 1.1}", "org:foo:1.0")
            }
        }
    }

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
                edge("org:foo:{require $notation; reject 1.1}", "org:foo:1.0") {
                    notRequested()
                    byReason("rejected version 1.1")
                }
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
        def selected = GradleMetadataResolveRunner.gradleMetadataPublished || GradleMetadataResolveRunner.useMaven() ? 'runtime' : 'default'
        failure.assertHasCause("""Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':test:unspecified' --> 'org:foo:{require 1.0; reject 1.1}'
   Dependency path ':test:unspecified' --> 'org:bar:1.0' ($selected) --> 'org:foo:1.1'""")
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
                edge("org:foo:{require [1.0,); reject [1.2, 1.5]}", "org:foo:1.1") {
                    notRequested()
                    byReason("rejected versions 1.5, 1.4, 1.3, 1.2")
                }
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
                edge("org:foo:{require 1.0; reject 1.1 & 1.2}", "org:foo:1.0")
            }
        }
    }

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

        def rejectString = rejects.collect({ "'${it}'" }).join(', ')
        buildFile << """
            dependencies {
                conf('org:foo:$notation') {
                   version {
                      reject $rejectString
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
                String rejectedVersions = (selected + 1..5).collect { "1.${it}" }.reverse().join(", ")
                edge("org:foo:{require $notation; reject ${rejects.join(' & ')}}", "org:foo:1.$selected") {
                    notRequested()
                    byReason("rejected versions ${rejectedVersions}")
                }
            }
        }

        where:
        notation         | rejects                      | selected | requiresMetadata
        '1+'             | ['1.4', '1.5']               | 3        | false
        '1.+'            | ['1.4', '1.5']               | 3        | false
        '[1.0,)'         | ['1.4', '1.5']               | 3        | false
        'latest.release' | ['1.4', '1.5']               | 3        | true

        '1+'             | ['[1.2,)', '1.5']            | 1        | false
        '1.+'            | ['[1.2,)', '1.5']            | 1        | false
        '[1.0,)'         | ['[1.2,)', '1.5']            | 1        | false
        'latest.release' | ['[1.2,)', '1.5']            | 1        | true

        '1+'             | ['1.5', '[1.1, 1.3]', '1.4'] | 0        | false
        '1.+'            | ['1.5', '[1.1, 1.3]', '1.4'] | 0        | false
        '[1.0,)'         | ['1.5', '[1.1, 1.3]', '1.4'] | 0        | false
        'latest.release' | ['1.5', '[1.1, 1.3]', '1.4'] | 0        | true
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
                    expectResolve()
                }
            }
            'org:foo:2' {
                expectResolve()
            }
        }

        succeeds ":checkDeps"

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                constraint('org:bar:{strictly 1}', 'org:bar:1')
                module('org:bar:1') {
                    byConstraint()
                }
                module('org:foo:2') {
                    edge('org:bar:2', 'org:bar:1') {
                        byAncestor()
                    }
                }
            }
        }
    }

}
