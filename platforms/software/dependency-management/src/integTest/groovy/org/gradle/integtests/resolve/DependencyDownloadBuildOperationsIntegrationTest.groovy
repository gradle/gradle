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

import org.gradle.api.internal.artifacts.DownloadArtifactBuildOperationType
import org.gradle.api.internal.artifacts.ResolveArtifactsBuildOperationType
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.resource.ExternalResourceAlreadyPresentBuildOperationType
import org.gradle.internal.resource.ExternalResourceListBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.gradle.internal.resource.ExternalResourceReadMetadataBuildOperationType

class DependencyDownloadBuildOperationsIntegrationTest extends AbstractHttpDependencyResolutionTest {

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "emits events for dependency resolution downloads - chunked: #chunked"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        buildFile << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }

            configurations {
                base { canBeResolved = false; canBeConsumed = false }
                path { extendsFrom(base) }
            }

            dependencies {
                base "org.utils:impl:1.3"
            }

            println configurations.path.files
        """

        mavenHttpRepo.server.chunkedTransfer = chunked

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 2
        downloadOps[0].details.location == m.pom.uri.toString()
        downloadOps[0].result.bytesRead == m.pom.file.length()
        !downloadOps[0].result.missing
        downloadOps[1].details.location == m.artifact.uri.toString()
        downloadOps[1].result.bytesRead == m.artifact.file.length()
        !downloadOps[0].result.missing

        // TODO - should have an event for graph resolution as well

        def artifactsOps = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps.size() == 1

        def artifactOps = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps.size() == 1
        artifactOps[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'
        artifactOps[0].result.resolvedFilePath.endsWith('impl-1.3.jar')
        artifactOps[0].result.fileSize == m.artifact.file.length()

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps2 = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps2.size() == 2
        metaDataOps2[0].details.location == m.pom.uri.toString()
        metaDataOps2[1].details.location == m.artifact.uri.toString()

        def artifactsOps2 = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps2.size() == 1

        def artifactOps2 = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps2.size() == 1
        artifactOps2[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'
        artifactOps2[0].result.resolvedFilePath.endsWith('impl-1.3.jar')
        artifactOps2[0].result.fileSize == m.artifact.file.length()

        where:
        chunked << [true, false]
    }

    def "emits ExternalResourceAlreadyPresent events when artifacts are served from the local cache"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        buildFile << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }

            configurations {
                base { canBeResolved = false; canBeConsumed = false }
                path { extendsFrom(base) }
            }

            dependencies {
                base "org.utils:impl:1.3"
            }

            println configurations.path.files
        """

        when:
        // First invocation: nothing cached yet, artifacts are downloaded
        run "help"

        then:
        buildOperations.all(ExternalResourceAlreadyPresentBuildOperationType).size() == 0

        when:
        // Second invocation without --refresh-dependencies: cache is still valid
        // and the resource accessor returns from cache without any network traffic.
        run "help"

        then:
        buildOperations.all(ExternalResourceReadBuildOperationType).size() == 0
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def alreadyPresentOps = buildOperations.all(ExternalResourceAlreadyPresentBuildOperationType)
        alreadyPresentOps.size() == 2
        alreadyPresentOps*.details*.location as Set == [m.pom.uri.toString(), m.artifact.uri.toString()] as Set
        alreadyPresentOps.find { it.details.location == m.artifact.uri.toString() }.with {
            it.result.resolvedFilePath.endsWith('impl-1.3.jar')
            it.result.fileSize == m.artifact.file.length()
        }
        alreadyPresentOps.find { it.details.location == m.pom.uri.toString() }.with {
            it.result.resolvedFilePath.endsWith('impl-1.3.pom')
            it.result.fileSize == m.pom.file.length()
        }
    }

    def "emits events for missing resources"() {
        given:
        def emptyRepo = mavenHttpRepo("empty")
        def missing = emptyRepo.module("org.utils", "impl", "1.3").allowAll()
        missing.rootMetaData.allowGetOrHead()
        def missingDir = emptyRepo.directory("org.utils", "impl")
        missingDir.allowGet()

        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()
        m.rootMetaData.file.delete()
        m.rootMetaData.allowGetOrHead()
        def dir = mavenHttpRepo.directory("org.utils", "impl")
        dir.allowGet()

        buildFile << """
            repositories {
                maven {
                    url = "${emptyRepo.uri}"
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
                maven {
                    url = "${mavenHttpRepo.uri}"
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }

            configurations {
                base { canBeResolved = false; canBeConsumed = false }
                path { extendsFrom(base) }
            }

            dependencies {
              base "org.utils:impl:1.+"
            }

            println configurations.path.files
        """

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 4
        downloadOps[0].details.location == missing.rootMetaData.uri.toString()
        downloadOps[0].result.bytesRead == 0
        downloadOps[0].result.missing
        downloadOps[1].details.location == m.rootMetaData.uri.toString()
        downloadOps[1].result.bytesRead == 0
        downloadOps[1].result.missing
        downloadOps[2].details.location == m.pom.uri.toString()
        downloadOps[2].result.bytesRead == m.pom.file.length()
        downloadOps[3].details.location == m.artifact.uri.toString()
        downloadOps[3].result.bytesRead == m.artifact.file.length()

        def listOps = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps.size() == 2
        listOps[0].details.location == missingDir.uri.toString()
        listOps[1].details.location == dir.uri.toString()

        def artifactsOps = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps.size() == 1

        def artifactOps = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps.size() == 1
        artifactOps[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps2 = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps2.size() == 2
        metaDataOps2[0].details.location == m.pom.uri.toString()
        metaDataOps2[1].details.location == m.artifact.uri.toString()

        def listOps2 = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps2.size() == 2
        listOps[0].details.location == missingDir.uri.toString()
        listOps[1].details.location == dir.uri.toString()

        def downloadOps2 = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps2.size() == 2
        downloadOps2[0].details.location == missing.rootMetaData.uri.toString()
        downloadOps2[0].result.bytesRead == 0
        downloadOps2[0].result.missing
        downloadOps2[1].details.location == m.rootMetaData.uri.toString()
        downloadOps2[1].result.bytesRead == 0
        downloadOps2[1].result.missing

        def artifactsOps2 = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps2.size() == 1

        def artifactOps2 = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps2.size() == 1
        artifactOps2[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'
    }

    def "emits events for dynamic dependency resolution"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()
        m.rootMetaData.file.delete()
        m.rootMetaData.allowGetOrHead()
        def dir = mavenHttpRepo.directory("org.utils", "impl")
        dir.allowGet()

        buildFile << """
            repositories {
                maven {
                    url = "${mavenHttpRepo.uri}"
                    metadataSources {
                        mavenPom()
                        artifact()
                    }
                }
            }

            configurations {
                base { canBeResolved = false; canBeConsumed = false }
                path { extendsFrom(base) }
            }

            dependencies {
              base "org.utils:impl:1.+"
            }

            println configurations.path.files
        """

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 3
        downloadOps[0].details.location == m.rootMetaData.uri.toString()
        downloadOps[0].result.bytesRead == 0
        downloadOps[0].result.missing
        downloadOps[1].details.location == m.pom.uri.toString()
        downloadOps[1].result.bytesRead == m.pom.file.length()
        !downloadOps[1].result.missing
        downloadOps[2].details.location == m.artifact.uri.toString()
        downloadOps[2].result.bytesRead == m.artifact.file.length()
        !downloadOps[2].result.missing

        def listOps = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps.size() == 1
        listOps[0].details.location == dir.uri.toString()

        def artifactsOps = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps.size() == 1

        def artifactOps = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps.size() == 1
        artifactOps[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps2 = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps2.size() == 2
        metaDataOps2[0].details.location == m.pom.uri.toString()
        metaDataOps2[1].details.location == m.artifact.uri.toString()

        def listOps2 = buildOperations.all(ExternalResourceListBuildOperationType)
        listOps2.size() == 1
        listOps2[0].details.location == dir.uri.toString()

        def downloadOps2 = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps2.size() == 1
        downloadOps2[0].details.location == m.rootMetaData.uri.toString()
        downloadOps2[0].result.bytesRead == 0
        downloadOps2[0].result.missing

        def artifactsOps2 = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps2.size() == 1

        def artifactOps2 = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps2.size() == 1
        artifactOps2[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'
    }

    def "emits DownloadArtifactBuildOperationType synthetically on configuration cache hit"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        buildFile << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }

            configurations {
                base { canBeResolved = false; canBeConsumed = false }
                path { extendsFrom(base) }
            }

            dependencies {
                base "org.utils:impl:1.3"
            }

            tasks.register('consume') {
                def files = configurations.path
                doLast {
                    files.each { println it }
                }
            }
        """

        when:
        // First run: configuration cache miss, dependency is resolved normally.
        executer.withArgument("--configuration-cache")
        run "consume"

        then:
        def artifactOps = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps.size() == 1
        artifactOps[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'
        artifactOps[0].result.resolvedFilePath.endsWith('impl-1.3.jar')
        artifactOps[0].result.fileSize == m.artifact.file.length()

        when:
        // Second run: configuration cache HIT. The original resolution pipeline is skipped but
        // the synthetic build operation is emitted during artifact deserialization.
        executer.withArgument("--configuration-cache")
        run "consume"

        then:
        def replayedOps = buildOperations.all(DownloadArtifactBuildOperationType)
        replayedOps.size() == 1
        replayedOps[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'
        replayedOps[0].result.resolvedFilePath.endsWith('impl-1.3.jar')
        replayedOps[0].result.fileSize == m.artifact.file.length()
        // On CC hit, no network read happens at all.
        buildOperations.all(ExternalResourceReadBuildOperationType).isEmpty()
        // A synthetic ExternalResourceAlreadyPresent child is emitted, so consumers see a consistent
        // parent-child structure on both CC miss and CC hit. The location is empty because the
        // original request URI is not persisted in CC state.
        def replayedAlreadyPresentOps = buildOperations.all(ExternalResourceAlreadyPresentBuildOperationType)
        replayedAlreadyPresentOps.size() == 1
        replayedAlreadyPresentOps[0].details.location == ''
        replayedAlreadyPresentOps[0].result.resolvedFilePath.endsWith('impl-1.3.jar')
        replayedAlreadyPresentOps[0].result.fileSize == m.artifact.file.length()
    }

    def "emits events for an artifact once per build"() {
        given:
        def m = mavenHttpRepo.module("org.utils", "impl", '1.3')
            .allowAll()
            .publish()

        buildFile << """
            repositories {
                maven { url = "${mavenHttpRepo.uri}" }
            }

            configurations {
                base { canBeResolved = false; canBeConsumed = false }
                primary { extendsFrom base }
                more { extendsFrom base }
            }

            dependencies {
              base "org.utils:impl:1.3"
            }

            println configurations.primary.files
            println configurations.primary.files
            println configurations.more.files
        """

        when:
        run "help"

        then:
        buildOperations.all(ExternalResourceReadMetadataBuildOperationType).size() == 0

        def downloadOps = buildOperations.all(ExternalResourceReadBuildOperationType)
        downloadOps.size() == 2
        downloadOps[0].details.location == m.pom.uri.toString()
        downloadOps[0].result.bytesRead == m.pom.file.length()
        !downloadOps[0].result.missing
        downloadOps[1].details.location == m.artifact.uri.toString()
        downloadOps[1].result.bytesRead == m.artifact.file.length()
        !downloadOps[1].result.missing

        def artifactsOps = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps.size() == 3

        def artifactOps = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps.size() == 1
        artifactOps[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'

        when:
        executer.withArguments("--refresh-dependencies")
        run "help"

        then:
        def metaDataOps2 = buildOperations.all(ExternalResourceReadMetadataBuildOperationType)
        metaDataOps2.size() == 2
        metaDataOps2[0].details.location == m.pom.uri.toString()
        metaDataOps2[1].details.location == m.artifact.uri.toString()

        buildOperations.all(ExternalResourceReadBuildOperationType).size() == 0

        def artifactsOps2 = buildOperations.all(ResolveArtifactsBuildOperationType)
        artifactsOps2.size() == 3

        def artifactOps2 = buildOperations.all(DownloadArtifactBuildOperationType)
        artifactOps2.size() == 1
        artifactOps2[0].details.artifactIdentifier == 'impl-1.3.jar (org.utils:impl:1.3)'

        where:
        chunked << [true, false]
    }

}
