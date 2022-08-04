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

    def "does not invalidate configuration cache entry when dynamic version information has not expired (#scenario)"() {
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

    def "invalidates configuration cache entry when dynamic version information has expired (#scenario)"() {
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
    def "reports changes to artifact in #repo.displayName"() {
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

    def "reports changes to metadata in #repo.displayName"() {
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

    def "reports changes to matching versions in #repo.displayName"() {
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
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${repo.versionMetadataLocation}' has changed.")
        outputContains("result = [lib1-2.5.jar, lib2-4.0.jar]")

        when:
        configurationCacheRun("resolve1", "resolve2")

        then:
        configurationCache.assertStateLoaded()
        outputContains("result = [lib1-2.5.jar, lib2-4.0.jar]")

        where:
        repo                | _
        new MavenFileRepo() | _
        // TODO - Ivy file repos + dynamic versions ignores changes
        // Maven local does not support dynamic versions
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
        noExceptionThrown()

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
        outputContains("Calculating task graph as configuration cache cannot be reused because file '${lockFile}' has changed.")
        outputContains("result = [lib-1.4.jar]")
    }

    abstract class FileRepoSetup {
        abstract String getDisplayName()

        abstract String getProblemDisplayName()

        String getVersionMetadataLocation() {
            return 'maven-repo/thing/lib1/maven-metadata.xml'.replace('/', File.separator)
        }

        abstract String getMetadataLocation()

        abstract void setup(AbstractIntegrationSpec owner)

        abstract void publishWithDifferentArtifactContent(AbstractIntegrationSpec owner)

        abstract void publishWithDifferentDependencies(AbstractIntegrationSpec owner)

        abstract void publishNewVersion(AbstractIntegrationSpec owner)
    }

    class MavenFileRepo extends FileRepoSetup {
        @Override
        String getDisplayName() {
            return "Maven file repository"
        }

        @Override
        String getProblemDisplayName() {
            return 'maven'
        }

        @Override
        String getMetadataLocation() {
            return 'maven-repo/thing/lib1/2.1/lib1-2.1.pom'.replace('/', File.separator)
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
    }

    class IvyFileRepo extends FileRepoSetup {
        @Override
        String getDisplayName() {
            return "Ivy file repository"
        }

        @Override
        String getProblemDisplayName() {
            return 'ivy'
        }

        @Override
        String getMetadataLocation() {
            return 'ivy-repo/thing/lib1/2.1/ivy-2.1.xml'.replace('/', File.separator)
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

    class MavenLocalRepo extends FileRepoSetup {
        @Override
        String getDisplayName() {
            return "Maven local repository"
        }

        @Override
        String getProblemDisplayName() {
            return 'MavenLocal'
        }

        @Override
        String getMetadataLocation() {
            return 'maven_home/.m2/repository/thing/lib1/2.1/lib1-2.1.pom'.replace('/', File.separator)
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
    }
}
