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
import org.junit.Ignore

class MavenDependencyResolutionIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources resources = new TestResources()
    @Rule public final HttpServer server = new HttpServer()

    @Test
    public void canResolveDependenciesFromMultipleMavenRepositories() {
        List expectedFiles = ['sillyexceptions-1.0.1.jar', 'repotest-1.0.jar', 'testdep-1.0.jar', 'testdep2-1.0.jar',
                'classifier-1.0-jdk15.jar', 'classifier-dep-1.0.jar', 'jaronly-1.0.jar']

        File projectDir = dist.testDir
        // Ignore deprecation warnings, since MavenRepo().allownomd is deprecated and used in test build script
        executer.inDirectory(projectDir).withTasks('retrieve').run()
        expectedFiles.each { new TestFile(projectDir, 'build', it).assertExists() }
    }

    @Test
    public void retrievesAndCachesLocalSnapshot() {
        dist.requireOwnUserHomeDir()
        def producerProject = dist.testFile('producer.gradle')
        def consumerProject = dist.testFile('projectWithMavenSnapshots.gradle')

        // Publish the first snapshot
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').run()

        // Retrieve the first snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').run()
        def jarFile = dist.testFile('build/testproject-1.0-SNAPSHOT.jar')
        def snapshot = jarFile.assertIsFile().snapshot()

        // Retrieve again should use cached snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)

        // Publish the second snapshot
        Thread.sleep(1100)
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').withArguments("-PemptyJar").run()

        // Retrieve again should use updated snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').run().assertTasksNotSkipped(':retrieve')
        jarFile.assertHasChangedSince(snapshot)
    }

    @Test
    public void retrievesAndCachesSnapshotViaHttp() {
        dist.requireOwnUserHomeDir()
        server.allowGet('/repo', dist.testFile('repo'))
        server.start()
        String repoUrl = "-PrepoUrl=http://localhost:${server.port}/repo"

        def producerProject = dist.testFile('producer.gradle')
        def consumerProject = dist.testFile('projectWithMavenSnapshots.gradle')

        // Publish the first snapshot
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').run()

        // Retrieve the first snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').withArguments(repoUrl).run()
        def jarFile = dist.testFile('build/testproject-1.0-SNAPSHOT.jar')
        def snapshot = jarFile.assertIsFile().snapshot()

        // Publish the second snapshot
        Thread.sleep(1100)
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').withArguments("-PemptyJar").run()

        // Retrieve again should use cached snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').withArguments(repoUrl).run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)

        // Retrieve again with zero timeout should use updated snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').withArguments("-PnoTimeout", repoUrl).run().assertTasksNotSkipped(':retrieve')
        jarFile.assertHasChangedSince(snapshot)
    }

    @Test @Ignore
    public void "can resolve and cache dependencies from HTTP Maven repository"() {
        def projectA = repo().module('group', 'projectA')
        projectA.publishArtifact()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.start()

        dist.testFile('build.gradle') << """
repositories {
    mavenRepo(name: 'repo', urls: 'http://localhost:${server.port}/repo1')
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

    @Test @Ignore
    public void "can resolve and cache dependencies from multiple HTTP Maven repositories"() {
        def projectA = repo().module('group', 'projectA')
        def projectB = repo().module('group', 'projectB')
        projectA.publishArtifact()
        projectB.publishArtifact()
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.pom', projectA.pomFile)
        server.expectGet('/repo1/group/projectA/1.0/projectA-1.0.jar', projectA.artifactFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.pom', projectB.pomFile)
        server.expectGet('/repo2/group/projectB/1.0/projectB-1.0.jar', projectB.artifactFile)
        server.start()

        dist.testFile('build.gradle') << """
repositories {
    mavenRepo(name: 'repo', urls: 'http://localhost:${server.port}/repo1')
    mavenRepo(name: 'repo2', urls: 'http://localhost:${server.port}/repo2')
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
        executer.withTasks('listJars').run()
    }

    MavenRepository repo() {
        return new MavenRepository(dist.testFile('repo'))
    }
}
