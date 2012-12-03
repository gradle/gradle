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

import org.gradle.integtests.resolve.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.maven.MavenHttpModule

class MavenSnapshotResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "can find and cache snapshots in multiple Maven HTTP repositories"() {
        server.start()
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        given:
        buildFile << """
repositories {
    maven { url "${repo1.uri}" }
    maven { url "${repo2.uri}" }
}

configurations { compile }

dependencies {
    compile "org.gradle:projectA:1.0-SNAPSHOT"
    compile "org.gradle:projectB:1.0-SNAPSHOT"
    compile "org.gradle:nonunique:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and: "snapshot modules are published"
        def repo1ProjectA = repo1.module("org.gradle", "projectA", "1.0-SNAPSHOT").publish()
        def repo1ProjectB = repo1.module("org.gradle", "projectB", "1.0-SNAPSHOT")
        def repo2ProjectB = repo2.module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()
        def repo1NonUnique = repo1.module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots()
        def repo2NonUnique = repo2.module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()

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
        server.start()
        def repo1 = mavenHttpRepo("repo1")
        def repo2 = mavenHttpRepo("repo2")

        given:
        buildFile << """
repositories {
    maven {
        url "${repo1.uri}"
        artifactUrls "${repo2.uri}"
    }
}

configurations { compile }

dependencies {
    compile "org.gradle:projectA:1.0-SNAPSHOT"
    compile "org.gradle:projectB:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and: "snapshot modules are published"
        def projectA = repo1.module("org.gradle", "projectA", "1.0-SNAPSHOT").publish()
        def repo1ProjectB = repo1.module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()
        def repo2ProjectB = repo2.module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()

        when: "Server provides projectA from repo1"
        expectModuleServed(projectA)

        and: "Server provides projectB with artifact in repo2"
        repo1ProjectB.expectMetaDataGet()
        repo1ProjectB.expectPomGet()
        // TODO - should fetch this once
        repo1ProjectB.expectMetaDataGet()

        server.expectGetMissing("/repo1/org/gradle/projectB/1.0-SNAPSHOT/${repo1ProjectB.artifactFile.name}")
        server.expectGetMissing("/repo1/org/gradle/projectB/1.0-SNAPSHOT/projectB-1.0-SNAPSHOT.jar")

        // TODO: This is not correct - should be looking for jar with unique version to fetch snapshot
        server.expectGet("/repo2/org/gradle/projectB/1.0-SNAPSHOT/projectB-1.0-SNAPSHOT.jar", repo2ProjectB.artifactFile)
//        server.expectGet("/repo2/org/gradle/projectB/1.0-SNAPSHOT/${repo2ProjectB.artifactFile.name}", repo2ProjectB.artifactFile)

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

    def "will detect changed snapshot artifacts when pom has not changed"() {
        server.start()

        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}

configurations { compile }
configurations.compile.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'

dependencies { 
    compile "org.gradle:unique:1.0-SNAPSHOT" 
    compile "org.gradle:nonunique:1.0-SNAPSHOT" 
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when: "snapshot modules are published"
        def uniqueVersionModule = mavenHttpRepo.module("org.gradle", "unique", "1.0-SNAPSHOT").publish()
        def nonUniqueVersionModule = mavenHttpRepo.module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()

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
        nonUniqueVersionModule.artifactFile << 'more content'

        and: "No server requests"
        expectChangedArtifactServed(uniqueVersionModule)
        expectChangedArtifactServed(nonUniqueVersionModule)

        and: "Resolve dependencies again"
        run 'retrieve'

        then:
        file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).assertHasChangedSince(uniqueJarSnapshot)
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).assertHasChangedSince(nonUniqueJarSnapshot)
    }

    def "uses cached snapshots from a Maven HTTP repository until the snapshot timeout is reached"() {
        server.start()

        given:
        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}

configurations { compile }

if (project.hasProperty('noTimeout')) {
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }
}

