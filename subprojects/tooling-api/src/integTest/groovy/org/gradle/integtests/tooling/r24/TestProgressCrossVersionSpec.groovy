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
package org.gradle.integtests.tooling.r24

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.*

class TestProgressCrossVersionSpec extends ToolingApiSpecification {

    @ToolingApiVersion(">=2.4")
    @TargetGradleVersion(">=2.4")
    def "receive 'test started' progress event"() {
        given:
        projectDir.file("build.gradle").text = '''
apply plugin: 'java'
repositories { mavenCentral() }
dependencies { testCompile "junit:junit:4.12" }
task foo(type:Test)
'''
        projectDir.create {
            src {
                test {
                    java {
                        file('MyTest.java').text = '''
public class MyTest {
  @org.junit.Test
  public void foo() {
     org.junit.Assert.assertEquals(1, 1);
  }
}
'''
                    }
                }
            }
        }

        when:
        List<TestProgressEvent> result = []
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                @Override
                void statusChanged(TestProgressEvent event) {
                    result << event
                }
            }).run()
        }

        then:
        result.size() % 2 == 0          // same number of start events as finish events
        result.size() == 8              // root suite, test process suite, test class suite, test method (each with a start and finish event)

        def rootStartedEvent = result[0]
        rootStartedEvent instanceof TestSuiteStartedEvent &&
                rootStartedEvent.descriptor.name == 'Test Run' &&
                rootStartedEvent.descriptor.className == null &&
                rootStartedEvent.descriptor.parent == null
        def testProcessStartedEvent = result[1]
        testProcessStartedEvent instanceof TestSuiteStartedEvent &&
                testProcessStartedEvent.descriptor.name == 'Gradle Test Executor 1' &&
                testProcessStartedEvent.descriptor.className == null &&
                testProcessStartedEvent.descriptor.parent == null
        def testClassStartedEvent = result[2]
        testClassStartedEvent instanceof TestSuiteStartedEvent &&
                testClassStartedEvent.descriptor.name == 'MyTest' &&
                testClassStartedEvent.descriptor.className == 'MyTest' &&
                testClassStartedEvent.descriptor.parent == null
        def testStartedEvent = result[3]
        testStartedEvent instanceof TestStartedEvent &&
                testStartedEvent.descriptor.name == 'foo' &&
                testStartedEvent.descriptor.className == 'MyTest' &&
                testStartedEvent.descriptor.parent == null
        def testSucceededEvent = result[4]
        testSucceededEvent instanceof TestSucceededEvent &&
                testSucceededEvent.descriptor.name == 'foo' &&
                testSucceededEvent.descriptor.className == 'MyTest' &&
                testSucceededEvent.descriptor.parent == null
        def testClassSucceededEvent = result[5]
        testClassSucceededEvent instanceof TestSuiteSucceededEvent &&
                testClassSucceededEvent.descriptor.name == 'MyTest' &&
                testClassSucceededEvent.descriptor.className == 'MyTest' &&
                testClassSucceededEvent.descriptor.parent == null
        def testProcessSucceededEvent = result[6]
        testProcessSucceededEvent instanceof TestSuiteSucceededEvent &&
                testProcessSucceededEvent.descriptor.name == 'Gradle Test Executor 1' &&
                testProcessSucceededEvent.descriptor.className == null &&
                testProcessSucceededEvent.descriptor.parent == null
        def rootSucceededEvent = result[7]
        rootSucceededEvent instanceof TestSuiteSucceededEvent &&
                rootSucceededEvent.descriptor.name == 'Test Run' &&
                rootSucceededEvent.descriptor.className == null &&
                rootSucceededEvent.descriptor.parent == null
    }

}
