/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r27
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.model.gradle.GradleBuild

import java.util.concurrent.CountDownLatch

class ConcurrentToolingApiCrossVersionTest extends ToolingApiSpecification {

    File binDistribution
    File allDistribution

    def setup() {
        binDistribution = buildContext.getDistributionsDir().file(String.format("gradle-2.8-20150914200118+0000-bin.zip", buildContext.getVersion().getVersion()))
        allDistribution = buildContext.getDistributionsDir().file(String.format("gradle-2.8-20150914200118+0000-all.zip", buildContext.getVersion().getVersion()))
    }

    def "test overlapping"() {
        given:
        // create two sample project
        File projectA = temporaryFolder.createDir("projectA")
        File projectB = temporaryFolder.createDir("projectB")
//
//        // the second one uses a gradle-2.6-all
//        FileWriter writer = new FileWriter(new File(projectA, "build.gradle"));
//        writer.write("task wrapper(type: Wrapper) { distributionUrl 'https://services.gradle.org/distributions/gradle-2.6-bin.zip'}");
//        writer.close();
//
//        // the second one uses a gradle-2.6-all
//        FileWriter writer2 = new FileWriter(new File(projectB, "build.gradle"));
//        writer2.write("task wrapper(type: Wrapper) { distributionUrl 'https://services.gradle.org/distributions/gradle-2.6-all.zip'}");
//        writer2.close();
//
//        // generate the wrapper for the second project
//        // if commented out then no exception happens
//        generateWrapper(projectA);
//        generateWrapper(projectB);

        // open a connection for both projects
        GradleConnector connector1 = GradleConnector.newConnector();
        connector1.forProjectDirectory(projectA);
        connector1.useDistribution(binDistribution.toURI())
        ProjectConnection connection1 = connector1.connect();
        GradleConnector connector2 = GradleConnector.newConnector();
        connector2.forProjectDirectory(projectB);
        connector2.useDistribution(allDistribution.toURI())
        ProjectConnection connection2 = connector2.connect();
        def evaluate = { true }
        when:
        for (int i = 0; i < 2; ++i) { // the exception happens the second time
            final CountDownLatch latch = new CountDownLatch(2);

            // query the same model for both projects asynchronously
            connection1.getModel(GradleBuild.class, new ResultHandler<GradleBuild>() {

                @Override
                public void onComplete(GradleBuild arg0) {
                    latch.countDown();
                }

                @Override
                public void onFailure(GradleConnectionException e) {
                    evaluate = { e.printStackTrace(); false }
                    latch.countDown();
                }
            });
            connection2.getModel(GradleBuild.class, new ResultHandler<GradleBuild>() {

                @Override
                public void onComplete(GradleBuild arg0) {
                    latch.countDown();
                }

                @Override
                public void onFailure(GradleConnectionException e) {
                    evaluate = { e.printStackTrace(); false }
                    latch.countDown();
                }
            });

            // wait the models to finish before proceeding to the next iteration
            latch.await();
        }

        then:
        evaluate()

        cleanup:
        true
        // close connections for both projects
        connection1.close();
        connection2.close();
    }


    private static void generateWrapper(File dir) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(dir);
        connector.useBuildDistribution();
        ProjectConnection connection = connector.connect();
        connection.newBuild().forTasks("wrapper").run();
        connection.close();
    }
}
