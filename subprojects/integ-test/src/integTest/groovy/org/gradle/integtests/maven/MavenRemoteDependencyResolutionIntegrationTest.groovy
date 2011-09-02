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
package org.gradle.integtests.maven

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
    mavenRepo(urls: "http://localhost:${server.port}/repo") {
        if (project.hasProperty('noTimeout')) {
            setSnapshotTimeout(0L)
        }
    }
}

configurations { compile }

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
        module.publishArtifact()

        // Retrieve the first snapshot
        def repoDir = dist.testFile('repo/org/gradle/testproject/1.0-SNAPSHOT')
        repoDir.assertIsDir()
        def pom = repoDir.listFiles().find { it.name.matches('.*-1.pom') }
        def jar = repoDir.listFiles().find { it.name.matches('.*-1.jar') }
        assert pom && jar
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        // TODO - it would be nice to defer these
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-sources.jar')}")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-src.jar')}")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-javadoc.jar')}")

        // TODO - these should not be here
        server.expectHead('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file('maven-metadata.xml'))
        7.times {
            server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        }
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)
        server.expectGetMissing('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.sha1')
        server.expectGetMissing('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.md5')
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.sha1")
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.md5")
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}.sha1")
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}.md5")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-sources.jar")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-src.jar")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-javadoc.jar")

        executer.withTasks('retrieve').run()
        def jarFile = dist.testFile('build/testproject-1.0-SNAPSHOT.jar')

        jarFile.assertIsCopyOf(module.artifactFile)
        def snapshot = jarFile.snapshot()

        // Publish the second snapshot
        module.publishWithChangedContent()

        // TODO - these should not be here
        server.expectHead('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file('maven-metadata.xml'))
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGetMissing('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.sha1')
        server.expectGetMissing('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.md5')

        // Retrieve again should use cached snapshot, and should not hit the server
        executer.withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)

        // Retrieve again with zero timeout should download and use updated snapshot

        pom = repoDir.listFiles().find { it.name.matches('.*-2.pom') }
        jar = repoDir.listFiles().find { it.name.matches('.*-2.jar') }
        assert pom && jar
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        // TODO - it would be nice to defer these
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-sources.jar')}")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-src.jar')}")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-javadoc.jar')}")

        // TODO - these should not be here
        server.expectHead('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file('maven-metadata.xml'))
        7.times {
            server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        }
        server.expectGetMissing('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.sha1')
        server.expectGetMissing('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.md5')
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.sha1")
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.md5")
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}.sha1")
        server.expectGetMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}.md5")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-sources.jar")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-src.jar")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-javadoc.jar")

        executer.withTasks('retrieve').withArguments("-PnoTimeout").run().assertTasksNotSkipped(':retrieve')
        jarFile.assertIsCopyOf(module.artifactFile)
    }

    @Test
    public void "does not download snapshot artifacts after expiry when snapshot has not changed"() {
        dist.requireOwnUserHomeDir()
        server.start()

        dist.testFile('build.gradle') << """
repositories {
    mavenRepo(urls: "http://localhost:${server.port}/repo") {
        setSnapshotTimeout(0)
    }
}

configurations { compile }

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
        module.publishArtifact()

        // Retrieve the first snapshot
        def repoDir = module.moduleDir
        repoDir.assertIsDir()
        def pom = repoDir.listFiles().find { it.name.matches('.*-1.pom') }
        def jar = repoDir.listFiles().find { it.name.matches('.*-1.jar') }
        assert pom && jar
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        // TODO - it would be nice to defer these
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-sources.jar')}")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-src.jar')}")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name.replace('.jar', '-javadoc.jar')}")

        // TODO - these should not be here
        server.expectHead('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file('maven-metadata.xml'))
        7.times {
            server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        }
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.sha1', repoDir.file("maven-metadata.xml.sha1"))
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.md5', repoDir.file("maven-metadata.xml.md5"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.sha1", repoDir.file("${pom.name}.sha1"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.md5", repoDir.file("${pom.name}.md5"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}.sha1", repoDir.file("${jar.name}.sha1"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}.md5", repoDir.file("${jar.name}.md5"))
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-sources.jar")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-src.jar")
        server.expectHeadMissing("/repo/org/gradle/testproject/1.0-SNAPSHOT/testproject-1.0-SNAPSHOT-javadoc.jar")

        executer.withTasks('retrieve').run()
        def jarFile = dist.testFile('build/testproject-1.0-SNAPSHOT.jar')
        jarFile.assertIsCopyOf(module.artifactFile)
        def snapshot = jarFile.assertIsFile().snapshot()

        // Retrieve again with zero timeout should check for updated snapshot
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))

        // TODO - these should not be here
        1.times {
            server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file("maven-metadata.xml"))
        }
        server.expectHead('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml', repoDir.file('maven-metadata.xml'))
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.sha1', repoDir.file("maven-metadata.xml.sha1"))
        server.expectGet('/repo/org/gradle/testproject/1.0-SNAPSHOT/maven-metadata.xml.md5', repoDir.file("maven-metadata.xml.md5"))
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}", pom)
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.sha1", repoDir.file("${pom.name}.sha1"))
        server.expectGet("/repo/org/gradle/testproject/1.0-SNAPSHOT/${pom.name}.md5", repoDir.file("${pom.name}.md5"))
        server.expectHead("/repo/org/gradle/testproject/1.0-SNAPSHOT/${jar.name}", jar)

        executer.withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)
    }

    @Test
    public void "can resolve and cache dependencies from HTTP Maven repository"() {
        dist.requireOwnUserHomeDir()

        def projectA = repo().module('group', 'projectA')
        projectA.publishArtifact()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)

        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0-sources.jar')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0-src.jar')

        // TODO - these should not be here
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom.sha1')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom.md5')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.jar.sha1')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.jar.md5')

        server.start()

        dist.testFile('build.gradle') << """
repositories {
    maven { url = 'http://localhost:${server.port}/repo1' }
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
        executer.withTasks('listJars').run()
    }

    @Test
    public void "can resolve and cache dependencies from multiple HTTP Maven repositories"() {
        dist.requireOwnUserHomeDir()

        def projectA = repo().module('group', 'projectA')
        def projectB = repo().module('group', 'projectB')
        projectA.publishArtifact()
        projectB.publishArtifact()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)

        // TODO - it would be nice to defer this
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0-sources.jar')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0-src.jar')
        server.expectHeadMissing('/repo1/group/projectA/1.0/projectA-1.0-javadoc.jar')
        server.expectHeadMissing('/repo2/group/projectB/1.0/projectB-1.0-sources.jar')
        server.expectHeadMissing('/repo2/group/projectB/1.0/projectB-1.0-src.jar')
        server.expectHeadMissing('/repo2/group/projectB/1.0/projectB-1.0-javadoc.jar')

        // TODO - these should not be here
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom.sha1')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.pom.md5')
        server.expectHead('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.jar.sha1')
        server.expectGetMissing('/repo1/group/projectA/1.0/projectA-1.0.jar.md5')
        server.expectHeadMissing('/repo2/group/projectA/1.0/projectA-1.0-sources.jar')
        server.expectHeadMissing('/repo2/group/projectA/1.0/projectA-1.0-src.jar')
        server.expectHeadMissing('/repo2/group/projectA/1.0/projectA-1.0-javadoc.jar')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')
        server.expectHead('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGetMissing('/repo2/group/projectB/1.0/projectB-1.0.pom.sha1')
        server.expectGetMissing('/repo2/group/projectB/1.0/projectB-1.0.pom.md5')
        server.expectHead('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)
        server.expectGetMissing('/repo2/group/projectB/1.0/projectB-1.0.jar.sha1')
        server.expectGetMissing('/repo2/group/projectB/1.0/projectB-1.0.jar.md5')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0-sources.jar')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0-src.jar')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0-javadoc.jar')

        server.start()

        dist.testFile('build.gradle') << """
repositories {
    maven { url = 'http://localhost:${server.port}/repo1' }
    maven { url = 'http://localhost:${server.port}/repo2' }
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

        // TODO - these should not be here
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0.pom')
        server.expectHeadMissing('/repo1/group/projectB/1.0/projectB-1.0.jar')

        executer.withTasks('listJars').run()
    }

    MavenRepository repo() {
        return new MavenRepository(dist.testFile('repo'))
    }
}
