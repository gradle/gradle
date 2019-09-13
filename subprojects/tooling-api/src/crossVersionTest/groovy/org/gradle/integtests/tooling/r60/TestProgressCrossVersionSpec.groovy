/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.tooling.r60

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.test.TestFailureResult2
import org.gradle.tooling.events.test.TestFinishEvent
import org.gradle.tooling.events.test.TestSuccessResult2
import org.gradle.tooling.events.test.internal.DefaultTestFinishEvent

class TestProgressCrossVersionSpec extends ToolingApiSpecification {

    // TODO (donat) is this the best place for the cross-version test?
    @ToolingApiVersion('>=6.0')
    @TargetGradleVersion('>=6.0')
    def "test execution exposes test output"() {
        given:
        goodCode()
        buildFile << 'test.ignoreFailures = true'
        file("src/test/java/example/MyTest2.java") << """
            package example;
            public class MyTest2 {
                @org.junit.Test public void works() throws Exception {
                    System.out.print("Winged Hussars");
                    System.err.print("The Last Battle");
                }
                @org.junit.Test public void fails() throws Exception {
                    System.out.print("To Hell And Back");
                    System.err.print("Gott mit uns");
                    org.junit.Assert.fail();
                }
            }
        """

        when:
        def events = []
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(
                    new org.gradle.tooling.events.ProgressListener() {

                        @Override
                        void statusChanged(ProgressEvent event) {
                            if (event instanceof DefaultTestFinishEvent) {
                                events << event
                            }
                        }
                    }
                ).run()
        }

        then:
        events.find { TestFinishEvent event -> event.result instanceof TestSuccessResult2 && event.result.output == 'Winged Hussars' && event.result.error == 'The Last Battle' }
        events.find { TestFinishEvent event -> event.result instanceof TestFailureResult2 && event.result.output == 'To Hell And Back' && event.result.error == 'Gott mit uns' }
    }
}
