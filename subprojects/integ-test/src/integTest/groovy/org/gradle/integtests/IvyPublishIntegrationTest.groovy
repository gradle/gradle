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
package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.HttpServer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.hamcrest.Matchers

public class IvyPublishIntegrationTest {
    @Rule
    public final GradleDistribution dist = new GradleDistribution()
    @Rule
    public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    final HttpServer server = new HttpServer()

    @After
    public void tearDown() {
        server.stop()
    }

    @Test
    public void testFailedPublish() {
        server.start()

        dist.testFile("build.gradle") << """
apply plugin: 'java'
uploadArchives {
        repositories.add(new org.apache.ivy.plugins.resolver.URLResolver()) {
            name = 'gradleReleases'
            addArtifactPattern("http://localhost:${server.port}/[module]/[revision]/[artifact]-[revision](-[classifier]).[ext]" as String)
        }
}
"""

        def result = executer.withTasks("uploadArchives").runWithFailure()
        result.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        result.assertHasCause('Could not publish configurations [configuration \':archives\'].')
        result.assertThatCause(Matchers.containsString('failed with status code 404'))

        server.stop()

        result = executer.withTasks("uploadArchives").runWithFailure()
        result.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        result.assertHasCause('Could not publish configurations [configuration \':archives\'].')
        result.assertHasCause('java.net.ConnectException: Connection refused')
    }
}
