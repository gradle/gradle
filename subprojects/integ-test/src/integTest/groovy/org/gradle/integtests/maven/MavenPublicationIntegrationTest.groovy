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

import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.MavenRepository
import org.junit.Test

class MavenPublicationIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources testResources = new TestResources()

    @Test
    public void canPublishAProjectWithDependencyInMappedAndUnMappedConfiguration() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsDeployed('root-1.0.jar', 'root-1.0.pom')
    }

    @Test
    public void canPublishAProjectWithNoMainArtifact() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsDeployed('root-1.0-source.jar')
    }

    @Test
    public void canPublishAProjectWithMetadataArtifacts() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsDeployed('root-1.0.jar', 'root-1.0.jar.sig', 'root-1.0.pom', 'root-1.0.pom.sig')
    }

    @Test
    public void canPublishASnapshotVersion() {
        dist.testFile('build.gradle') << """
apply plugin: 'java'
apply plugin: 'maven'

group = 'org.gradle'
version = '1.0-SNAPSHOT'
archivesBaseName = 'test'

uploadArchives {
    repositories {
        mavenDeployer {
            snapshotRepository(url: uri("mavenRepo"))
        }
    }
}
"""

        executer.withTasks('uploadArchives').run()

        def module = repo().module('org.gradle', 'test', '1.0-SNAPSHOT')
        module.assertArtifactsDeployed('test-1.0-SNAPSHOT.jar', 'test-1.0-SNAPSHOT.pom')
    }

    def MavenRepository repo() {
        new MavenRepository(dist.testFile('mavenRepo'))
    }

}