dependencies {
    compile "org.gradle:unique:1.0-SNAPSHOT"
    compile "org.gradle:nonunique:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        when: "snapshot modules are published"
        def uniqueVersionModule = mavenHttpRepo.module("org.gradle", "unique", "1.0-SNAPSHOT").publish()
        def nonUniqueVersionModule = mavenHttpRepo.module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()

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

    def "does not download snapshot artifacts after expiry when snapshot has not changed"() {
        server.start()

        buildFile << """
repositories {
    maven { url "${mavenHttpRepo.uri}" }
}

configurations { compile }

configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

dependencies {
    compile "org.gradle:testproject:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        when: "Publish the first snapshot"
        def module = mavenHttpRepo.module("org.gradle", "testproject", "1.0-SNAPSHOT").publish()

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
        server.start()
        given:
        def module = mavenHttpRepo.module("org.gradle", "testproject", "1.0-SNAPSHOT").publish()

        and:
        settingsFile << "include 'a', 'b'"
        buildFile << """
allprojects {
    repositories {
        maven { url "${mavenHttpRepo.uri}" }
    }

    configurations { compile }

    configurations.all {
        resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
    }

    dependencies {
        compile "org.gradle:testproject:1.0-SNAPSHOT"
    }

    task retrieve(type: Sync) {
        into 'build'
        from configurations.compile
    }
}
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

    def "can update snapshot artifact during build even if it is locked earlier in build"() {
        server.start()
        given:
        def module = mavenHttpRepo("/repo", maven("repo1")).module("org.gradle", "testproject", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        def module2 = mavenHttpRepo("/repo", maven("repo2")).module("org.gradle", "testproject", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        module2.pomFile << '    ' // ensure it's a different length to the first one
        def module2Artifact = module2.artifactFile
        module2.artifactFile << module2.artifactFile.bytes // ensure it's a different length to the first one

        and:
        settingsFile << "include 'first', 'second'"
        buildFile << """
def fileLocks = [:]
subprojects {
    repositories {
        maven { url "http://localhost:${server.port}/repo" }
    }

    configurations { compile }

    configurations.all {
        resolutionStrategy.resolutionRules.eachArtifact({ artifact ->
            artifact.refresh()
        } as Action)
    }

    dependencies {
        compile "org.gradle:testproject:1.0-SNAPSHOT"
    }

    task lock << {
        configurations.compile.each { file ->
            println "locking " + file
            def lockFile = new RandomAccessFile(file.canonicalPath, 'r')
            fileLocks[file] = lockFile
        }
    }

    task retrieve(type: Sync) {
        into 'build'
        from configurations.compile
    }
    retrieve.dependsOn 'lock'
}
project('second') {
    lock.dependsOn ':first:lock'
    retrieve.dependsOn ':first:retrieve'

    task cleanup << {
        fileLocks.each { key, value ->
            println "unlocking " + key
            value.close()
        }
    }
    cleanup.dependsOn 'retrieve'
}
"""
        when: "Module is requested once"
        module.expectMetaDataGet()
        module.expectPomGet()
        module.expectMetaDataGet()
        module.artifact.expectGet()

        module2.expectMetaDataGet()
        module2Artifact.expectHead()
        module2Artifact.expectSha1Get()
        module2Artifact.expectGet()

        then:
        run 'cleanup'

        and:
        file('first/build/testproject-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifactFile)
        file('second/build/testproject-1.0-SNAPSHOT.jar').assertIsCopyOf(module2.artifactFile)
    }

    def "avoid redownload unchanged artifact when no checksum available"() {
        server.start()

        given:
        buildFile << """
            repositories {
                maven { url "${mavenHttpRepo.uri}" }
            }

            configurations { compile }

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
        def module = mavenHttpRepo.module("group", "projectA", "1.1-SNAPSHOT").withNonUniqueSnapshots().publish()
        // Set the last modified to something that's not going to be anything “else”.
        // There are lots of dates floating around in a resolution and we want to make
        // sure we use this.
        module.artifactFile.setLastModified(2000)
        module.pomFile.setLastModified(6000)

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
        module.expectMetaDataGet()
        module.expectPomHead()
        module.expectPomSha1GetMissing()
        module.expectPomGet()
        // TODO - should only ask for metadata once
        module.expectMetaDataGet()
        artifact.expectHead()
        artifact.expectSha1GetMissing()
        artifact.expectGet()

        run "retrieve"

        then:
        downloadedJarFile.assertHasChangedSince(initialDownloadJarFileSnapshot)
        downloadedJarFile.assertIsCopyOf(module.artifactFile)
    }

    private expectModuleServed(MavenHttpModule module) {
        module.expectMetaDataGet()
        module.expectPomGet()
        // TODO - should only ask for metadata once
        module.expectMetaDataGet()
        module.artifact.expectGet()
    }

    private expectChangedModuleServed(MavenHttpModule module) {
        module.expectMetaDataGet()
        module.expectPomHead()
        module.expectPomSha1Get()
        module.expectPomGet()
        // TODO - should only ask for metadata once
        module.expectMetaDataGet()
        def artifact = module.artifact
        artifact.expectHead()
        artifact.expectSha1Get()
        artifact.expectGet()
    }

    private expectChangedArtifactServed(MavenHttpModule module) {
        module.expectMetaDataGet()
        module.expectPomHead()
        // TODO - should only ask for metadata once
        module.expectMetaDataGet()
        def artifact = module.artifact
        artifact.expectHead()
        artifact.expectSha1Get()
        artifact.expectGet()
    }

    private expectChangedProbe(MavenHttpModule module) {
        module.expectMetaDataGet()
        module.expectPomHead()
        // TODO - should only ask for metadata once
        module.expectMetaDataGet()
        module.artifact.expectHead()
    }

    private expectModuleMissing(MavenHttpModule module) {
        module.expectMetaDataGetMissing()
        module.expectPomGetMissing()
        // TODO - should only ask for metadata once
        module.expectMetaDataGetMissing()
        module.artifact.expectHeadMissing()
    }
}
