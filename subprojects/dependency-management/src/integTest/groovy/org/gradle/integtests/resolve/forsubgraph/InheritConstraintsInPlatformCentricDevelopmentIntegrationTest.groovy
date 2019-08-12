/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.integtests.resolve.forsubgraph

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.Ignore
import spock.lang.Unroll

import static org.gradle.integtests.resolve.forsubgraph.InheritConstraintsInPlatformCentricDevelopmentIntegrationTest.PlatformType.MODULE
import static org.gradle.integtests.resolve.forsubgraph.InheritConstraintsInPlatformCentricDevelopmentIntegrationTest.PlatformType.PLATFORM
import static org.gradle.integtests.resolve.forsubgraph.InheritConstraintsInPlatformCentricDevelopmentIntegrationTest.PlatformType.LEGACY_PLATFORM
import static org.gradle.integtests.resolve.forsubgraph.InheritConstraintsInPlatformCentricDevelopmentIntegrationTest.PlatformType.ENFORCED_PLATFORM

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")]
)
class InheritConstraintsInPlatformCentricDevelopmentIntegrationTest extends AbstractModuleDependencyResolveTest {

    enum PlatformType {
        // The recommended way of doing platforms
        PLATFORM,          // constraints in platform are published with 'forSubgraph', consumer uses 'platform()' dependencies
        // The recommended way of dealing with existing platforms
        LEGACY_PLATFORM,   // constraints in platform are published without 'forSubgraph', consumer uses 'platform()' dependencies + component metadata rules to add 'forSubgraph' to published constraints
        // The discouraged way of dealing with existing platforms
        ENFORCED_PLATFORM, // constraints in platform are published without 'forSubgraph', consumer uses 'enforcedPlatform()' dependencies (to be deprecated)
        // Using a normal module as "platform" (i.e. inheriting it's constraints) also works
        MODULE             // constraints in module are published with 'forSubgraph', consumer uses normal dependencies with 'inheritConstraints()'
    }

    def setup() {
        resolve.withStrictReasonsCheck()
    }

    String platformDependency(platformType, String dependency) {
        switch (platformType) {
            case PLATFORM:
            case LEGACY_PLATFORM:
                return "conf(platform('$dependency'))"
            case ENFORCED_PLATFORM:
                return "conf(enforcedPlatform('$dependency'))"
            case MODULE:
                return "conf('$dependency') { inheritConstraints() }"
        }
        ""
    }

    private singleLibraryBuildFile(platformType) {
        buildFile << """
            dependencies {
                ${platformDependency(platformType, 'org:platform:1.+')}
                conf('org:bar')
            }
        """
    }

    private void initialRepository(platformType) {
        repository {
            'org:platform:1.0'() {
                variant('apiElements') {
                    attributes = ['org.gradle.category': 'platform']
                    noArtifacts = true
                }
                constraint(group: 'org', artifact: 'bar', version: '2.0', forSubgraph: platformType in [PLATFORM, MODULE])
                constraint(group: 'org', artifact: 'foo', version: '3.0', rejects: ['3.1', '3.2'], forSubgraph: platformType in [PLATFORM, MODULE])
            }
            'org:foo' {
                '3.0'()
                '3.1'() // bad version
                '3.2'() // bad version
            }
            'org:bar:2.0'() {
                dependsOn 'org:foo:3.1'
            }
        }
        if (platformType == LEGACY_PLATFORM) {
            // we use component metadata rules to get behavior similar to 'enforcedPlatform()' for 'platform()'
            buildFile << """
                dependencies {
                    components.withModule('org:platform') { ComponentMetadataDetails details ->
                        details.withVariant('apiElements') {
                            withDependencyConstraints {
                                it.each { it.version { forSubgraph() } }
                            }
                        }
                    }
                }
            """
        }
    }

    private void updatedRepository(platformType) {
        initialRepository(platformType)
        repository {
            'org:platform:1.1'() {
                variant('apiElements') {
                    attributes = ['org.gradle.category': 'platform']
                    noArtifacts = true
                }
                constraint(group: 'org', artifact: 'bar', version: '2.0', forSubgraph:  platformType in [PLATFORM, MODULE])
                constraint(group: 'org', artifact: 'foo', version: '3.1.1', rejects: ['3.1', '3.2'], forSubgraph:  platformType in [PLATFORM, MODULE])
            }
            'org:foo' {
                '3.1.1'()
            }
        }
    }

