/*
 * Copyright 2026 the original author or authors.
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

@Issue("https://github.com/gradle/gradle/issues/26177")
class TestNGInitFailureConsoleLoggingIntegrationTest extends AbstractIntegrationSpec {
    def "framework initialization failure is logged to console under default granularity"() {
        TestNGCoverage.enableTestNG(buildFile, '6.3.1')
        file("src/test/java/FooTest.java") << """
            import org.testng.annotations.*;

            public class FooTest {
                public FooTest() { throw new NullPointerException("boom from ctor"); }
                @Test public void foo() {}
            }
        """

        expect:
        fails("test")

        // With default testLogging (minGranularity=-1), the framework-initialization
        // failure is attached to a composite descriptor and currently gets filtered
        // out by TestEventLogger.isLoggedGranularity — so the user sees only
        // "> There were failing tests" and has to hunt down the XML report.
        // After the fix, fatal/initialization failures should bypass granularity
        // filtering and surface the failing exception in the console output.
        outputContains("NullPointerException")
    }
}
