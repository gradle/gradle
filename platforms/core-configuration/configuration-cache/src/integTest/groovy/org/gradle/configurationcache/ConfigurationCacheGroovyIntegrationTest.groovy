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

package org.gradle.configurationcache

class ConfigurationCacheGroovyIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "build on Groovy project with JUnit tests"() {

        def configurationCache = newConfigurationCacheFixture()

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
        configurationCacheRun "build"

        then:
        configurationCache.assertStateStored()
        result.assertTasksExecuted(*expectedTasks)

        and:
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()

        and:
        assertTestsExecuted("ThingTest", "ok")

        when:
        configurationCacheRun "clean"

        and:
        configurationCacheRun "build"

        then:
        configurationCache.assertStateLoaded()
        result.assertTasksExecuted(*expectedTasks)

        and:
        classFile.isFile()
        testClassFile.isFile()
        testResults.isDirectory()

        and:
        assertTestsExecuted("ThingTest", "ok")
    }

    def "build on Groovy project without sources nor groovy dependency"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            plugins { id 'groovy' }
        """

        when:
        configurationCacheRun "build"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "clean"
        configurationCacheRun "build"

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskExecuted(":compileGroovy")
        result.assertTaskSkipped(":compileGroovy")
    }

    def "assemble on Groovy project with sources but no groovy dependency is executed and fails with a reasonable error message"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            plugins { id 'groovy' }
        """
        file("src/main/groovy/Thing.groovy") << """
            class Thing {}
        """

        when:
        configurationCacheFails WARN_PROBLEMS_CLI_OPT, "assemble"

        then:
        configurationCache.assertStateStored()

        and:
        result.assertTaskExecuted(":compileGroovy")
        failureDescriptionStartsWith("Execution failed for task ':compileGroovy'.")
        failureCauseContains("Cannot infer Groovy class path because no Groovy Jar was found on class path")

        when:
        configurationCacheFails "assemble"

        then:
        configurationCache.assertStateLoaded()
        result.assertTaskExecuted(":compileGroovy")
        failureDescriptionStartsWith("Execution failed for task ':compileGroovy'.")
        failureCauseContains("Cannot infer Groovy class path because no Groovy Jar was found on class path")
    }
}
