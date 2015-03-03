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
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.TestProgressEvent
import org.gradle.tooling.TestProgressListener

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
        def result = []
        withConnection { ProjectConnection connection ->
            connection.newBuild().forTasks('test').addTestProgressListener(new TestProgressListener() {
                @Override
                void statusChanged(TestProgressEvent event) {
                    result << event
                }
            }).run()
        }

        then:
        result.size() % 2 == 0 // same number of start events as finish events
        result.size() == 8     // root suite, test process suite, test class suite, test method (each with a start and finish event)
        result.findAll { def event ->
            event.eventTypeId == -1 &&
            event.descriptor.name == 'foo' &&
            event.descriptor.className == 'MyTest' &&
            event.descriptor.parent == null &&
            event.result == null
        }.size() == 2          // test method start and finish event
    }

}
