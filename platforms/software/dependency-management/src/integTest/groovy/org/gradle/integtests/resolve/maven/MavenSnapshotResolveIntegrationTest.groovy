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
package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule
import spock.lang.Issue

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

@RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
class MavenSnapshotResolveIntegrationTest extends AbstractModuleDependencyResolveTest {

    @Override
    String getTestConfiguration() { "compile" }

    def "can resolve unique and non-unique snapshots"() {
        given:
        buildFile << """
dependencies {
    compile "org:unique:1.0-SNAPSHOT"
    compile "org:non-unique:1.0-SNAPSHOT"
}
"""

        and:
        def uniqueVersionModule = publishModule("org", "unique", "1.0-SNAPSHOT")
        def nonUniqueVersionModule = publishModule(mavenHttpRepo, "org", "non-unique", "1.0-SNAPSHOT", false)

        when:
        expectModuleServed(uniqueVersionModule)
        expectModuleServed(nonUniqueVersionModule)

        and:
        run 'checkDeps'

        then:
        resolve.expectDefaultConfiguration("runtime")
        resolve.expectGraph {
            root(":", ":test:") {
                snapshot("org:unique:1.0-SNAPSHOT", uniqueVersionModule.uniqueSnapshotVersion)
                module("org:non-unique:1.0-SNAPSHOT")
            }
        }
    }

