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
    def "captures output from logging tools"() {
        TestNGCoverage.enableTestNG(buildFile)
        buildFile << """
            test.onOutput { id, event -> println 'captured ' + event.message }
            dependencies { compile "org.slf4j:slf4j-simple:1.7.7", "org.slf4j:slf4j-api:1.7.7" }
        """

        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;
            import org.slf4j.*;

            public class FooTest {
                private final static Logger LOGGER = LoggerFactory.getLogger(FooTest.class);
                @Test public void foo() {
                  LOGGER.info("cool output from test");
                }
            }
        """

        when: run("test")
        then:
        result.output.contains("captured [Test worker] INFO FooTest - cool output from test")
    }

    @Issue("GRADLE-2841")
    def "captures logging from static initializers"() {
        TestNGCoverage.enableTestNG(buildFile)
        buildFile << "test.onOutput { id, event -> println 'captured ' + event.message }"

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
