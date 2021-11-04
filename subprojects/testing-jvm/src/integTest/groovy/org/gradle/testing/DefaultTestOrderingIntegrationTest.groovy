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
package org.gradle.testing

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_4_LATEST
import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE_JUPITER

@TargetCoverage({ JUNIT_4_LATEST + JUNIT_VINTAGE_JUPITER })
class DefaultTestOrderingIntegrationTest extends MultiVersionIntegrationSpec {

    @Rule public final TestResources resources = new TestResources(temporaryFolder)

    def setup() {
        executer.noExtraLogging()
        executer.withRepositoryMirrors()
    }

    private void addEmptyTestClass(String testName) {
        file("src/test/java/${testName}.java") << """
import org.junit.*;
public class ${testName} {
    @Test public void test() {}
}
"""
    }

    def "test classes are scanned and run in deterministic order by default"() {
        addEmptyTestClass("AdTest")
        addEmptyTestClass("AATest")
        addEmptyTestClass("AyTest")
        addEmptyTestClass("AÆTest")
        addEmptyTestClass("ACTest")
        addEmptyTestClass("AÄTest")
        addEmptyTestClass("AZTest")
        addEmptyTestClass("AbTest")

        String expectedTestMessages = """
executed Test test(AATest)
executed Test test(ACTest)
executed Test test(AZTest)
executed Test test(AbTest)
executed Test test(AdTest)
executed Test test(AyTest)
executed Test test(AÄTest)
executed Test test(AÆTest)
"""

        file("build.gradle") << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies { testImplementation 'junit:junit:4.12' }
            test.beforeTest { println "executed " + it }
        """

        when:
        succeeds("test")

        then:
        outputContains(expectedTestMessages)
    }
}
