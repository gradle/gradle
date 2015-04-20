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


package org.gradle.integtests.tooling.r25

import org.gradle.api.GradleException
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection

class TestLauncherCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "can execute test from BuildInvocation"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/example/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     org.junit.Assert.assertEquals(1, 1);
                }
            }
        """

        when: "we create a new test launcher with a test pattern to execute"
        def result = []
        withConnection {
            ProjectConnection connection ->
                connection.newTestsLauncher()
                        .addJvmTestClasses('example.MyTest')
                        .addTestProgressListener {
                            result.add(it)
                    println it
                        }
                        .run()
        }

        then: "the test is executed"
        assert result.size()>0
    }
}
