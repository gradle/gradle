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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

@RequiredFeatures([
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "ivy"),
    // we only need to check without experimental, it doesn't depend on this flag
    @RequiredFeature(feature = GradleMetadataResolveRunner.EXPERIMENTAL_RESOLVE_BEHAVIOR, value = "false"),
    // we only need to check without Gradle metadata, it doesn't matter either
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false"),
])
class IvyCustomVersionListerIntegrationTest extends AbstractModuleDependencyResolveTest {
    void "can list versions without hitting repository"() {
        withLister([testA: [1, 2, 3]])
        given:
        repository {
            'org:testA:1'()
            'org:testA:2'()
            'org:testA:3'()
        }
        buildFile << """
            dependencies {
                conf "org:testA:+"
            }
        """

        when:
        repositoryInteractions {
            'org:testA:3' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'
    }

    void "falls back to repository listing when no version is listed"() {
        withLister([:])
        given:
        repository {
            'org:testA:1'()
            'org:testA:2'()
            'org:testA:3'()
        }
        buildFile << """
            dependencies {
                conf "org:testA:+"
            }
        """

        when:
        repositoryInteractions {
            'org:testA' {
                expectVersionListing()
            }
            'org:testA:3' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'
    }

    void "doesn't fallback to repository listing when empty list version is returned"() {
        withLister([testA:[]])
        given:
        buildFile << """
            dependencies {
                conf "org:testA:+"
            }
        """

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("Could not find any matches for org:testA:+ as no versions of org:testA are available.")
    }

    void "can version listing can use module identifer to return the version list"() {
        withLister([testA: [1, 2, 3], testB: [1, 2]])
        given:
        repository {
            'org:testA:1'()
            'org:testA:2'()
            'org:testA:3'()
            'org:testB:1'()
            'org:testB:2'()
        }
        buildFile << """
            dependencies {
                conf "org:testA:+"
                conf "org:testB:+"
            }
        """

        when:
        repositoryInteractions {
            'org:testA:3' {
                expectResolve()
            }
            'org:testB:2' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'
    }

    void "caches version listing"() {
        withLister([testA: [1, 2, 3]], true)
        given:
        repository {
            'org:testA:1'()
            'org:testA:2'()
            'org:testA:3'()
        }
        buildFile << """
            dependencies {
                conf "org:testA:+"
            }
        """

        when:
        repositoryInteractions {
            'org:testA:3' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'
        outputContains("Listing versions for module testA")
        resetExpectations()

        when:
        repositoryInteractions {
            'org:testA:3' {
                allowAll()
            }
        }
        succeeds 'checkDeps'

        then:
        outputDoesNotContain("Listing versions for module testA")
        resetExpectations()

        when:
        repositoryInteractions {
            'org:testA:3' {
                allowAll()
            }
        }
        succeeds 'checkDeps', '--refresh-dependencies'

        then:
        outputContains("Listing versions for module testA")
    }


    private void withLister(Map<String, List<String>> moduleToVersions, boolean logQueries=false) {
        metadataListerClass = 'MyLister'
        def listing = moduleToVersions.collect { k, v ->
            "                    if (name=='$k') { details.listed([${v.collect { "'$it'" }.join(',')}]) }"
        }.join(' else \n')
        buildFile << """
            class MyLister implements ComponentMetadataVersionLister {
                void execute(ComponentMetadataListerDetails details) {
                    def name = details.moduleIdentifier.name
                    if ($logQueries) { println("Listing versions for module \$name") }
$listing
                }
            }
        """

    }


}
