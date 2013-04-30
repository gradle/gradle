/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildsetup.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult


class JavaLibrarySetupIntegrationTest extends AbstractIntegrationSpec {

    def "creates sample source if no source present"() {
        when:
        def executed = succeeds('setupBuild', '--type', 'java-library')
        then:
        executed.assertTasksExecuted(":generateBuildFile", ":generateSettingsFile", ":setupProjectLayout", ":wrapper", ":setupBuild")
        and:
        file("src/main/java/Library.java").exists()


        when:
        succeeds("build")
        then:
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        testResult.assertTestClassesExecuted("LibraryTest")
        testResult.testClass("LibraryTest").assertTestPassed("testSomeLibraryMethod")
    }

    def "setupProjectLayout is skipped when sources detected"() {
        setup:
        file("src/main/java/org/acme/SampleMain.java") << """
        package org.acme;

        public class SampleMain{
        }
"""
        file("src/test/java/org/acme/SampleMainTest.java") << """
                package org.acme;

                public class SampleMain{
                }
        """
        when:
        def executed = succeeds('setupBuild', '--type', 'java-library')
        then:
        executed.assertTasksExecuted(":generateBuildFile", ":generateSettingsFile", ":setupProjectLayout", ":wrapper", ":setupBuild")
        executed.assertTaskSkipped(":setupProjectLayout")
        and:
        file("src/main/java").exists()

    }
}
