/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.TestResources
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * @author Hans Dockter
 */
class MavenSnapshotIntegrationTest {
    @Rule public GradleDistribution distribution = new GradleDistribution()
    @Rule public GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources testResources = new TestResources()

    @Before
    public void setup() {
        distribution.requireOwnUserHomeDir()
    }

    @Test
    public void retrievesAndCacheLocalSnapshot() {
        def producerProject = distribution.testFile('producer.gradle')
        def consumerProject = distribution.testFile('projectWithMavenSnapshots.gradle')

        // Publish the first snapshot
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').run()

        // Retrieve the first snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').run()
        def jarFile = distribution.testFile('build/testproject-1.0-SNAPSHOT.jar')
        def snapshot = jarFile.assertIsFile().snapshot()

        // Retrieve again should use cached snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').run().assertTasksSkipped(':retrieve')
        jarFile.assertHasNotChangedSince(snapshot)

        // Publish the second snapshot
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').withArguments("-PemptyJar").run()

        // Retrieve again should use updated snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').run().assertTasksNotSkipped(':retrieve')
        jarFile.assertHasChangedSince(snapshot)
    }

    @Test
    public void retrievesAndCacheSnapshotViaHttp() {
        HttpServer server = new HttpServer()
        server.add('/repo', distribution.testFile('repo'))
        server.start()
        String repoUrl = "-PrepoUrl=http://localhost:${server.port}/repo"

        def producerProject = distribution.testFile('producer.gradle')
        def consumerProject = distribution.testFile('projectWithMavenSnapshots.gradle')

        // Publish the first snapshot
        executer.usingBuildScript(producerProject).withTasks('uploadArchives').run()

        // Retrieve the first snapshot
        executer.usingBuildScript(consumerProject).withTasks('retrieve').withArguments(repoUrl).run()
        def jarFile = distribution.testFile('build/testproject-1.0-SNAPSHOT.jar')
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
        
        server.stop()
    }
}