    def "can find and cache snapshots in multiple Maven HTTP repositories"() {
        def repo2 = mavenHttpRepo("repo2")

        given:
        buildFile << """
repositories {
    maven { url "${repo2.uri}" }
}

dependencies {
    compile "org.gradle.integtests.resolve:projectA:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:projectB:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:nonunique:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and: "snapshot modules are published"
        def repo1ProjectA = publishModule("org.gradle.integtests.resolve", "projectA", "1.0-SNAPSHOT")
        def repo1ProjectB = createModule("org.gradle.integtests.resolve", "projectB", "1.0-SNAPSHOT")
        def repo2ProjectB = publishModule(repo2, "org.gradle.integtests.resolve", "projectB", "1.0-SNAPSHOT")
        def repo1NonUnique = createModule(mavenHttpRepo, "org.gradle.integtests.resolve", "nonunique", "1.0-SNAPSHOT", false)
        def repo2NonUnique = publishModule(repo2, "org.gradle.integtests.resolve", "nonunique", "1.0-SNAPSHOT", false)

        when: "Server provides projectA from repo1"
        expectModuleServed(repo1ProjectA)

        and: "Server provides projectB from repo2"
        expectModuleMissing(repo1ProjectB)
        expectModuleServed(repo2ProjectB)

        and: "Server provides nonunique snapshot from repo2"
        expectModuleMissing(repo1NonUnique)
        expectModuleServed(repo2NonUnique)

        and: "We resolve dependencies"
        run 'retrieve'

        then: "Snapshots are downloaded"
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar', 'projectB-1.0-SNAPSHOT.jar', 'nonunique-1.0-SNAPSHOT.jar')
        def snapshotA = file('libs/projectA-1.0-SNAPSHOT.jar').snapshot()
        def snapshotNonUnique = file('libs/nonunique-1.0-SNAPSHOT.jar').snapshot()

        when: "We resolve with snapshots cached: no server requests"
        server.resetExpectations()
        def result = run('retrieve')

        then: "Everything is up to date"
        result.assertTaskSkipped(':retrieve')
        file('libs/projectA-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshotA);
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshotNonUnique);
    }

    def "can find and cache snapshots in Maven HTTP repository with additional artifact urls"() {
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        given:
        buildFile << """
repositories.clear() // Do not use default repo
repositories {
    maven {
        url "${repo1.uri}"
        artifactUrls "${repo2.uri}"
    }
}

dependencies {
    compile "org.gradle.integtests.resolve:projectA:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:projectB:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and: "snapshot modules are published"
        def projectA = publishModule(repo1, "org.gradle.integtests.resolve", "projectA", "1.0-SNAPSHOT")
        def repo1ProjectB = publishModule(repo1, "org.gradle.integtests.resolve", "projectB", "1.0-SNAPSHOT")
        def repo2ProjectB = publishModule(repo2, "org.gradle.integtests.resolve", "projectB", "1.0-SNAPSHOT")

        when: "Server provides projectA from repo1"
        expectModuleServed(projectA)

        and: "Server provides projectB with artifact in repo2"
        repo1ProjectB.metaData.expectGet()
        repo1ProjectB.pom.expectGet()
        if (isGradleMetadataPublished()) {
            repo1ProjectB.moduleMetadata.expectGet()
        }
        repo1ProjectB.artifact.expectGetMissing()
        repo2ProjectB.artifact.expectGet()

        and: "We resolve dependencies"
        run 'retrieve'

        then: "Snapshots are downloaded"
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar', 'projectB-1.0-SNAPSHOT.jar')
        def snapshotA = file('libs/projectA-1.0-SNAPSHOT.jar').snapshot()
        def snapshotB = file('libs/projectB-1.0-SNAPSHOT.jar').snapshot()

        when: "We resolve with snapshots cached: no server requests"
        server.resetExpectations()
        def result = run('retrieve')

        then: "Everything is up to date"
        result.assertTaskSkipped(':retrieve')
        file('libs/projectA-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshotA);
        file('libs/projectB-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshotB);
    }

    def "can find and cache snapshots in Maven HTTP repository with artifact classifier"() {
        given:
        buildFile << """
dependencies {
    compile "org.gradle.integtests.resolve:projectA:1.0-SNAPSHOT:tests"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and:
        def projectA = createModule("org.gradle.integtests.resolve", "projectA", "1.0-SNAPSHOT")
        def classifierArtifact = projectA.artifact(classifier: "tests")
        projectA.publish()

        when:
        projectA.metaData.expectGet()
        projectA.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectA.moduleMetadata.expectGet()
        }
        classifierArtifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT-tests.jar')
        def snapshotA = file('libs/projectA-1.0-SNAPSHOT-tests.jar').snapshot()

        when:
        server.resetExpectations()
        run 'retrieve'

        then: "Everything is up to date"
        skipped ':retrieve'
        file('libs/projectA-1.0-SNAPSHOT-tests.jar').assertHasNotChangedSince(snapshotA);
    }

    def "will detect changed snapshot artifacts when pom has not changed"() {
        buildFile << """
configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'

dependencies {
    compile "org.gradle.integtests.resolve:unique:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:nonunique:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when: "snapshot modules are published"
        def uniqueVersionModule = publishModule("org.gradle.integtests.resolve", "unique", "1.0-SNAPSHOT")
        def nonUniqueVersionModule = publishModule(mavenHttpRepo, "org.gradle.integtests.resolve", "nonunique", "1.0-SNAPSHOT", false)

        and: "Server handles requests"
        expectModuleServed(uniqueVersionModule)
        expectModuleServed(nonUniqueVersionModule)

        and: "We resolve dependencies"
        run 'retrieve'

        then: "Snapshots are downloaded"
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar', 'nonunique-1.0-SNAPSHOT.jar')
        def uniqueJarSnapshot = file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).snapshot()
        def nonUniqueJarSnapshot = file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).snapshot()
        server.resetExpectations()

        when: "Change the snapshot artifacts directly: do not change the pom"
        uniqueVersionModule.artifactFile << 'more content'
        uniqueVersionModule.backingModule.sha1File(uniqueVersionModule.artifactFile)
        nonUniqueVersionModule.artifactFile << 'more content'
        nonUniqueVersionModule.backingModule.sha1File(nonUniqueVersionModule.artifactFile)

        and: "No server requests"
        expectChangedArtifactServed(uniqueVersionModule)
        expectChangedArtifactServed(nonUniqueVersionModule)

        and: "Resolve dependencies again"
        run 'retrieve'

        then:
        file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).assertHasChangedSince(uniqueJarSnapshot)
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).assertHasChangedSince(nonUniqueJarSnapshot)
    }

