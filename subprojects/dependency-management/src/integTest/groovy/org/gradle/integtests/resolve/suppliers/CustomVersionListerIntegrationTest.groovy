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

package org.gradle.integtests.resolve.suppliers

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest

// we only need to check without Gradle metadata, it doesn't matter
@RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
class CustomVersionListerIntegrationTest extends AbstractModuleDependencyResolveTest {
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
        withLister([testA: []])
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

    void "can version listing can use module identifier to return the version list"() {
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

    void "caches version listing using #lister lister"() {
        ListerInteractions listerInteractions
        switch (lister) {
            case 'simple':
                listerInteractions = withLister(modules, true)
                break;
            default:
                listerInteractions = withExternalResourceLister(modules, true)
                break;
        }
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
        succeeds 'checkDeps'

        then:
        outputDoesNotContain("Listing versions for module testA")
        resetExpectations()

        when:
        repositoryInteractions {
            "org:testA:3" {
                expectHeadMetadata()
                expectHeadArtifact()
            }
        }
        listerInteractions.expectRefresh('testA')
        succeeds 'checkDeps', '--refresh-dependencies'

        then:
        outputContains("Listing versions for module testA")

        where:
        lister               | modules
        'simple'             | [testA: [1, 2, 3]]
        'file on repository' | [testA: [1, 2, 3]]
    }

    void "can recover from broken lister"() {
        withBrokenLister()
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
        fails 'checkDeps', '-PbreakBuild=true'

        then:
        failure.assertHasCause("Failed to list versions for org:testA.")
        failure.assertHasCause("oh noes!")

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

    def "can recover from --offline mode"() {
        withLister(['testA': [1, 2, 3]])

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
        fails 'checkDeps', '--offline'

        then:
        failure.assertHasCause("No cached version listing for org:testA:+ available for offline mode")

        when:
        repositoryInteractions {
            'org:testA:3' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDeps'
    }

    def "can use result from lister in --offline mode"() {
        withLister(['testA': [1, 2, 3]])

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

        when:
        resetExpectations()

        then:
        succeeds 'checkDeps', '--refresh-dependencies', '--offline'
    }

    private ListerInteractions withLister(Map<String, List<String>> moduleToVersions, boolean logQueries = false) {
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
        new SimpleListerInteractions()
    }

    private ListerInteractions withBrokenLister() {
        buildFile << """
            class BrokenLister implements ComponentMetadataVersionLister {
                private final boolean breakBuild

                @javax.inject.Inject
                BrokenLister(boolean breakBuild) { this.breakBuild = breakBuild }

                void execute(ComponentMetadataListerDetails details) {
                    if (breakBuild) { throw new RuntimeException("oh noes!") }
                }
            }
        """
        setMetadataListerClassWithParams('BrokenLister', 'project.findProperty(\'breakBuild\')?true:false')
        new SimpleListerInteractions()
    }

    private ListerInteractions withExternalResourceLister(Map<String, List<String>> moduleToVersions, boolean logQueries = false) {
        metadataListerClass = 'MyLister'
        buildFile << """
            class MyLister implements ComponentMetadataVersionLister {

                final RepositoryResourceAccessor repositoryResourceAccessor

                @javax.inject.Inject
                MyLister(RepositoryResourceAccessor accessor) { repositoryResourceAccessor = accessor }

                void execute(ComponentMetadataListerDetails details) {
                    def id = details.moduleIdentifier
                    if ($logQueries) { println("Listing versions for module \$id.name") }
                    repositoryResourceAccessor.withResource("\${id.group}/\${id.name}/versions.txt") {
                        def versions = (new String(it.bytes)).split(',') as List
                        details.listed(versions)
                    }
                }
            }
        """
        def files = [:]
        moduleToVersions.each { module, versions ->
            def file = temporaryFolder.createFile("versions-${module}.txt")
            file << versions.join(',')
            files[module] = file
            server.allowGetOrHead("/repo/org/$module/versions.txt", file)
        }
        new ExternalResourceListerInteractions(files)
    }

    interface ListerInteractions {
        void expectRefresh(String... modules)
    }

    class SimpleListerInteractions implements ListerInteractions {

        @Override
        void expectRefresh(String... modules) {

        }
    }

    class ExternalResourceListerInteractions implements ListerInteractions {
        private final Map<String, File> files

        ExternalResourceListerInteractions(Map<String, File> files) {
            this.files = files
        }

        @Override
        void expectRefresh(String... modules) {
            modules.each {
                server.expectHead("/repo/org/$it/versions.txt", files[it])
            }
        }
    }


}
