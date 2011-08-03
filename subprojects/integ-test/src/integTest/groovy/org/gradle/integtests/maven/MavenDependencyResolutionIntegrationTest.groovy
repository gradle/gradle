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
        executer.withDeprecationChecksDisabled().inDirectory(projectDir).withTasks('retrieve').run()
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

}
