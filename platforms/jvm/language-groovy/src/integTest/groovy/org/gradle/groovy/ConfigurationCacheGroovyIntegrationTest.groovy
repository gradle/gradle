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

package org.gradle.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(value = IntegTestPreconditions.NotConfigCached, reason = "handles CC explicitly")
class ConfigurationCacheGroovyIntegrationTest extends AbstractIntegrationSpec {
    def configurationCache = newConfigurationCacheFixture()

    @Override
    void setupExecuter() {
        super.setupExecuter()
        executer.withConfigurationCacheEnabled()
    }

    def "build on Groovy project with JUnit tests"() {
        given:
        buildFile << """
            plugins { id 'groovy' }

            ${mavenCentralRepository()}

            dependencies {
                implementation(localGroovy())
                testImplementation("junit:junit:4.13")
            }
        """
        file("src/main/groovy/Thing.groovy") << """
            class Thing {}
        """
        file("src/test/groovy/ThingTest.groovy") << """
            import org.junit.*
            class ThingTest {
                @Test void ok() { new Thing() }
            }
        """

        and:
        def expectedTasks = [
            ":compileJava", ":compileGroovy", ":processResources", ":classes", ":jar", ":assemble",
            ":compileTestJava", ":compileTestGroovy", ":processTestResources", ":testClasses", ":test", ":check",
            ":build"
        ]
        def classFile = file("build/classes/groovy/main/Thing.class")
        def testClassFile = file("build/classes/groovy/test/ThingTest.class")
        def testResults = file("build/test-results/test")

        when:
        run "build"

        then:
        configurationCache.assertStateStored()
        result.assertTasksScheduled(*expectedTasks)

        and:
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()

        and:
        assertTestsExecuted("ThingTest", "ok")

        when:
        run "clean"

        and:
        run "build"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksScheduled(*expectedTasks)

        and:
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()

        and:
        assertTestsExecuted("ThingTest", "ok")
    }

    def "build on Groovy project without sources nor groovy dependency"() {
        given:
        buildFile << """
            plugins { id 'groovy' }
        """

        when:
        run "build"

        then:
        configurationCache.assertStateStored()

        when:
        run "clean"
        run "build"

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskScheduled(":compileGroovy")
        result.assertTaskSkipped(":compileGroovy")
    }

    def "assemble on Groovy project with sources but no groovy dependency is executed and fails with a reasonable error message"() {
        given:
        buildFile << """
            plugins { id 'groovy' }
        """
        file("src/main/groovy/Thing.groovy") << """
            class Thing {}
        """

        when:
        fails "--configuration-cache-problems=warn", "assemble"

        then:
        configurationCache.assertStateStored()

        and:
        result.assertTaskScheduled(":compileGroovy")
        failureDescriptionStartsWith("Execution failed for task ':compileGroovy'.")
        failureCauseContains("Cannot infer Groovy class path because no Groovy Jar was found on class path")

        when:
        fails "assemble"

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskScheduled(":compileGroovy")
        failureDescriptionStartsWith("Execution failed for task ':compileGroovy'.")
        failureCauseContains("Cannot infer Groovy class path because no Groovy Jar was found on class path")
    }

    protected void assertTestsExecuted(String testClass, String... testNames) {
        new DefaultTestExecutionResult(testDirectory)
            .testClass(testClass)
            .assertTestsExecuted(testNames)
    }
}
