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

import org.gradle.util.TestFile
import org.junit.Test
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.MavenRepository

class MavenRemoteDependencyResolutionIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()
    @Rule public final HttpServer server = new HttpServer()

    @Test
    public void canResolveDependenciesFromMultipleMavenRepositories() {
        List expectedFiles = ['sillyexceptions-1.0.1.jar', 'repotest-1.0.jar', 'testdep-1.0.jar', 'testdep2-1.0.jar',
                'classifier-1.0-jdk15.jar', 'classifier-dep-1.0.jar', 'jaronly-1.0.jar']

        File projectDir = dist.testDir
        executer.inDirectory(projectDir).withTasks('retrieve').run()
        expectedFiles.each { new TestFile(projectDir, 'build', it).assertExists() }
    }

    @Test
    public void "uses cached snapshot resolved from a Maven HTTP repository until the snapshot timeout is reached"() {
        dist.requireOwnUserHomeDir()
        server.start()

        dist.testFile('build.gradle') << """
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
    compile "org.gradle:testproject:1.0-SNAPSHOT"
}

task retrieve(type: Sync) {
    into 'build'
    from configurations.compile
}
"""

        // Publish the first snapshot
        def module = repo().module("org.gradle", "testproject", "1.0-SNAPSHOT")
        module.publish()

        // Retrieve the first snapshot
        def repoDir = dist.testFile('repo/org/gradle/testproject/1.0-SNAPSHOT')
        repoDir.assertIsDir()
        def pom = repoDir.listFiles().find { it.name.matches('.*-1.pom') }
        def jar = repoDir.listFiles().find { it.name.matches('.*-1.jar') }
        assert pom && jar
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        // TODO - should only ask for metadata once
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))

        executer.withTasks('retrieve').run()
        def jarFile = dist.testFile('build/testproject-1.0-SNAPSHOT.jar')

        jarFile.assertIsCopyOf(module.artifactFile)
        def snapshot = jarFile.snapshot()

        // Publish the second snapshot
        module.publishWithChangedContent()
        waitOneSecondSoThatPublicationDateWillHaveChanged()

        server.resetExpectations()

        // Retrieve again should use cached snapshot, and should not hit the server
        executer.withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)

        // Retrieve again with zero timeout should download and use updated snapshot

        pom = repoDir.listFiles().find { it.name.matches('.*-2.pom') }
        jar = repoDir.listFiles().find { it.name.matches('.*-2.jar') }
        assert pom && jar
        server.resetExpectations()
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        // TODO - should only ask for metadata once
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))

        executer.withTasks('retrieve').withArguments("-PnoTimeout").run().assertTasksNotSkipped(':retrieve')
        jarFile.assertIsCopyOf(module.artifactFile)
    }

    private def waitOneSecondSoThatPublicationDateWillHaveChanged() {
        // TODO:DAZ Remove this
        // Ivy checks the publication date to see if it's _really_ changed, won't delete the artifacts if not.
        // So wait a second to ensure the date will be different.
        Thread.sleep(1000)
    }

    @Test
    public void "does not download snapshot artifacts after expiry when snapshot has not changed"() {
        dist.requireOwnUserHomeDir()
        server.start()

        dist.testFile('build.gradle') << """
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

        // Publish the first snapshot
        def module = repo().module("org.gradle", "testproject", "1.0-SNAPSHOT")
        module.publish()

        // Retrieve the first snapshot
        def repoDir = module.moduleDir
        repoDir.assertIsDir()
        def pom = repoDir.listFiles().find { it.name.matches('.*-1.pom') }
        def jar = repoDir.listFiles().find { it.name.matches('.*-1.jar') }
        assert pom && jar
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        // TODO - should only ask for metadata once
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))

        executer.withTasks('retrieve').run()
        def jarFile = dist.testFile('build/testproject-1.0-SNAPSHOT.jar')
        jarFile.assertIsCopyOf(module.artifactFile)
        def snapshot = jarFile.assertIsFile().snapshot()

        // Retrieve again with zero timeout should check for updated snapshot
        server.resetExpectations()
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))

        // TODO - this should not be here?
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)

        executer.withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)
    }

    @Test
    public void "can resolve and cache dependencies from HTTP Maven repository"() {
        dist.requireOwnUserHomeDir()

        def projectA = repo().module('group', 'projectA')
        projectA.publish()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        server.start()

        dist.testFile('build.gradle') << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
}
"""

        executer.withTasks('listJars').run()

        server.resetExpectations()
        // No server calls for cached dependencies
        executer.withTasks('listJars').run()
    }

    @Test
    public void "does not download source and javadoc artifacts from HTTP Maven repository until required"() {
        dist.requireOwnUserHomeDir()

        def projectA = repo().module('group', 'projectA', '1.0')
        projectA.artifact(classifier: 'sources')
        projectA.artifact(classifier: 'javadoc')
        projectA.publish()
        def sourceJar = projectA.artifactFile(classifier: 'sources')
        def javadocJar = projectA.artifactFile(classifier: 'javadoc')

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        server.start()

        dist.testFile('build.gradle') << """
apply plugin: 'java'
apply plugin: 'eclipse'
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
}
dependencies {
    compile 'group:projectA:1.0'
}
eclipse { classpath { downloadJavadoc = true } }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar']
}
"""

        executer.withTasks('listJars').run()

        server.resetExpectations()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-sources.jar', sourceJar)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar', javadocJar)

        executer.withTasks('eclipseClasspath').run()
    }

    @Test
    public void "can resolve and cache dependencies from multiple HTTP Maven repositories"() {
        dist.requireOwnUserHomeDir()

        def projectA = repo().module('group', 'projectA')
        def projectB = repo().module('group', 'projectB')
        projectA.publish()
        projectB.publish()

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)

        // Looks for POM and JAR in repo1 before looking in repo2 (jar is an attempt to handle publication without module descriptor)
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.pom')
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        server.start()

        dist.testFile('build.gradle') << """
repositories {
    maven { url 'http://localhost:${server.port}/repo1' }
    maven { url 'http://localhost:${server.port}/repo2' }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar']
}
"""

        executer.withTasks('listJars').run()

        server.resetExpectations()
        // No server requests when all jars cached

        executer.withTasks('listJars').run()
    }

    @Test
    public void "uses artifactsUrl to resolve artifacts"() {
        dist.requireOwnUserHomeDir()

        def projectA = repo().module('group', 'projectA')
        def projectB = repo().module('group', 'projectB')
        projectA.publish()
        projectB.publish()

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)

        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        server.start()

        dist.testFile('build.gradle') << """
repositories {
    maven {
        url 'http://localhost:${server.port}/repo1'
        artifactUrls 'http://localhost:${server.port}/repo2'
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.0', 'group:projectB:1.0'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.0.jar', 'projectB-1.0.jar']
}
"""

        executer.withTasks('listJars').run()
    }

    @Test
    public void "can resolve dependencies from password protected HTTP Maven repository"() {
        dist.requireOwnUserHomeDir()

        def module = repo().module('group', 'projectA', '1.2')
        module.publish()

        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.pom', 'username', 'password', module.pomFile)
        server.expectGet('/repo/group/projectA/1.2/projectA-1.2.jar', 'username', 'password', module.artifactFile)
        server.start()

        dist.testFile('build.gradle') << """
repositories {
    maven {
        userName 'username'
        password 'password'
        url "http://localhost:${server.port}/repo"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""

        executer.withTasks('listJars').run()
    }

    MavenRepository repo() {
        return new MavenRepository(dist.testFile('repo'))
    }
}
