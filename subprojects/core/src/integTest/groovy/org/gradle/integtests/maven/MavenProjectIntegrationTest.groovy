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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.integtests.DistributionIntegrationTestRunner
import org.gradle.integtests.fixtures.TestResources

@RunWith(DistributionIntegrationTestRunner.class)
class MavenProjectIntegrationTest {
    @Rule public final GradleDistribution dist = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule public final TestResources testResources = new TestResources()

    @Test
    public void handlesSubProjectsWithoutTheMavenPluginApplied() {
        dist.testFile("settings.gradle").write("include 'subProject'");
        dist.testFile("build.gradle") << '''
            apply plugin: 'java'
            apply plugin: 'maven'
        '''
        executer.withTaskList().run();
    }

    @Test
    public void canDeployAProjectWithDependencyInMappedAndUnMappedConfiguration() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsDeployed('root-1.0.jar')
    }

    @Test
    public void canDeployAProjectWithNoMainArtifact() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsDeployed('root-1.0-source.jar')
    }

    @Test
    public void canDeployAProjectWithMetadataArtifacts() {
        executer.withTasks('uploadArchives').run()
        def module = repo().module('group', 'root', 1.0)
        module.assertArtifactsDeployed('root-1.0.jar', 'root-1.0.jar.sig', 'root-1.0.pom', 'root-1.0.pom.sig')
    }

    def MavenRepository repo() {
        new MavenRepository(dist.testFile('mavenRepo'))
    }
}