    @Issue("GRADLE-3524")
    def "cacheChangingModulesFor does not apply to extending configurations"() {
        given:
        buildFile << """
configurations {
    testCompile.extendsFrom(compile)
}
configurations.compile {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

dependencies {
    compile "org.gradle.integtests.resolve:unique:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:nonunique:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.testCompile
}
"""

        when:
        def uniqueVersionModule = publishModule("org.gradle.integtests.resolve", "unique", "1.0-SNAPSHOT")
        def nonUniqueVersionModule = publishModule(mavenHttpRepo, "org.gradle.integtests.resolve", "nonunique", "1.0-SNAPSHOT", false)

        and:
        expectModuleServed(uniqueVersionModule)
        expectModuleServed(nonUniqueVersionModule)

        then:
        run 'retrieve'

        when: "Change the snapshot artifacts directly: do not change the pom"
        uniqueVersionModule.artifactFile << 'more content'
        uniqueVersionModule.backingModule.sha1File(uniqueVersionModule.artifactFile)
        nonUniqueVersionModule.artifactFile << 'more content'
        nonUniqueVersionModule.backingModule.sha1File(nonUniqueVersionModule.artifactFile)

        and: "No server requests"
        server.resetExpectations()

        then: "Resolve dependencies again"
        run 'retrieve'
    }

