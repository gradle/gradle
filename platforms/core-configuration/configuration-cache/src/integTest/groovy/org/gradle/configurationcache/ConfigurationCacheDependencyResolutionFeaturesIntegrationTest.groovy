/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import groovy.transform.Canonical
import org.gradle.api.tasks.TasksWithInputsAndOutputs
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule

import java.util.concurrent.TimeUnit

import static org.gradle.api.internal.artifacts.verification.DependencyVerificationFixture.getChecksum

class ConfigurationCacheDependencyResolutionFeaturesIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements TasksWithInputsAndOutputs {
    @Rule
    HttpServer server = new HttpServer()
    def remoteRepo = new MavenHttpRepository(server, mavenRepo)

    @Override
    def setup() {
        // So that dependency resolution results from previous tests do not interfere
        executer.requireOwnGradleUserHomeDir()
    }

    @Canonical
    class RepoFixture {
        MavenHttpRepository repository
        Closure<Void> cleanup

        URI getUri() {
            repository.uri
        }
    }

    def "does not invalidate configuration cache entry when dynamic version information has not expired"() {
        given:
        RepoFixture defaultRepo = new RepoFixture(remoteRepo)
        List<RepoFixture> repos = scenario == DynamicVersionScenario.SINGLE_REPO
            ? [defaultRepo]
            : [defaultRepo, repoWithout('thing', 'lib')]

        server.start()

        remoteRepo.module("thing", "lib", "1.2").publish()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation
            }

            ${repositoriesBlockFor(repos)}

