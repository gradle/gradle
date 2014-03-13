/*
 * Copyright 2013 the original author or authors.
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


package org.gradle.testing.junit

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

public class JUnitFilteringSupportIntegrationTest extends AbstractIntegrationSpec {

    void "informs that we dont support filtering for lower versions of JUnit 4.x"() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.5' }
            test.filter.includeTestsMatching "FooTest.pass"
        """

        file("src/test/java/FooTest.java") << """import org.junit.*;
        public class FooTest {
            @Test public void pass() {}
        }
        """

        when: fails("test")

        then:
        failure.error.contains("Test filtering is not supported for given version of JUnit. Please upgrade JUnit version to at least 4.6.")
    }

    void "informs that we dont support filtering for JUnit 3.x"() {
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:3.8.1' }
            test.filter.includeTestsMatching "FooTest.pass"
        """

        file("src/test/java/FooTest.java") << """
            public class FooTest {
                public void testPass() {}
            }
        """

        when: fails("test")

        then: failure.error.contains("Test filtering is not supported for given version of JUnit. Please upgrade JUnit version to at least 4.6.")
    }
}