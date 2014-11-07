/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.testing.testng

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.testing.fixture.TestNGCoverage
import spock.lang.Issue

class TestNGStaticLoggingIntegrationTest extends AbstractIntegrationSpec {

    @Issue("GRADLE-2841")
    def "captures logging from static initializers"() {
        buildFile << """
            apply plugin: 'java'
            repositories { jcenter() }
            dependencies { testCompile "org.testng:testng:${TestNGCoverage.NEWEST}" }
            test {
                useTestNG()
                onOutput { id, event -> println "captured " + event.message }
            }
        """

        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                static { System.out.println("cool output from initializer"); }
                @Test public void foo() { System.out.println("cool output from test"); }
            }
        """

        when: run("test")
        then:
        result.output.contains("captured cool output from test")
        result.output.contains("captured cool output from initializer")
    }
}