    static private String expectSubgraphVersion(platformType, String requiredVersion, String rejectedVersions = '') {
        String subgraph = platformType == ENFORCED_PLATFORM ? '' : 'subgraph'
        if (!subgraph.isEmpty() && rejectedVersions.isEmpty()) {
            return "{require $requiredVersion; $subgraph}"
        }
        if (subgraph.isEmpty() && !rejectedVersions.isEmpty()) {
            return "{require $requiredVersion; reject $rejectedVersions}"
        }
        if (!subgraph.isEmpty() && !rejectedVersions.isEmpty()) {
            return "{require $requiredVersion; reject $rejectedVersions; $subgraph}"
        }
        requiredVersion
    }

    @Unroll
    void "(1) all future releases of org:foo:3.0 are bad and the platform enforces 3.0 [#platformType]"() {
        initialRepository(platformType)
        singleLibraryBuildFile(platformType)

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.0' {
                expectGetMetadata()
                if (platformType == MODULE) { expectGetArtifact() }
            }
            'org:bar:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:3.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:platform:1.+', 'org:platform:1.0') {
                    byRequest()
                    if (platformType != MODULE) {
                        configuration(platformType == ENFORCED_PLATFORM ? 'enforcedApiElements' : 'apiElements')
                        noArtifacts()
                    }
                    constraint("org:bar:${expectSubgraphVersion(platformType, '2.0')}", 'org:bar:2.0').byConstraint()
                    constraint("org:foo:${expectSubgraphVersion(platformType, '3.0', '3.1 & 3.2')}", 'org:foo:3.0').byConstraint()
                }
                edge('org:bar', 'org:bar:2.0') {
                    byRequest()
                    edge('org:foo:3.1', 'org:foo:3.0') {
                        if (platformType != ENFORCED_PLATFORM) { byAncestor() } else { byRequest() }
                    }
                }
            }
            if (platformType == ENFORCED_PLATFORM) {
                nodesWithoutRoot.each { it.forced() }
            }
        }

        where:
        platformType << PlatformType.values()
    }

    @Unroll
    void "(2) org:foo:3.1.1 and platform upgrade 1.1 are release [#platformType]"() {
        updatedRepository(platformType)
        singleLibraryBuildFile(platformType)

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.1' {
                expectGetMetadata()
                if (platformType == MODULE) { expectGetArtifact() }
            }
            'org:bar:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org:foo:3.1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org:platform:1.+', 'org:platform:1.1') {
                    byRequest()
                    if (platformType != MODULE) {
                        configuration(platformType == ENFORCED_PLATFORM ? 'enforcedApiElements' : 'apiElements')
                        noArtifacts()
                    }
                    constraint("org:bar:${expectSubgraphVersion(platformType, '2.0')}", 'org:bar:2.0').byConstraint()
                    constraint("org:foo:${expectSubgraphVersion(platformType, '3.1.1', '3.1 & 3.2')}", 'org:foo:3.1.1').byConstraint()
                }
                edge('org:bar', 'org:bar:2.0') {
                    byRequest()
                    edge('org:foo:3.1', 'org:foo:3.1.1') {
                        if (platformType != ENFORCED_PLATFORM) { byAncestor() } else { byRequest() }
                    }
                }
            }
            if (platformType == ENFORCED_PLATFORM) {
                nodesWithoutRoot.each { it.forced() }
            }
        }

        where:
        platformType << PlatformType.values()
    }

    @Unroll
    void "(3) library developer has issues with org:foo:3.1.1 and overrides platform decision with 3.2 which fails due to reject [#platformType]"() {
        updatedRepository(platformType)
        singleLibraryBuildFile(platformType)
        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:3.2')
                }
            }
        """

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.1' {
                expectGetMetadata()
            }
            'org:bar:2.0' {
                expectGetMetadata()
                if (platformType == ENFORCED_PLATFORM) { expectGetArtifact() }
            }
            'org:foo:3.1.1' {
                expectGetMetadata()
                if (platformType == ENFORCED_PLATFORM) { expectGetArtifact() }
            }
            'org:foo:3.2' {
                if (platformType != ENFORCED_PLATFORM) { expectGetMetadata() }
            }
        }
        if (platformType == ENFORCED_PLATFORM) {
            // issue with enforced platform: the forced version is always used and the conflict is 'hidden'
            succeeds ':checkDeps'
        } else {
            fails ':checkDeps'
        }
        then:
        (platformType == ENFORCED_PLATFORM && !failure) || failure.assertHasCause(
            """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org:bar:2.0' --> 'org:foo:3.1'
   Constraint path ':test:unspecified' --> 'org:platform:1.1' --> 'org:foo:{require 3.1.1; reject 3.1 & 3.2; subgraph}'
   Constraint path ':test:unspecified' --> 'org:foo:3.2'""")

        where:
        platformType << PlatformType.values()
    }

    @Unroll
    void "(4) library developer has issues with org:foo:3.1.1 and forces an override of the platform decision with forSubgraph [#platformType]"() {
        // issue with enforced platform: consumer can not override platform decision via constraint
        //                               (an override via an own forced dependency is possible)
        def expectedFooVersion = platformType == ENFORCED_PLATFORM ? '3.1.1' : '3.2'

        updatedRepository(platformType)
        singleLibraryBuildFile(platformType)
        buildFile << """
            dependencies {
                constraints {
                    conf('org:foo:3.2') {
                        version { forSubgraph() }
                    }
                }
            }
        """

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
                expectGetArtifact()
            }
            'org:platform:1.1' {
                expectGetMetadata()
                if (platformType == MODULE) { expectGetArtifact() }
            }
            'org:bar:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            "org:foo:$expectedFooVersion" {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(':', ':test:') {
                constraint('org:foo:{require 3.2; subgraph}', "org:foo:$expectedFooVersion").byConstraint()
                edge('org:platform:1.+', 'org:platform:1.1') {
                    byRequest()
                    if (platformType != MODULE) {
                        configuration(platformType == ENFORCED_PLATFORM ? 'enforcedApiElements' : 'apiElements')
                        noArtifacts()
                    }
                    constraint("org:bar:${expectSubgraphVersion(platformType, '2.0')}", 'org:bar:2.0').byConstraint()
                    constraint("org:foo:${expectSubgraphVersion(platformType, '3.1.1', '3.1 & 3.2')}", "org:foo:$expectedFooVersion").byConstraint()
                }
                edge('org:bar', 'org:bar:2.0') {
                    edge('org:foo:3.1', "org:foo:$expectedFooVersion").byAncestor()
                }.byRequest()
            }
            if (platformType == ENFORCED_PLATFORM) {
                nodesWithoutRoot.each { it.forced() }
            }
        }

        where:
        platformType << PlatformType.values()
    }

    @Unroll
    void "(5) if two libraries are combined without agreeing on an override, the original platform constraint is brought back [#platformType]"() {
        updatedRepository(platformType)
        settingsFile << "\ninclude 'recklessLibrary', 'secondLibrary'"
        buildFile << """
            project(':recklessLibrary') {
                configurations { conf }
                dependencies {
                    ${platformDependency(platformType, 'org:platform:1.+')}
                    conf('org:bar')
                    constraints {
                        conf('org:foo:3.2') {
                            version { forSubgraph() } // ignoring platform's reject
                        }
                    }
                }
            }
            project(':secondLibrary') {
                configurations { conf }
                dependencies {
                    ${platformDependency(platformType, 'org:platform:1.+')}
                    conf('org:bar')
                }
            }
            dependencies {
                conf(project(path: ':recklessLibrary', configuration: 'conf'))
                conf(project(path: ':secondLibrary', configuration: 'conf'))
            }
        """

        when:
        repositoryInteractions {
            'org:platform' {
                expectVersionListing()
            }
            'org:platform:1.1' {
                expectGetMetadata()
            }
            'org:bar:2.0' {
                expectGetMetadata()
                if (platformType == ENFORCED_PLATFORM) { expectGetArtifact() }
            }
            'org:foo:3.1.1' {
                expectGetMetadata()
                if (platformType == ENFORCED_PLATFORM) { expectGetArtifact() }
            }
            'org:foo:3.2' {
                if (platformType != ENFORCED_PLATFORM) { expectGetMetadata() }
            }
        }
        if (platformType == ENFORCED_PLATFORM) {
            // issue with enforced platform: the forced version is always used and the conflict is 'hidden'
            succeeds ':checkDeps'
        } else {
            fails ':checkDeps'
        }

        then:
        (platformType == ENFORCED_PLATFORM && !failure) || failure.assertHasCause(
            """Cannot find a version of 'org:foo' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'test:recklessLibrary:unspecified' --> 'org:bar:2.0' --> 'org:foo:3.1'
   Constraint path ':test:unspecified' --> 'test:recklessLibrary:unspecified' --> 'org:platform:1.1' --> 'org:foo:{require 3.1.1; reject 3.1 & 3.2; subgraph}'
   Constraint path ':test:unspecified' --> 'test:recklessLibrary:unspecified' --> 'org:foo:{require 3.2; subgraph}'""")

        where:
        platformType << PlatformType.values()
    }

    @Ignore // Having only Unroll tests breaks something in the combination with GradleMetadataResolveRunner
    void "dummy"() {
        expect:
        true
    }
}
