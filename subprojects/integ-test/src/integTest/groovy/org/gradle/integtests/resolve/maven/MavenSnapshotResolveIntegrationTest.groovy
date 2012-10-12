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

import org.gradle.integtests.fixtures.MavenModule
import org.gradle.integtests.resolve.AbstractDependencyResolutionTest

class MavenSnapshotResolveIntegrationTest extends AbstractDependencyResolutionTest {

    def "can find and cache snapshots in multiple Maven HTTP repositories"() {
        server.start()

        given:
        buildFile << """
repositories {
    maven { url "http://localhost:${server.port}/repo1" }
    maven { url "http://localhost:${server.port}/repo2" }
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
        def projectA = mavenRepo().module("org.gradle", "projectA", "1.0-SNAPSHOT").publish()
        def projectB = mavenRepo().module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()
        def nonUnique = mavenRepo().module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()

        when: "Server provides projectA from repo1"
        expectModuleServed(projectA, '/repo1')

        and: "Server provides projectB from repo2"
        expectModuleMissing(projectB, '/repo1')
        expectModuleServed(projectB, '/repo2')

        and: "Server provides nonunique snapshot from repo2"
        expectModuleMissing(nonUnique, '/repo1')
        expectModuleServed(nonUnique, '/repo2')

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

        given:
        buildFile << """
repositories {
    maven {
        url "http://localhost:${server.port}/repo1"
        artifactUrls "http://localhost:${server.port}/repo2"
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
        def projectA = mavenRepo().module("org.gradle", "projectA", "1.0-SNAPSHOT").publish()
        def projectB = mavenRepo().module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()

        when: "Server provides projectA from repo1"
        expectModuleServed(projectA, '/repo1')

        and: "Server provides projectB with artifact in repo2"
        server.expectGet("/repo1/org/gradle/projectB/1.0-SNAPSHOT/maven-metadata.xml", projectB.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo1/org/gradle/projectB/1.0-SNAPSHOT/${projectB.pomFile.name}", projectB.pomFile)
        server.expectGet("/repo1/org/gradle/projectB/1.0-SNAPSHOT/maven-metadata.xml", projectB.moduleDir.file("maven-metadata.xml"))
        server.expectGetMissing("/repo1/org/gradle/projectB/1.0-SNAPSHOT/${projectB.artifactFile.name}")
        server.expectGetMissing("/repo1/org/gradle/projectB/1.0-SNAPSHOT/projectB-1.0-SNAPSHOT.jar")

        // TODO: This is not correct - should be looking for jar with unique version to fetch snapshot
        server.expectGet("/repo2/org/gradle/projectB/1.0-SNAPSHOT/projectB-1.0-SNAPSHOT.jar", projectB.artifactFile)
//        server.expectGet("/repo2/org/gradle/projectB/1.0-SNAPSHOT/${projectB.artifactFile.name}",  projectB.artifactFile)

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
    maven { url "http://localhost:${server.port}/repo" }
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
        def uniqueVersionModule = mavenRepo().module("org.gradle", "unique", "1.0-SNAPSHOT").publish()
        def nonUniqueVersionModule = mavenRepo().module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()

        and: "Server handles requests"
        expectModuleServed(uniqueVersionModule, '/repo', false, false)
        expectModuleServed(nonUniqueVersionModule, '/repo', false, false)

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
        expectChangedArtifactServed(uniqueVersionModule, '/repo')
        expectChangedArtifactServed(nonUniqueVersionModule, '/repo')

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
    maven { url "http://localhost:${server.port}/repo" }
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
        def uniqueVersionModule = mavenRepo().module("org.gradle", "unique", "1.0-SNAPSHOT")
        uniqueVersionModule.publish()
        def nonUniqueVersionModule = mavenRepo().module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots()
        nonUniqueVersionModule.publish()

        and: "Server handles requests"
        expectModuleServed(uniqueVersionModule, '/repo')
        expectModuleServed(nonUniqueVersionModule, '/repo')

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
        expectModuleServed(uniqueVersionModule, '/repo', true, true)
        expectModuleServed(nonUniqueVersionModule, '/repo', true, true)

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
    maven { url "http://localhost:${server.port}/repo" }
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
        def module = mavenRepo().module("org.gradle", "testproject", "1.0-SNAPSHOT")
        module.publish()

        and: "Server handles requests"
        expectModuleServed(module, '/repo')

        and:
        run 'retrieve'

        then:
        file('build').assertHasDescendants('testproject-1.0-SNAPSHOT.jar')
        def snapshot = file('build/testproject-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifactFile).snapshot()

        when: "Server handles requests"
        server.resetExpectations()
        expectChangedProbe('/repo', module, false)

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
        def module = mavenRepo().module("org.gradle", "testproject", "1.0-SNAPSHOT")
        module.publish()

        and:
        settingsFile << "include 'a', 'b'"
        buildFile << """
allprojects {
    repositories {
        maven { url "http://localhost:${server.port}/repo" }
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
        expectModuleServed(module, '/repo')

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
        def module = mavenRepo().module("org.gradle", "testproject", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        def module2 = maven('repo2').module("org.gradle", "testproject", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()
        module2.artifactFile << module2.artifactFile.bytes // ensure it's a different length to the first one
        module2.pomFile << '    ' // ensure it's a different length to the first one

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
        resolutionStrategy.resolutionRules.eachModule({ module ->
            module.refresh()
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
        def moduleName = module.artifactId
        def prefix = "/repo"
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)

        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module2.moduleDir.file("maven-metadata.xml"))
        server.expectHead("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module2.pomFile)
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}.sha1", module2.sha1File(module2.pomFile))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module2.pomFile)
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module2.moduleDir.file("maven-metadata.xml"))
        server.expectHead("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module2.artifactFile)
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}.sha1", module2.sha1File(module2.artifactFile))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module2.artifactFile)

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
                maven { url "http://localhost:${server.port}/repo" }
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
        def module = mavenRepo().module("group", "projectA", "1.1-SNAPSHOT").withNonUniqueSnapshots().publish()
        // Set the last modified to something that's not going to be anything “else”.
        // There are lots of dates floating around in a resolution and we want to make
        // sure we use this.
        module.artifactFile.setLastModified(2000)
        module.pomFile.setLastModified(6000)

        def base = "/repo/group/projectA/1.1-SNAPSHOT"
        def metaDataPath = "$base/maven-metadata.xml"
        def pomPath = "$base/$module.pomFile.name"
        def pomSha1Path = "${pomPath}.sha1"
        def originalPomLastMod = module.pomFile.lastModified()
        def originalPomContentLength = module.pomFile.length()
        def jarPath = "$base/$module.artifactFile.name"
        def jarSha1Path = "${jarPath}.sha1"
        def originalJarLastMod = module.artifactFile.lastModified()
        def originalJarContentLength = module.artifactFile.length()

        when:
        server.expectGet(metaDataPath, module.metaDataFile)
        server.expectGet(pomPath, module.pomFile)
        server.expectGet(metaDataPath, module.metaDataFile)
        server.expectGet(jarPath, module.artifactFile)

        run "retrieve"

        then:
        def downloadedJarFile = file("build/projectA-1.1-SNAPSHOT.jar")
        downloadedJarFile.assertIsCopyOf(module.artifactFile)
        def initialDownloadJarFileSnapshot = downloadedJarFile.snapshot()

        // Do change the jar, so we can check that the new version wasn't downloaded
        module.publishWithChangedContent()

        when:
        server.resetExpectations()
        server.expectGet(metaDataPath, module.metaDataFile)
        server.expectHead(pomPath, module.pomFile, originalPomLastMod, originalPomContentLength)
        server.expectGet(metaDataPath, module.metaDataFile)
        server.expectHead(jarPath, module.artifactFile, originalJarLastMod, originalJarContentLength)

        run "retrieve"

        then:
        downloadedJarFile.assertHasNotChangedSince(initialDownloadJarFileSnapshot)

        when:
        server.resetExpectations()
        server.expectGet(metaDataPath, module.metaDataFile)
        server.expectGetMissing(pomSha1Path)
        server.expectHead(pomPath, module.pomFile)
        server.expectGet(pomPath, module.pomFile)
        server.expectGet(metaDataPath, module.metaDataFile)
        server.expectGetMissing(jarSha1Path)
        server.expectHead(jarPath, module.artifactFile)
        server.expectGet(jarPath, module.artifactFile)


        run "retrieve"

        then:
        downloadedJarFile.assertHasChangedSince(initialDownloadJarFileSnapshot)
        downloadedJarFile.assertIsCopyOf(module.artifactFile)
    }

    private expectModuleServed(MavenModule module, def prefix, boolean sha1requests = false, boolean headRequests = false) {
        def moduleName = module.artifactId;
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)
        // TODO - should only ask for metadata once
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)

        if (sha1requests) {
            server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}.sha1", module.sha1File(module.pomFile))
            server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}.sha1", module.sha1File(module.artifactFile))
        }

        if (headRequests) {
            server.expectHead("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)
            server.expectHead("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)
        }
    }

    private expectChangedArtifactServed(MavenModule module, def prefix, boolean sha1requests = false, boolean headRequests = false) {
        def moduleName = module.artifactId;
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectHead("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)

        // TODO - should only ask for metadata once
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))

        server.expectHead("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}.sha1", module.sha1File(module.artifactFile))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)
    }

    private expectChangedProbe(prefix, MavenModule module, boolean expectSha1) {
        module.expectMetaDataGet(server, prefix)
        module.expectPomHead(server, prefix)
        if (expectSha1) {
            module.expectPomSha1Get(server, prefix)
        }

        module.expectMetaDataGet(server, prefix)
        module.expectArtifactHead(server, prefix)
        if (expectSha1) {
            module.expectArtifactSha1Get(server, prefix)
        }
    }
    
    private expectModuleMissing(MavenModule module, def prefix) {
        def moduleName = module.artifactId;
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml")
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${moduleName}-1.0-SNAPSHOT.pom")
        // TODO - should only ask for metadata once
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml")
        server.expectHeadMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${moduleName}-1.0-SNAPSHOT.jar")
    }


}
