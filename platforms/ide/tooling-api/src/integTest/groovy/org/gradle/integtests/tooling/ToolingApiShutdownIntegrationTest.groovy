/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Ignore
import spock.lang.Retry
import spock.lang.Timeout

@Ignore
class ToolingApiShutdownIntegrationTest extends AbstractIntegrationSpec {

    @Retry(count = 3)
    @Timeout(30)
    def "tooling api can disconnect from hanging daemon"() {
        setup:
        settingsFile << "rootProject.name = 'client-runner'"
        buildFile << """
            plugins {
                id 'java'
            }

            repositories {
                ${mavenCentralRepository()}
                maven { url = '${buildContext.localRepository.toURI()}' }
            }

            tasks.register('runGradleBuildWithUnstableDaemon', JavaExec) {
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ToolingApiClient"
                args = [ file("test-project") ]
            }

            dependencies {
                implementation 'org.gradle:gradle-tooling-api:${distribution.version.baseVersion.version}'
            }
        """
        file("src/main/java/ToolingApiClient.java") << """
            import org.gradle.tooling.GradleConnector;
            import org.gradle.tooling.ProjectConnection;
            import org.gradle.tooling.ResultHandler;
            import org.gradle.tooling.GradleConnectionException;

            import java.io.ByteArrayOutputStream;
            import java.io.File;

            public class ToolingApiClient {
                public static void main(String[] args) throws Exception {
                    runHelp(new File(args[0]));
                }

                private static void runHelp(File projectLocation) throws Exception {
                    GradleConnector connector = GradleConnector.newConnector().useGradleVersion("6.8-20201118175938+0000"); // can be changed to latest release after 6.8 is available
                    ProjectConnection connection = connector.forProjectDirectory(projectLocation).connect();
                    connection.newBuild().forTasks("help").run(new ResultHandler<Void>() {
                        public void onComplete(Void result) { }
                        public void onFailure(GradleConnectionException failure) { }
                    });
                    connector.disconnect();
                }
            }
        """
        file("test-project/build.gradle") << ""
        file("test-project/settings.gradle") << ""
        file("test-project/gradle.properties") << "systemProp.org.gradle.internal.testing.daemon.hang=60000"

        when:
        succeeds("runGradleBuildWithUnstableDaemon")

        then:
        output.contains("BUILD SUCCESSFUL")
    }
}
