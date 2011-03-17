package org.gradle.integtests

import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.integtests.fixtures.GradleDistributionExecuter
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.Sample
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * @author Hans Dockter
 */
public class SamplesIvyPublishIntegrationTest {
    @Rule
    public final GradleDistribution dist = new GradleDistribution()
    @Rule
    public final GradleDistributionExecuter executer = new GradleDistributionExecuter()
    @Rule
    public final Sample sample = new Sample("ivypublish")

    @Test
    public void testPublish() {
        // the actual testing is done in the build script.
        File projectDir = sample.dir
        executer.inDirectory(projectDir).withTasks("uploadArchives").run()
    }
}