    def "uses cached snapshots from a Maven HTTP repository until the snapshot timeout is reached"() {
        given:
        buildFile << """
if (project.hasProperty('noTimeout')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}

dependencies {
    compile "org.gradle.integtests.resolve:unique:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:nonunique:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when: "snapshot modules are published"
        def uniqueVersionModule = publishModule("org.gradle.integtests.resolve", "unique", "1.0-SNAPSHOT")
        def nonUniqueVersionModule = publishModule(mavenHttpRepo, "org.gradle.integtests.resolve", "nonunique", "1.0-SNAPSHOT", false)

        and: "Server handles requests"
        expectModuleServed(uniqueVersionModule)
        expectModuleServed(nonUniqueVersionModule)

        and: "We resolve dependencies"
        run 'retrieve'

        then: "Snapshots are downloaded"
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar', 'nonunique-1.0-SNAPSHOT.jar')
        def uniqueJarSnapshot = file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).snapshot()
        def nonUniqueJarSnapshot = file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).snapshot()

        when: "Republish the snapshots"
        uniqueVersionModule.publishWithChangedContent()
        nonUniqueVersionModule.publishWithChangedContent()

        and: "No server requests"
        server.resetExpectations()

        and: "Resolve dependencies again, with cached versions"
        run 'retrieve'

        then:
        file('libs/unique-1.0-SNAPSHOT.jar').assertHasNotChangedSince(uniqueJarSnapshot)
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertHasNotChangedSince(nonUniqueJarSnapshot)

        when: "Server handles requests"
        expectChangedModuleServed(uniqueVersionModule)
        expectChangedModuleServed(nonUniqueVersionModule)

        and: "Resolve dependencies with cache expired"
        executer.withArguments("-PnoTimeout")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar', 'nonunique-1.0-SNAPSHOT.jar')
        file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).assertHasChangedSince(uniqueJarSnapshot)
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).assertHasChangedSince(nonUniqueJarSnapshot);
    }

    @Issue("gradle/gradle#3019")
    @ToBeFixedForConfigurationCache
    def "should honour changing module cache expiry for subsequent snapshot resolutions in the same build"() {
        given:
        buildFile << """
configurations {
    fresh
    stale
}
configurations.fresh.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'

dependencies {
    stale "org.gradle.integtests.resolve:unique:1.0-SNAPSHOT"
    fresh "org.gradle.integtests.resolve:unique:1.0-SNAPSHOT"
}

task resolveStaleThenFresh {
    doFirst {
        project.sync {
            from configurations.stale
            into 'stale'
        }
        project.sync {
            from configurations.fresh
            into 'fresh'
        }
    }
}
"""

        when: "snapshot modules are published"
        def snapshotModule = publishModule("org.gradle.integtests.resolve", "unique", "1.0-SNAPSHOT")
        snapshotModule.artifactFile.makeOlder()

        and:
        expectModuleServed(snapshotModule)

        and:
        run 'resolveStaleThenFresh'

        then:
        file('stale').assertHasDescendants('unique-1.0-SNAPSHOT.jar')
        file('fresh').assertHasDescendants('unique-1.0-SNAPSHOT.jar')
        def firstStaleVersion = file('stale/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(snapshotModule.artifactFile).snapshot()
        def firstFreshVersion = file('fresh/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(snapshotModule.artifactFile).snapshot()

        when: "Republish the snapshots"
        server.resetExpectations()
        snapshotModule.publishWithChangedContent()

        // Should get the newer snapshot when resolving 'fresh'
        expectChangedModuleServed(snapshotModule)

        and:
        run 'resolveStaleThenFresh'

        then:
        file('fresh/unique-1.0-SNAPSHOT.jar').assertContentsHaveChangedSince(firstFreshVersion)
        file('fresh/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(snapshotModule.artifactFile)
    }

    def "does not download snapshot artifacts after expiry when snapshot has not changed"() {
        buildFile << """
configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

dependencies {
    compile "org.gradle.integtests.resolve:testproject:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        when: "Publish the first snapshot"
        def module = publishModule("org.gradle.integtests.resolve", "testproject", "1.0-SNAPSHOT")

        and: "Server handles requests"
        expectModuleServed(module)

        and:
        run 'retrieve'

        then:
        file('build').assertHasDescendants('testproject-1.0-SNAPSHOT.jar')
        def snapshot = file('build/testproject-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifactFile).snapshot()

        when: "Server handles requests"
        server.resetExpectations()
        expectChangedProbe(module)

        // Retrieve again with zero timeout should check for updated snapshot
        and:
        def result = run 'retrieve'

        then:
        result.assertTaskSkipped(':retrieve')
        file('build/testproject-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshot);
    }

    def "does not download snapshot artifacts more than once per build"() {
        given:
        def module = publishModule("org.gradle.integtests.resolve", "testproject", "1.0-SNAPSHOT")

        and:
        createDirs("a", "b")
        settingsFile << """
include 'a', 'b'
"""
        buildFile << """
subprojects {
    repositories {
        maven { url "${mavenHttpRepo.uri}" }
    }

    configurations { compile }
}

allprojects {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }

    dependencies {
        compile "org.gradle.integtests.resolve:testproject:1.0-SNAPSHOT"
    }

    task retrieve(type: Sync) {
        into 'build'
        from configurations.compile
    }
}

//imposing an artificial order so that the parallel build retrieves sequentially, GRADLE-2788
retrieve.dependsOn ":a:retrieve"
tasks.getByPath(":a:retrieve").dependsOn ":b:retrieve"
"""
        when: "Module is requested once"
        expectModuleServed(module)

        then:
        run 'retrieve'

        and:
        file('build').assertHasDescendants('testproject-1.0-SNAPSHOT.jar')
        file('a/build').assertHasDescendants('testproject-1.0-SNAPSHOT.jar')
        file('b/build').assertHasDescendants('testproject-1.0-SNAPSHOT.jar')
    }

    def "avoid redownload unchanged artifact when no checksum available"() {
        given:
        buildFile << """
            configurations.all {
                resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
            }

            dependencies {
                compile group: "group", name: "projectA", version: "1.1-SNAPSHOT"
            }

            task retrieve(type: Copy) {
                into 'build'
                from configurations.compile
            }
        """

        and:
        def module = publishModule(mavenHttpRepo, "group", "projectA", "1.1-SNAPSHOT", false)
        // Set the last modified to something that's not going to be anything “else”.
        // There are lots of dates floating around in a resolution and we want to make
        // sure we use this.
        module.artifactFile.setLastModified(2000)
        module.pom.file.setLastModified(6000)
        def artifact = module.artifact

        when:
        expectModuleServed(module)

        run "retrieve"

        then:
        def downloadedJarFile = file("build/projectA-1.1-SNAPSHOT.jar")
        downloadedJarFile.assertIsCopyOf(module.artifactFile)
        def initialDownloadJarFileSnapshot = downloadedJarFile.snapshot()

        when:
        server.resetExpectations()
        expectChangedProbe(module)

        run "retrieve"

        then:
        downloadedJarFile.assertHasNotChangedSince(initialDownloadJarFileSnapshot)

        when:
        module.publishWithChangedContent()
        server.resetExpectations()
        module.metaData.expectGet()
        module.pom.expectHead()
        module.pom.sha1.expectGetMissing()
        module.pom.expectGet()
        if (isGradleMetadataPublished()) {
            module.moduleMetadata.expectHead()
            module.moduleMetadata.sha1.expectGetMissing()
            module.moduleMetadata.expectGet()
        }
        artifact.expectHead()
        artifact.sha1.expectGetMissing()
        artifact.expectGet()

        run "retrieve"

        then:
        downloadedJarFile.assertHasChangedSince(initialDownloadJarFileSnapshot)
        downloadedJarFile.assertIsCopyOf(module.artifactFile)
    }

    @Issue("GRADLE-3017")
    def "resolves changed metadata in snapshot dependency"() {
        given:
        def projectB1 = publishModule('group', 'projectB', '1.0')
        def projectB2 = publishModule('group', 'projectB', '2.0')
        def projectA = createModule('group', 'projectA', "1.0-SNAPSHOT").dependsOn('group', 'projectB', '1.0').publish()

        buildFile << """
configurations {
    compile {
        if (project.hasProperty('bypassCache')) {
            resolutionStrategy.cacheChangingModulesFor(0, "seconds")
        }
    }
}
dependencies {
    compile 'group:projectA:1.0-SNAPSHOT'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        projectA.metaData.expectGet()
        projectA.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectA.moduleMetadata.expectGet()
        }
        projectA.artifact.expectGet()
        projectB1.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectB1.moduleMetadata.expectGet()
        }
        projectB1.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar', 'projectB-1.0.jar')

        when: "Project A is published with changed dependencies"
        server.resetExpectations()
        projectA = projectA.dependsOn('group', 'projectB', '2.0').publish()

        and: "Resolve with caching"
        run 'retrieve'

        then: "Gets original ProjectA metadata from cache"
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar', 'projectB-1.0.jar')

        when: "Resolve without cache"
        projectA.metaData.expectHead()
        projectA.pom.expectHead()
        projectA.pom.sha1.expectGet()
        projectA.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectA.moduleMetadata.expectHead()
            projectA.moduleMetadata.sha1.expectGet()
            projectA.moduleMetadata.expectGet()
        }
        projectA.artifact.expectHead()
        projectB2.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectB2.moduleMetadata.expectGet()
        }
        projectB2.artifact.expectGet()

        and:
        executer.withArguments("-PbypassCache")
        run 'retrieve'

        then: "Gets updated metadata"
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar', 'projectB-2.0.jar')

        when: "Resolve with caching"
        server.resetExpectations()
        run 'retrieve'

        then: "Gets updated metadata from cache"
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar', 'projectB-2.0.jar')
    }

    def "reports and recovers from missing snapshot"() {
        given:
        def projectA = createModule('group', 'projectA', "1.0-SNAPSHOT")

        buildFile << """
dependencies {
    compile 'group:projectA:1.0-SNAPSHOT'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        expectModuleMissing(projectA)

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause("""Could not find group:projectA:1.0-SNAPSHOT.
Searched in the following locations:
  - ${projectA.metaData.uri}
  - ${projectA.pom.uri}
Required by:
""")

        when:
        server.resetExpectations()
        projectA.publish()
        expectModuleServed(projectA)

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar')
    }

    def "reports missing unique snapshot artifact"() {
        given:
        def projectA = publishModule('group', 'projectA', "1.0-SNAPSHOT")

        buildFile << """
dependencies {
    compile 'group:projectA:1.0-SNAPSHOT'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        projectA.metaData.expectGet()
        projectA.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectA.moduleMetadata.expectGet()
        }
        projectA.artifact.expectGetMissing()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause("""Could not find projectA-1.0-SNAPSHOT.jar (group:projectA:1.0-SNAPSHOT:${projectA.uniqueSnapshotVersion}).
Searched in the following locations:
    ${projectA.artifact.uri}""")

        when:
        server.resetExpectations()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause("""Could not find projectA-1.0-SNAPSHOT.jar (group:projectA:1.0-SNAPSHOT:${projectA.uniqueSnapshotVersion}).
Searched in the following locations:
    ${projectA.artifact.uri}""")
    }

    def "reports and recovers from broken maven-metadata.xml"() {
        given:
        def projectA = publishModule('group', 'projectA', "1.0-SNAPSHOT")

        buildFile << """
dependencies {
    compile 'group:projectA:1.0-SNAPSHOT'
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        def metaData = projectA.metaData
        metaData.expectGetBroken()

        then:
        fails 'retrieve'

        and:
        failure.assertHasCause('Could not resolve group:projectA:1.0-SNAPSHOT.')
        failure.assertHasCause("Unable to load Maven meta-data from ${metaData.uri}.")
        failure.assertHasCause("Could not GET '${metaData.uri}'. Received status code 500 from server")

        when:
        server.resetExpectations()
        expectModuleServed(projectA)

        then:
        succeeds 'retrieve'
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar')
    }

    def "can find and cache a unique snapshot in a Maven HTTP repository"() {
        given:
        def published = publishModule("org.gradle.integtests.resolve", "projectA", "1.0-SNAPSHOT")
        buildFile << """
configurations {
    compile {
        resolutionStrategy.cacheChangingModulesFor 0, 'SECONDS'
    }
}

dependencies {
    compile "org.gradle.integtests.resolve:projectA:${published.publishArtifactVersion}"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        published.pom.expectGet()
        if (isGradleMetadataPublished()) {
            published.moduleMetadata.expectGet()
        }
        published.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${published.publishArtifactVersion}.jar")

        when:
        server.resetExpectations()
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${published.publishArtifactVersion}.jar")
    }

    def "can find a unique snapshot in a Maven file repository"() {
        given:
        def fileRepo = maven("fileRepo")
        def projectA = publishModule(fileRepo, "org.gradle.integtests.resolve", "projectA", "1.0-SNAPSHOT")
        buildFile << """
repositories.clear() // Not using default repo
repositories {
    maven {
        url "${fileRepo.uri}"
    }
}

dependencies {
    compile "org.gradle.integtests.resolve:projectA:${projectA.publishArtifactVersion}"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${projectA.publishArtifactVersion}.jar")

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${projectA.publishArtifactVersion}.jar")
    }

    def "snapshot wins over rc"() {
        given:
        def projectA = publishModule("group", "projectA", "1.0-SNAPSHOT")
        publishModule("group", "projectA", "1.0-RC1")

        buildFile << """
dependencies {
    compile "group:projectA:1.0-SNAPSHOT"
    compile "group:projectA:1.0-RC1"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        expectModuleServed(projectA)

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.0-SNAPSHOT.jar")
    }

    def "applies conflict resolution when unique snapshot is referenced by timestamp"() {
        given:
        def projectA = publishModule("group", "projectA", "1.0-SNAPSHOT")
        def timestamp1 = projectA.publishArtifactVersion
        buildFile << """
dependencies {
    compile "group:projectA:${timestamp1}"
    compile "group:projectA:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        projectA.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectA.moduleMetadata.expectGet()
        }
        projectA.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${timestamp1}.jar")

        when:
        projectA.publishWithChangedContent()
        def timestamp2 = projectA.publishArtifactVersion
        buildFile << """
dependencies {
    compile "group:projectA:${timestamp2}"
}
"""
        server.resetExpectations()
        projectA.pom.expectGet()
        if (isGradleMetadataPublished()) {
            projectA.moduleMetadata.expectGet()
        }
        projectA.artifact.expectGet()

        and:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-${timestamp2}.jar")

        when:
        server.resetExpectations()
        def released = publishModule("group", "projectA", "1.0")

        expectModuleServed(released)
        buildFile << """
dependencies {
    compile "group:projectA:1.0"
}
"""
        run 'retrieve'

        then:
        file('libs').assertHasDescendants("projectA-1.0.jar")
    }

    def "reports failure to find a missing unique snapshot in a Maven HTTP repository"() {
        given:
        def projectA = createModule("org.gradle.integtests.resolve", "projectA", "1.0-SNAPSHOT")
        def published = projectA.publish()
        buildFile << """
dependencies {
    compile "org.gradle.integtests.resolve:projectA:${published.publishArtifactVersion}"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when:
        published.missing()

        and:
        fails('retrieve')

        then:
        failure.assertHasCause("""Could not find org.gradle.integtests.resolve:projectA:${published.publishArtifactVersion}.
Searched in the following locations:
  - ${projectA.pom.uri}
Required by:
""")
        failure.assertHasResolutions(repositoryHint("Maven POM"),
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)

    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    def "can resolve unique and non-unique snapshots using Gradle Module Metadata (redirection = #redirection, metadata sources=#metadataSources)"() {
        given:
        buildFile << """
repositories.clear()
repositories {
    maven {
      url "${mavenHttpRepo.uri}"
      metadataSources {
          ${metadataSources.code}
      }
    }
}

dependencies {
    compile "org.gradle.integtests.resolve:unique:1.0-SNAPSHOT"
    compile "org.gradle.integtests.resolve:nonunique:1.0-SNAPSHOT"

    components {
        all(CheckIsChangingRule)
    }
}

class CheckIsChangingRule implements ComponentMetadataRule {
    @Override
    void execute(ComponentMetadataContext context) {
        assert context.details.changing
    }
}

"""

        def usesGradleMetadata = metadataSources == Sources.GRADLE || redirection

        when:
        def uniqueVersionModule = mavenHttpRepo.module("org.gradle.integtests.resolve", "unique", "1.0-SNAPSHOT").withModuleMetadata()
        def nonUniqueVersionModule = mavenHttpRepo.module("org.gradle.integtests.resolve", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().withModuleMetadata()

        if (!redirection) {
            uniqueVersionModule.withoutGradleMetadataRedirection()
            nonUniqueVersionModule.withoutGradleMetadataRedirection()
        }
        uniqueVersionModule.publish()
        nonUniqueVersionModule.publish()

        and:
        expectModuleServed(uniqueVersionModule, metadataSources == Sources.POM, usesGradleMetadata)
        expectModuleServed(nonUniqueVersionModule, metadataSources == Sources.POM, usesGradleMetadata)

        and:
        run 'checkDeps'

        then:
        resolve.expectDefaultConfiguration("runtime")
        resolve.expectGraph {
            root(":", ":test:") {
                snapshot("org.gradle.integtests.resolve:unique:1.0-SNAPSHOT", uniqueVersionModule.uniqueSnapshotVersion)
                module("org.gradle.integtests.resolve:nonunique:1.0-SNAPSHOT")
            }
        }

        where:
        redirection | metadataSources
        true        | Sources.GRADLE
        false       | Sources.GRADLE
        true        | Sources.POM
        false       | Sources.POM
    }

    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def "can resolve files from new variant with unique and non unique snapshots"() {
        given:
        buildFile << """
dependencies.components {
    all(PomRule) {
        params("runtime")
    }
    all(PomRule) {
        params("runtimeElements")
    }
}

dependencies {
    compile("org.gradle.integtests.resolve:unique:1.0-SNAPSHOT")
    compile("org.gradle.integtests.resolve:not-unique:1.0-SNAPSHOT")
}

configurations {
    poms {
        extendsFrom(configurations.compile)
        assert canBeResolved
        canBeConsumed = false
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category, Category.DOCUMENTATION))
            attribute(DocsType.DOCS_TYPE_ATTRIBUTE, getObjects().named(DocsType, "pom"))
        }
    }
}

tasks.register("downloadPoms", Copy) {
    from(configurations.poms)
    into("\$buildDir/poms")
}

abstract class PomRule implements ComponentMetadataRule {

    final String baseVariant

    @Inject
    PomRule(String baseVariant) {
        this.baseVariant = baseVariant
    }

    @Inject
    abstract ObjectFactory getObjects()

    void execute(ComponentMetadataContext context) {
        context.details.maybeAddVariant("pom", baseVariant) {
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category, Category.DOCUMENTATION))
                attribute(DocsType.DOCS_TYPE_ATTRIBUTE, getObjects().named(DocsType, "pom"))
            }
            withFiles {
                removeAllFiles()
                addFile("\${context.details.id.name}-\${context.details.id.version}.pom")
            }
        }
    }
}

"""

        def snapshotModule = mavenHttpRepo.module("org.gradle.integtests.resolve", "unique", "1.0-SNAPSHOT")
        if (isGradleMetadataPublished()) {
            snapshotModule.withModuleMetadata()
        }
        snapshotModule.publish()
        snapshotModule.allowAll()

        def nonUniqueSnapshotModule = mavenHttpRepo.module("org.gradle.integtests.resolve", "not-unique", "1.0-SNAPSHOT").withNonUniqueSnapshots()
        if (isGradleMetadataPublished()) {
            nonUniqueSnapshotModule.withModuleMetadata()
        }
        nonUniqueSnapshotModule.publish()
        nonUniqueSnapshotModule.allowAll()

        when:
        run 'downloadPoms'

        then:
        file('build/poms').assertHasDescendants("unique-1.0-SNAPSHOT.pom", "not-unique-1.0-SNAPSHOT.pom")
    }

    private MavenModule createModule(MavenRepository repository = mavenHttpRepo, String org, String name, String version, boolean uniqueSnapshot = true) {
        def module = repository.module(org, name, version)
        if (isGradleMetadataPublished()) {
            module.withModuleMetadata()
        }
        if (!uniqueSnapshot) {
            module.withNonUniqueSnapshots()
        }
        return module
    }

    private MavenModule publishModule(MavenRepository repository = mavenHttpRepo, String org, String name, String version, boolean uniqueSnapshot = true) {
        return createModule(repository, org, name, version, uniqueSnapshot).publish()
    }

    private expectModuleServed(MavenHttpModule module) {
        if (module.version.endsWith('-SNAPSHOT')) {
            module.metaData.expectGet()
        }
        module.pom.expectGet()
        if (isGradleMetadataPublished()) {
            module.moduleMetadata.expectGet()
        }
        module.artifact.expectGet()
    }

    private expectModuleServed(MavenHttpModule module, boolean pom, boolean gmm) {
        if (module.version.endsWith('-SNAPSHOT')) {
            module.metaData.expectGet()
        }
        if (pom) {
            module.pom.expectGet()
        }
        if (gmm) {
            module.moduleMetadata.expectGet()
        }
        module.artifact.expectGet()
    }

    private expectChangedModuleServed(MavenHttpModule module) {
        if (module.uniqueSnapshots) {
            module.metaData.expectHead()
        }
        module.metaData.expectGet()
        module.pom.expectHead()
        module.pom.sha1.expectGet()
        module.pom.expectGet()
        if (isGradleMetadataPublished()) {
            module.moduleMetadata.expectHead()
            module.moduleMetadata.sha1.expectGet()
            module.moduleMetadata.expectGet()
        }
        module.artifact.expectHead()
        module.artifact.sha1.expectGet()
        module.artifact.expectGet()
    }

    private expectChangedArtifactServed(MavenHttpModule module) {
        if (module.uniqueSnapshots) {
            module.metaData.expectHead()
        } else {
            module.metaData.expectGet()
        }
        module.pom.expectHead()
        if (isGradleMetadataPublished()) {
            module.moduleMetadata.expectHead()
        }
        def artifact = module.artifact
        artifact.expectHead()
        artifact.sha1.expectGet()
        artifact.expectGet()
    }

    private expectChangedProbe(MavenHttpModule module) {
        if (module.uniqueSnapshots) {
            module.metaData.expectHead()
        } else {
            module.metaData.expectGet()
        }
        module.pom.expectHead()
        if (isGradleMetadataPublished()) {
            module.moduleMetadata.expectHead()
        }
        module.artifact.expectHead()
    }

    private expectModuleMissing(MavenHttpModule module) {
        module.metaData.expectGetMissing()
        module.pom.expectGetMissing()
    }

    enum Sources {
        GRADLE("gradleMetadata()"),
        POM("mavenPom()")

        private final String code

        Sources(String code) {
            this.code = code
        }
    }
}
