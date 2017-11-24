/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import spock.lang.IgnoreIf
import spock.lang.Issue

@RequiredFeatures([
    // this test is specific to Ivy
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy"),
]
)
@IgnoreIf({ GradleContextualExecuter.parallel })
class IvyDynamicRevisionResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Issue("GRADLE-2502")
    def "latest.integration selects highest version regardless of status"() {
        given:
        buildFile << """
  dependencies {
      conf 'org.test:projectA:latest.integration'
  }
  """

        when:
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.integration as no versions of org.test:projectA are available.'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.0' {
                withModule() {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.0' {
                expectGetMetadata()
                expectHeadArtifact()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.0")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA' {
                '1.1' {
                    withModule {
                        withStatus('integration')
                    }
                }
                '1.2' {
                    withModule {
                        withStatus('integration')
                    }
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.2")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA' {
                '1.3' {
                    withModule {
                        withStatus('release')
                    }
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.3' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.3")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA' {
                '1.4' {
                    withModule {
                        withNoMetaData()
                    }
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.4' {
                expectGetMetadata()
                expectHeadArtifact()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.integration", "org.test:projectA:1.4")
            }
        }
    }

    @Issue("GRADLE-2502")
    def "latest.milestone selects highest version with milestone or release status"() {
        given:
        buildFile << """
  dependencies {
      conf 'org.test:projectA:latest.milestone'
  }
  """

        when:
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.milestone as no versions of org.test:projectA are available.'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:2.0' {
                withModule {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
        }
        runAndFail 'checkDeps', '--refresh-dependencies'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.milestone.
Versions that do not match:
    2.0
Searched in the following locations:
'''

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.3' {
                withModule {
                    withStatus 'integration'
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectGetMetadata()
            }
        }
        runAndFail 'checkDeps', '--refresh-dependencies'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.milestone.
Versions that do not match:
    2.0
    1.3
Searched in the following locations:
'''

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.0' {
                withModule {
                    withStatus 'milestone'
                }
            }
            'org.test:projectA:1.1' {
                withModule {
                    withStatus 'milestone'
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectHeadMetadata()
            }
            'org.test:projectA:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.1")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2' {
                withModule {
                    withStatus 'release'
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectHeadMetadata()
            }
            'org.test:projectA:1.2' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.2")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.3' {
                withModule {
                    withStatus 'integration'
                    publishWithChangedContent()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectHeadMetadata()
                withModule {
                    // todo: handle this properly in ModuleVersionSpec test fixture
                    getArtifact(name: 'ivy', ext: 'xml.sha1').allowGetOrHead()
                    if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                        getArtifact(ext: 'module.sha1').allowGetOrHead()
                    }
                }
                maybeGetMetadata()
            }
            'org.test:projectA:1.2' {
                expectHeadMetadata()
                expectHeadArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.milestone", "org.test:projectA:1.2")
            }
        }
    }

    @Issue("GRADLE-2502")
    void "latest.release selects highest version with release status"() {
        given:
        buildFile << """
  dependencies {
      conf 'org.test:projectA:latest.release'
  }
  """
        when:
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.release as no versions of org.test:projectA are available.'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:2.0' {
                withModule {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.release.
Versions that do not match:
    2.0
Searched in the following locations:
'''

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.3' {
                withModule {
                    withStatus 'integration'
                }
            }
            'org.test:projectA:1.2' {
                withModule {
                    withStatus 'milestone'
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectGetMetadata()
            }
            'org.test:projectA:1.2' {
                expectGetMetadata()
            }
        }
        runAndFail 'checkDeps', '--refresh-dependencies'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.release.
Versions that do not match:
    2.0
    1.3
    1.2
Searched in the following locations:
'''

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.1' {
                withModule {
                    withStatus 'release'
                }
            }
            'org.test:projectA:1.0' {
                withModule {
                    withStatus 'release'
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectHeadMetadata()
            }
            'org.test:projectA:1.2' {
                expectHeadMetadata()
            }
            'org.test:projectA:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.release", "org.test:projectA:1.1")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.1.1' {
                withModule {
                    withStatus 'milestone'
                }
            }
            'org.test:projectA:1.1-beta2' {
                withModule {
                    withStatus 'integration'
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                expectGetMetadata()
                expectHeadArtifact()
            }
            'org.test:projectA:1.3' {
                expectHeadMetadata()
            }
            'org.test:projectA:1.2' {
                expectHeadMetadata()
            }
            'org.test:projectA:1.1.1' {
                expectGetMetadata()
            }
            'org.test:projectA:1.1' {
                expectHeadMetadata()
                expectHeadArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.release", "org.test:projectA:1.1")
            }
        }
    }

    @RequiredFeatures(
        @RequiredFeature(feature=GradleMetadataResolveRunner.GRADLE_METADATA, value="true")
    )
    // this test is a variant of the previous one, where we don't publish ivy.xml files, but publish Gradle module files only
    // it allows making sure that we actually read and write the "status" flag in Gradle metadata, because otherwise if the
    // Ivy file is present, it will be taken from there first
    void "latest.release selects highest version with release status using Gradle metadata only"() {
        given:
        buildFile << """
  dependencies {
      conf 'org.test:projectA:latest.release'
  }
  
  configurations {
      conf.resolutionStrategy.componentSelection.all { ComponentSelection s, ComponentMetadata d ->
         if (d.status != 'release') { s.reject('nope') } 
      }
  }
      
  dependencies.components.all { ComponentMetadataDetails details, IvyModuleDescriptor ivyModule ->
     def version = details.id.version
     def expectedStatus = [
        '2.0'        : 'integration',
        '1.3'        : 'integration',
        '1.2'        : 'milestone',
        '1.1'        : 'release',
        '1.1.1'      : 'milestone',
        '1.1-beta-2' : 'integration',
        '1.0'        : 'release',
     ]
     assert details.status == expectedStatus[version]
  }
  """
        when:
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any matches for org.test:projectA:latest.release as no versions of org.test:projectA are available.'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:2.0' {
                withModule {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                allowAll()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.release.
Versions that do not match:
    2.0
Searched in the following locations:
'''

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.3' {
                withModule {
                    withStatus 'integration'
                    withNoIvyMetaData()
                }
            }
            'org.test:projectA:1.2' {
                withModule {
                    withStatus 'milestone'
                    withNoIvyMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                allowAll()
            }
            'org.test:projectA:1.3' {
                allowAll()
            }
            'org.test:projectA:1.2' {
                allowAll()
            }
        }
        runAndFail 'checkDeps', '--refresh-dependencies'

        then:
        failureHasCause '''Could not find any version that matches org.test:projectA:latest.release.
Versions that do not match:
    2.0
    1.3
    1.2
Searched in the following locations:
'''

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.1' {
                withModule {
                    withStatus 'release'
                    withNoIvyMetaData()
                }
            }
            'org.test:projectA:1.0' {
                withModule {
                    withStatus 'release'
                    withNoIvyMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                allowAll()
            }
            'org.test:projectA:1.3' {
                allowAll()
            }
            'org.test:projectA:1.2' {
                allowAll()
            }
            'org.test:projectA:1.1' {
                allowAll()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.release", "org.test:projectA:1.1")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.1.1' {
                withModule {
                    withStatus 'milestone'
                    withNoIvyMetaData()
                }
            }
            'org.test:projectA:1.1-beta2' {
                withModule {
                    withStatus 'integration'
                    withNoIvyMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:2.0' {
                allowAll()
            }
            'org.test:projectA:1.3' {
                allowAll()
            }
            'org.test:projectA:1.2' {
                allowAll()
            }
            'org.test:projectA:1.1.1' {
                allowAll()
            }
            'org.test:projectA:1.1' {
                allowAll()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:latest.release", "org.test:projectA:1.1")
            }
        }
    }

    @Issue(["GRADLE-2502", "GRADLE-2794"])
    def "version selector ending in + selects highest matching version"() {
        given:
        buildFile << """
  dependencies {
      conf 'org.test:projectA:1.2+'
  }
  """
        and:
        repository {
            'org.test:projectA:1.1.2'()
            'org.test:projectA:2.0'()
        }

        when:
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
        }
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:1.2+'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2' {
                withModule {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2' {
                expectGetMetadata()
                expectHeadArtifact()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2.1'()
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2.1")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2.9'()
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2.9' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2.9")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2.10'()
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2.10' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:1.2+", "org.test:projectA:1.2.10")
            }
        }
    }

    @Issue("GRADLE-2502")
    def "version range selects highest matching version"() {
        given:
        buildFile << """
  dependencies {
      conf 'org.test:projectA:[1.2,2.0]'
  }
  """
        and:
        repository {
            'org.test:projectA' {
                '1.1.2'()
                '2.1'()
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
        }
        when:
        runAndFail 'checkDeps'

        then:
        failureHasCause 'Could not find any version that matches org.test:projectA:[1.2,2.0]'

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2' {
                withModule {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2' {
                expectGetMetadata()
                expectHeadArtifact()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.2")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.2.1'()
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.2.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.2.1")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.3'()
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.3' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.3")
            }
        }

        when:
        resetExpectations()
        repository {
            'org.test:projectA:1.4' {
                withModule {
                    withNoMetaData()
                }
            }
        }
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.4' {
                expectGetMetadata()
                expectHeadArtifact()
                expectGetArtifact()
            }
        }
        run 'checkDeps', '--refresh-dependencies'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.2,2.0]", "org.test:projectA:1.4")
            }
        }
    }

    @Issue("GRADLE-3334")
    def "can resolve version range with single value specified"() {
        given:
        buildFile << """
dependencies {
    conf group: "org.test", name: "projectA", version: "[1.1]"
}
"""
        and:
        repository {
            'org.test' {
                'projectB' {
                    '2.0'()
                }
                'projectA' {
                    '1.1' {
                        dependsOn 'org.test:projectB:[2.0]'
                    }
                }
            }
        }

        when:
        repositoryInteractions {
            'org.test:projectA' {
                expectVersionListing()
            }
            'org.test:projectA:1.1' {
                expectGetMetadata()
                expectGetArtifact()
            }
            if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                // todo: is single version in range something we want to allow in Gradle metadata?
                'org.test:projectB' {
                    expectVersionListing()
                }
            }
            'org.test:projectB:2.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }
        succeeds 'checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org.test:projectA:[1.1]", "org.test:projectA:1.1") {
                    if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                        edge("org.test:projectB:[2.0]", "org.test:projectB:2.0")
                    } else {
                        edge("org.test:projectB:2.0", "org.test:projectB:2.0") // Transitive version range is lost when converting to Ivy ModuleDescriptor
                    }
                }
            }
        }
    }

}
