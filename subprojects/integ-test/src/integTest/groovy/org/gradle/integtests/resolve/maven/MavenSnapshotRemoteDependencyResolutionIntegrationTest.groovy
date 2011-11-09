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
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.integtests.fixtures.internal.AbstractIntegrationSpec
import org.junit.Rule

class MavenSnapshotRemoteDependencyResolutionIntegrationTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    
    def "setup"() {
        requireOwnUserHomeDir()
    }

    def "can search for snapshot in multiple Maven HTTP repositories"() {
        server.start()

        given:
        buildFile << """
repositories {
    mavenRepo(url: "http://localhost:${server.port}/repo1")
    mavenRepo(url: "http://localhost:${server.port}/repo2")
}

configurations { compile }

dependencies {
    compile "org.gradle:projectA:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'libs'
    from configurations.compile
}
"""

        and: "snapshot modules are published"
        def projectA = repo().module("org.gradle", "projectA", "1.0-SNAPSHOT")
        projectA.publish()

        when: "Server handles requests"
        server.expectGetMissing('/repo1/org/gradle/projectA/1.0-SNAPSHOT/maven-metadata.xml')
        server.expectGetMissing('/repo1/org/gradle/projectA/1.0-SNAPSHOT/projectA-1.0-SNAPSHOT.pom')
        // TODO Should not look for jar in repo1
        server.expectGetMissing('/repo1/org/gradle/projectA/1.0-SNAPSHOT/maven-metadata.xml')
        server.expectGetMissing("/repo1/org/gradle/projectA/1.0-SNAPSHOT/projectA-1.0-SNAPSHOT.jar")

        server.expectGet('/repo2/org/gradle/projectA/1.0-SNAPSHOT/maven-metadata.xml', projectA.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo2/org/gradle/projectA/1.0-SNAPSHOT/${projectA.pomFile.name}", projectA.pomFile)
        // TODO - should only ask for metadata once
        server.expectGet('/repo2/org/gradle/projectA/1.0-SNAPSHOT/maven-metadata.xml', projectA.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo2/org/gradle/projectA/1.0-SNAPSHOT/${projectA.artifactFile.name}", projectA.artifactFile)

        and: "We resolve dependencies"
        run 'retrieve'

        then: "Snapshots is downloaded"
        file('libs').assertHasDescendants('projectA-1.0-SNAPSHOT.jar')
    }

    def "uses cached snapshots from a Maven HTTP repository until the snapshot timeout is reached"() {
        server.start()

        given:
        buildFile << """
repositories {
    mavenRepo(url: "http://localhost:${server.port}/repo")
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
        server.expectGet('/repo/org/gradle/unique/1.0-SNAPSHOT/maven-metadata.xml', uniqueVersionModule.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/unique/1.0-SNAPSHOT/${uniqueVersionModule.pomFile.name}", uniqueVersionModule.pomFile)
        server.expectGetMissing('/repo/org/gradle/nonunique/1.0-SNAPSHOT/maven-metadata.xml')
        server.expectGet("/repo/org/gradle/nonunique/1.0-SNAPSHOT/nonunique-1.0-SNAPSHOT.pom", nonUniqueVersionModule.pomFile)
        
        // TODO - should only ask for metadata once
        server.expectGet('/repo/org/gradle/unique/1.0-SNAPSHOT/maven-metadata.xml', uniqueVersionModule.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/unique/1.0-SNAPSHOT/${uniqueVersionModule.artifactFile.name}", uniqueVersionModule.artifactFile)
        // TODO - should only ask for metadata once
        server.expectGetMissing('/repo/org/gradle/nonunique/1.0-SNAPSHOT/maven-metadata.xml')
        server.expectGet("/repo/org/gradle/nonunique/1.0-SNAPSHOT/nonunique-1.0-SNAPSHOT.jar", nonUniqueVersionModule.artifactFile)

        and: "We resolve dependencies"
        run 'retrieve'

        then: "Snapshots are downloaded"
        file('libs').assertHasDescendants('unique-1.0-SNAPSHOT.jar', 'nonunique-1.0-SNAPSHOT.jar')
        def uniqueJarSnapshot = file('libs/unique-1.0-SNAPSHOT.jar').assertIsCopyOf(uniqueVersionModule.artifactFile).snapshot()
        def nonUniqueJarSnapshot = file('libs/nonunique-1.0-SNAPSHOT.jar').assertIsCopyOf(nonUniqueVersionModule.artifactFile).snapshot()

        when: "Republish the snapshots"
        uniqueVersionModule.publishWithChangedContent()
        nonUniqueVersionModule.publishWithChangedContent()
        waitOneSecondSoThatPublicationDateWillHaveChanged()

        and: "No server requests"
        server.resetExpectations()

        and: "Resolve dependencies again, with cached versions"
        run 'retrieve'

        then:
        file('libs/unique-1.0-SNAPSHOT.jar').assertHasNotChangedSince(uniqueJarSnapshot)
        file('libs/nonunique-1.0-SNAPSHOT.jar').assertHasNotChangedSince(nonUniqueJarSnapshot)
        
        when: "Server handles requests"
        server.expectGet('/repo/org/gradle/unique/1.0-SNAPSHOT/maven-metadata.xml', uniqueVersionModule.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/unique/1.0-SNAPSHOT/${uniqueVersionModule.pomFile.name}", uniqueVersionModule.pomFile)
        server.expectGetMissing('/repo/org/gradle/nonunique/1.0-SNAPSHOT/maven-metadata.xml')
        server.expectGet("/repo/org/gradle/nonunique/1.0-SNAPSHOT/nonunique-1.0-SNAPSHOT.pom", nonUniqueVersionModule.pomFile)
        
        // TODO - should only ask for metadata once
        server.expectGet('/repo/org/gradle/unique/1.0-SNAPSHOT/maven-metadata.xml', uniqueVersionModule.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/unique/1.0-SNAPSHOT/${uniqueVersionModule.artifactFile.name}", uniqueVersionModule.artifactFile)
        // TODO - should only ask for metadata once
        server.expectGetMissing('/repo/org/gradle/nonunique/1.0-SNAPSHOT/maven-metadata.xml')
        server.expectGet("/repo/org/gradle/nonunique/1.0-SNAPSHOT/nonunique-1.0-SNAPSHOT.jar", nonUniqueVersionModule.artifactFile)
        
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
    mavenRepo(url: "http://localhost:${server.port}/repo")
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
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', module.moduleDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${module.artifactFile.name}", module.artifactFile)

        // TODO - should only ask for metadata once
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', module.moduleDir.file("maven-metadata.xml"))

        and:
        run 'retrieve'
        
        then:
        file('build').assertHasDescendants('testproject-1.0-SNAPSHOT.jar')
        def snapshot = file('build/testproject-1.0-SNAPSHOT.jar').assertIsCopyOf(module.artifactFile).snapshot()

        when: "Server handles requests"
        server.resetExpectations()
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', module.moduleDir.file("maven-metadata.xml"))

        // TODO - this should not be here?
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${module.pomFile.name}", module.pomFile)

        // Retrieve again with zero timeout should check for updated snapshot
        and: 
        def result = run 'retrieve'
        
        then:
        result.assertTaskSkipped(':retrieve')
        file('build/testproject-1.0-SNAPSHOT.jar').assertHasNotChangedSince(snapshot);
    }

    MavenRepository repo() {
        return new MavenRepository(file('repo'))
    }
}
