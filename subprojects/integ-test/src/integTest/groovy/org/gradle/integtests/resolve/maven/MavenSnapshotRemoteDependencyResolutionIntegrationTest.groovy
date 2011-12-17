/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.MavenModule
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.junit.Rule

class MavenSnapshotRemoteDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    
    def "setup"() {
        requireOwnUserHomeDir()
    }

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
        def projectA = repo().module("org.gradle", "projectA", "1.0-SNAPSHOT").publish()
        def projectB = repo().module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()
        def nonUnique = repo().module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots().publish()

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
        // TODO:DAZ
//        result.assertTaskSkipped(':retrieve')
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
        def projectA = repo().module("org.gradle", "projectA", "1.0-SNAPSHOT").publish()
        def projectB = repo().module("org.gradle", "projectB", "1.0-SNAPSHOT").publish()

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
        // TODO:DAZ
//        result.assertTaskSkipped(':retrieve')
        file('libs/projectA-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshotA);
        file('libs/projectB-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshotB);
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
        def uniqueVersionModule = repo().module("org.gradle", "unique", "1.0-SNAPSHOT")
        uniqueVersionModule.publish()
        def nonUniqueVersionModule = repo().module("org.gradle", "nonunique", "1.0-SNAPSHOT").withNonUniqueSnapshots()
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
        waitOneSecondSoThatPublicationDateWillHaveChanged()
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
        expectModuleServed(uniqueVersionModule, '/repo')
        expectModuleServed(nonUniqueVersionModule, '/repo')
        
        and: "Resolve dependencies with cache expired"
        executer.withArguments("-PnoTimeout")
        run 'retrieve'
        
        then:
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar', 'nonunique-1.0-SNAPSHOT.jar')
        file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).assertHasChangedSince(uniqueJarSnapshot)
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).assertHasChangedSince(nonUniqueJarSnapshot);
    }

    private def waitOneSecondSoThatPublicationDateWillHaveChanged() {
        // TODO:DAZ Remove this
        // Ivy checks the publication date to see if it's _really_ changed, won't delete the artifacts if not.
        // So wait a second to ensure the date will be different.
        Thread.sleep(1000)
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
        def module = repo().module("org.gradle", "testproject", "1.0-SNAPSHOT")
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
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)

        // Retrieve again with zero timeout should check for updated snapshot
        and: 
        def result = run 'retrieve'
        
        then:
        // TODO:DAZ
//        result.assertTaskSkipped(':retrieve')
        file('build/testproject-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshot);
    }

    private expectModuleServed(MavenModule module, def prefix) {
        def moduleName = module.artifactId;
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)
        // TODO - should only ask for metadata once
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml", module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)
    }

    private expectModuleMissing(MavenModule module, def prefix) {
        def moduleName = module.artifactId;
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml")
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${moduleName}-1.0-SNAPSHOT.pom")
        // TODO - should only ask for metadata once
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/maven-metadata.xml")
        server.expectGetMissing("${prefix}/org/gradle/${moduleName}/1.0-SNAPSHOT/${moduleName}-1.0-SNAPSHOT.jar")
    }

    MavenRepository repo() {
        return new MavenRepository(file('repo'))
    }
}