            dependencies {
                implementation 'thing:lib:1.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        remoteRepo.getModuleMetaData("thing", "lib").expectGet()
        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when version list is already cached when configuration cache entry is written
        configurationCacheRun("resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        cleanup:
        cleanUpAll repos

        where:
        scenario << DynamicVersionScenario.values()
    }

    def "invalidates configuration cache entry when dynamic version information has expired"() {
        given:
        RepoFixture defaultRepo = new RepoFixture(remoteRepo)
        List<RepoFixture> repos = scenario == DynamicVersionScenario.SINGLE_REPO
            ? [defaultRepo]
            : [defaultRepo, repoWithout('thing', 'lib')]

        server.start()

        remoteRepo.module("thing", "lib", "1.2").publish()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation {
                    resolutionStrategy.cacheDynamicVersionsFor(4, ${TimeUnit.name}.HOURS)
                }
            }

            ${repositoriesBlockFor(repos)}

            dependencies {
                implementation 'thing:lib:1.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        remoteRepo.getModuleMetaData("thing", "lib").expectGet()
        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when version list is already cached when configuration cache entry is written
        configurationCacheRun("resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        def clockOffset = TimeUnit.MILLISECONDS.convert(4, TimeUnit.HOURS)
        remoteRepo.getModuleMetaData("thing", "lib").expectHead()
        configurationCacheRun("resolve1", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached version information for thing:lib:1.+ has expired.")
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve1", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve2", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached version information for thing:lib:1.+ has expired.")
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve2", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        cleanup:
        cleanUpAll repos

        where:
        scenario << DynamicVersionScenario.values()
    }

    private RepoFixture repoWithout(String group, String artifact) {
        HttpServer server = new HttpServer()
        MavenHttpRepository repo = new MavenHttpRepository(server, '/empty', maven(file('empty')))
        server.start()
        repo.getModuleMetaData(group, artifact).expectGetMissing()
        new RepoFixture(repo, { server.stop() })
    }

    enum DynamicVersionScenario {
        SINGLE_REPO, MATCHING_REPO_PLUS_404

        @Override
        String toString() {
            super.toString().split('_').collect { it.toLowerCase() }.join(' ')
        }
    }

    private static String repositoriesBlockFor(List<RepoFixture> fixtures) {
        """
            repositories {
                ${fixtures.collect { "maven { url = '${it.uri}' }" }.join('\n')}
            }
        """
    }

    private cleanUpAll(List<RepoFixture> fixtures) {
        fixtures.forEach {
            it.cleanup?.call()
        }
    }

    def "does not invalidate configuration cache entry when changing artifact information has not expired"() {
        given:
        server.start()

        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation('thing:lib:1.3') {
                    changing = true
                }
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when artifact information is cached
        configurationCacheRun("resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")
    }

    def "invalidates configuration cache entry when changing artifact information has expired"() {
        given:
        server.start()

        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation {
                    resolutionStrategy.cacheChangingModulesFor(4, ${TimeUnit.name}.HOURS)
                }
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation('thing:lib:1.3') {
                    changing = true
                }
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when: // run again with different tasks, to verify behaviour when artifact information is cached
        configurationCacheRun("resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        v3.pom.expectHead()
        v3.artifact.expectHead()
        def clockOffset = TimeUnit.MILLISECONDS.convert(4, TimeUnit.HOURS)
        configurationCacheRun("resolve1", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached artifact information for thing:lib:1.3 has expired.")
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve2", "-Dorg.gradle.internal.test.clockoffset=${clockOffset}")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because cached artifact information for thing:lib:1.3 has expired.")
        outputContains("result = [lib-1.3.jar]")
    }

    // This documents current behaviour, rather than desired behaviour. The contents of the artifact does not affect the contents of the task graph and so should not be treated as an input
    def "reports changes to artifact in file repository"() {
        repo.setup(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.1'
                resolve2 'thing:lib1:2.1'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib1-2.1.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        when:
        repo.publishWithDifferentArtifactContent(this)
        configurationCacheRun("resolve1", "resolve2")

        then:
        // This should be a cache hit
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${repo.metadataLocation}' has changed.")
        outputContains("result = [lib1-2.1.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        where:
        repo                 | _
        new MavenFileRepo()  | _
        new IvyFileRepo()    | _
        new MavenLocalRepo() | _
    }

    def "reports changes to metadata in file repository"() {
        repo.setup(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.1'
                resolve2 'thing:lib1:2.1'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib1-2.1.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        when:
        repo.publishWithDifferentDependencies(this)
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${repo.metadataLocation}' has changed.")
        outputContains("result = [lib1-2.1.jar, lib2-4.0.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar, lib2-4.0.jar]")

        where:
        repo                 | _
        new MavenFileRepo()  | _
        new IvyFileRepo()    | _
        new MavenLocalRepo() | _
    }

    def "reports changes to matching versions in file repository"() {
        repo.setup(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.+'
                resolve2 'thing:lib1:2.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib1-2.1.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.1.jar]")

        when:
        repo.publishNewVersion(this)
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because ${repo.newVersionInvalidatedResource} has changed.")
        outputContains("result = [lib1-2.5.jar, lib2-4.0.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.5.jar, lib2-4.0.jar]")

        where:
        repo                | _
        new MavenFileRepo() | _
        new IvyFileRepo()   | _
        // Maven local does not support dynamic versions
    }

    def "reports changes to matching snapshot versions in file repository"() {
        repo.setup(this)
        repo.publishSnapshot(this)
        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                resolve1
                resolve2
            }
            dependencies {
                resolve1 'thing:lib1:2.0-SNAPSHOT'
                resolve2 'thing:lib1:2.0-SNAPSHOT'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.resolve1)
            }
            task resolve2(type: ShowFilesTask) {
                inFiles.from(configurations.resolve2)
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib1-${repo.snapshotVersion}.jar, lib2-4.0.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-${repo.snapshotVersion}.jar, lib2-4.0.jar]")

        when:
        repo.publishNewSnapshot(this)
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because ${repo.newSnapshotInvalidatedResource} has changed.")
        outputContains("result = [lib1-${repo.newSnapshotVersion}.jar, lib2-4.1.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-${repo.newSnapshotVersion}.jar, lib2-4.1.jar]")

        where:
        repo                 | _
        new MavenFileRepo()  | _
        new MavenLocalRepo() | _
    }

    def "disables configuration cache when --export-keys is used"() {
        given:
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("help")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("help", "--export-keys")

        then:
        configurationCache.assertNoConfigurationCache()
        outputContains("Calculating task graph as configuration cache cannot be reused due to --export-keys")
    }

    def "invalidates configuration cache when dependency lock file changes"() {
        server.start()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation {
                    resolutionStrategy.activateDependencyLocking()
                }
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation 'thing:lib:1.+'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """

        def configurationCache = newConfigurationCacheFixture()

        def moduleMetaData = remoteRepo.getModuleMetaData("thing", "lib")
        moduleMetaData.expectGet()
        v3.pom.expectGet()
        v3.artifact.expectGet()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("result = [lib-1.3.jar]")

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.3.jar]")

        when:
        def v4 = remoteRepo.module("thing", "lib", "1.4").publish()
        moduleMetaData.expectHead()
        moduleMetaData.expectGet()
        v4.pom.expectGet()
        v4.artifact.expectGet()

        run("resolve1", "--write-locks", "--refresh-dependencies")

        then:
        outputContains("result = [lib-1.4.jar]")

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        def lockFile = 'gradle.lockfile'
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${lockFile}' has changed.")
        outputContains("result = [lib-1.4.jar]")

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib-1.4.jar]")

        when:
        file(lockFile).delete()
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${lockFile}' has been removed.")
        outputContains("result = [lib-1.4.jar]")
    }

    def "invalidates cache when verification file changes"() {
        server.start()
        def v3 = remoteRepo.module("thing", "lib", "1.3").publish()
        def checkSum = getChecksum(v3, "sha256")
        v3.allowAll()

        taskTypeLogsInputFileCollectionContent()
        buildFile << """
            configurations {
                implementation
            }

            repositories { maven { url = '${remoteRepo.uri}' } }

            dependencies {
                implementation 'thing:lib:1.3'
            }

            task resolve1(type: ShowFilesTask) {
                inFiles.from(configurations.implementation)
            }
        """

        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun("resolve1", "--write-verification-metadata", "sha256")

        then:
        def verificationFile = file("gradle/verification-metadata.xml")
        verificationFile.isFile()

        // TODO - get a false cache miss here because the content of the metadata file changes during the previous build
        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'gradle/verification-metadata.xml' has changed.".replace('/', File.separator))

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()

        when:
        verificationFile.replace("<sha256 value=\"$checkSum\"", '<sha256 value="12345"')
        configurationCacheFails("resolve1")

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'gradle/verification-metadata.xml' has changed.".replace('/', File.separator))
        failure.assertHasCause("Dependency verification failed for configuration ':implementation'")
        configurationCache.assertStateStoreFailed()

        when:
        verificationFile.replace('<sha256 value="12345"', "<sha256 value=\"$checkSum\"")
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun("resolve1")

        then:
        configurationCache.assertStateLoaded()

        when:
        verificationFile.delete()
        configurationCacheRun("resolve1")

        then:
        outputContains("Calculating task graph as configuration cache cannot be reused because file 'gradle/verification-metadata.xml' has been removed.".replace('/', File.separator))
        configurationCache.assertStateStored()
    }

    abstract class FileRepoSetup {
        @Override
        String toString() {
            return getClass().simpleName
        }

        String getVersionMetadataLocation() {
            return 'maven-repo/thing/lib1/maven-metadata.xml'.replace('/', File.separator)
        }

        abstract String getMetadataLocation()

        abstract String getNewVersionInvalidatedResource()

        abstract void setup(AbstractIntegrationSpec owner)

        abstract void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner)

        abstract void publishWithDifferentDependencies(AbstractIntegrationSpec owner)

        abstract void publishNewVersion(AbstractIntegrationSpec owner)
    }

    abstract class MavenRepoSetup extends FileRepoSetup {
        abstract String getSnapshotVersion()

        abstract String getNewSnapshotInvalidatedResource()

        abstract String getNewSnapshotVersion()

        abstract void publishSnapshot(AbstractIntegrationSpec owner)

        abstract void publishNewSnapshot(AbstractIntegrationSpec owner)
    }

    class MavenFileRepo extends MavenRepoSetup {
        @Override
        String getMetadataLocation() {
            return 'maven-repo/thing/lib1/2.1/lib1-2.1.pom'.replace('/', File.separator)
        }

        @Override
        String getNewVersionInvalidatedResource() {
            return "file '$versionMetadataLocation'"
        }

        @Override
        void setup(AbstractIntegrationSpec owner) {
            owner.with {
                mavenRepo.module("thing", "lib1", "2.1").publish()
                buildFile << """
                    repositories {
                        maven {
                            url = '${mavenRepo.uri}'
                        }
                    }
                """
            }
        }

        @Override
        void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner) {
            owner.with {
                mavenRepo.module("thing", "lib1", "2.1").publishWithChangedContent()
            }
        }

        @Override
        void publishWithDifferentDependencies(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = mavenRepo.module("thing", "lib2", "4.0").publish()
                mavenRepo.module("thing", "lib1", "2.1").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewVersion(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = mavenRepo.module("thing", "lib2", "4.0").publish()
                mavenRepo.module("thing", "lib1", "2.5").dependsOn(dep).publish()
            }
        }

        @Override
        String getSnapshotVersion() {
            return "2.0-20100101.120001-1"
        }

        @Override
        String getNewSnapshotInvalidatedResource() {
            return "file 'maven-repo/thing/lib1/2.0-SNAPSHOT/maven-metadata.xml'".replace('/', File.separator)
        }

        @Override
        String getNewSnapshotVersion() {
            return "2.0-20100101.120002-2"
        }

        @Override
        void publishSnapshot(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = mavenRepo.module("thing", "lib2", "4.0").publish()
                mavenRepo.module("thing", "lib1", "2.0-SNAPSHOT").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewSnapshot(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = mavenRepo.module("thing", "lib2", "4.1").publish()
                def module = mavenRepo.module("thing", "lib1", "2.0-SNAPSHOT").dependsOn(dep)
                module.publishCount++
                module.publish()
            }
        }
    }

    class IvyFileRepo extends FileRepoSetup {
        @Override
        String getMetadataLocation() {
            return 'ivy-repo/thing/lib1/2.1/ivy-2.1.xml'.replace('/', File.separator)
        }

        @Override
        String getNewVersionInvalidatedResource() {
            return "directory 'ivy-repo/thing/lib1'".replace('/', File.separator)
        }

        @Override
        void setup(AbstractIntegrationSpec owner) {
            owner.with {
                ivyRepo.module("thing", "lib1", "2.1").publish()
                buildFile << """
                    repositories {
                        ivy {
                            url = '${ivyRepo.uri}'
                        }
                    }
                """
            }
        }

        @Override
        void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner) {
            owner.with {
                ivyRepo.module("thing", "lib1", "2.1").publishWithChangedContent()
            }
        }

        @Override
        void publishWithDifferentDependencies(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = ivyRepo.module("thing", "lib2", "4.0").publish()
                ivyRepo.module("thing", "lib1", "2.1").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewVersion(AbstractIntegrationSpec owner) {
            owner.with {
                def dep = ivyRepo.module("thing", "lib2", "4.0").publish()
                ivyRepo.module("thing", "lib1", "2.5").dependsOn(dep).publish()
            }
        }
    }

    class MavenLocalRepo extends MavenRepoSetup {
        @Override
        String getMetadataLocation() {
            return 'maven_home/.m2/repository/thing/lib1/2.1/lib1-2.1.pom'.replace('/', File.separator)
        }

        @Override
        String getNewVersionInvalidatedResource() {
            throw new UnsupportedOperationException()
        }

        @Override
        void setup(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                m2.mavenRepo().module("thing", "lib1", "2.1").publish()
                buildFile << """
                    repositories {
                        mavenLocal()
                    }
                """
            }
        }

        @Override
        void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                m2.mavenRepo().module("thing", "lib1", "2.1").publishWithChangedContent()
            }
        }

        @Override
        void publishWithDifferentDependencies(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                def dep = m2.mavenRepo().module("thing", "lib2", "4.0").publish()
                m2.mavenRepo().module("thing", "lib1", "2.1").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewVersion(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                def dep = m2.mavenRepo().module("thing", "lib2", "4.0").publish()
                m2.mavenRepo().module("thing", "lib1", "2.5").dependsOn(dep).publish()
            }
        }

        @Override
        String getSnapshotVersion() {
            return "2.0-SNAPSHOT"
        }

        @Override
        String getNewSnapshotVersion() {
            return snapshotVersion
        }

        @Override
        String getNewSnapshotInvalidatedResource() {
            return "file 'maven_home/.m2/repository/thing/lib1/2.0-SNAPSHOT/lib1-2.0-SNAPSHOT.pom'".replace('/', File.separator)
        }

        @Override
        void publishSnapshot(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                def dep = m2.mavenRepo().module("thing", "lib2", "4.0").publish()
                m2.mavenRepo().module("thing", "lib1", "2.0-SNAPSHOT").dependsOn(dep).publish()
            }
        }

        @Override
        void publishNewSnapshot(AbstractIntegrationSpec owner) {
            owner.with {
                m2.execute(executer)
                def dep = m2.mavenRepo().module("thing", "lib2", "4.1").publish()
                m2.mavenRepo().module("thing", "lib1", "2.0-SNAPSHOT").dependsOn(dep).publish()
            }
        }
    }
}
