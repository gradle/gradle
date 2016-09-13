/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.buildinit.plugins

import org.gradle.buildinit.plugins.fixtures.WrapperTestFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

class GradlePluginInitIntegrationTest extends AbstractIntegrationSpec {

    public static final String SAMPLE_PLUGIN_CLASS = "src/main/java/HelloWorldPlugin.java"
    public static final String SAMPLE_PLUGIN_TASK_CLASS = "src/main/java/HelloWorld.java"
    public static final String SAMPLE_PLUGIN_TEST_CLASS = "src/functionalTest/groovy/FunctionalTest.groovy"

    final wrapper = new WrapperTestFixture(testDirectory)

    // TestKit cannot find a usable Gradle distribution if we're running embedded.
    @IgnoreIf({ GradleContextualExecuter.embedded })
    def "creates sample source if no source present"() {
        when:
        succeeds('init', '--type', 'gradle-plugin')

        then:
        file(SAMPLE_PLUGIN_CLASS).exists()
        file(SAMPLE_PLUGIN_TASK_CLASS).exists()
        file(SAMPLE_PLUGIN_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()

        when:
        succeeds("build")

        then:
        TestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory, "build", "", "", "functionalTest")
        testResult.assertTestClassesExecuted("FunctionalTest")
        testResult.testClass("FunctionalTest").assertTestPassed("hello world task prints hello world")
    }

    def "setupProjectLayout is skipped when groovy sources detected"() {
        setup:
        file("src/main/java/org/acme/SampleMain.groovy") << """
            package org.acme;

            class SampleMain{
            }
    """
        file("src/functionalTest/groovy/org/acme/SampleMainTest.groovy") << """
                    package org.acme;

                    class SampleMain{
                    }
            """
        when:
        succeeds('init', '--type', 'groovy-library')

        then:
        !file(SAMPLE_PLUGIN_CLASS).exists()
        !file(SAMPLE_PLUGIN_TASK_CLASS).exists()
        !file(SAMPLE_PLUGIN_TEST_CLASS).exists()
        buildFile.exists()
        settingsFile.exists()
        wrapper.generated()
    }
}
