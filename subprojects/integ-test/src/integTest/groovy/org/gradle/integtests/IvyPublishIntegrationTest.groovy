package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.HttpServer
import org.junit.After
import org.junit.Rule
import org.junit.Test

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
    }
}
