/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.configurationcache.fixtures.SomeToolingModelBuildAction
import org.gradle.configurationcache.fixtures.ToolingApiBackedGradleExecuter
import org.gradle.configurationcache.fixtures.ToolingApiSpec
import org.gradle.integtests.fixtures.executer.GradleExecuter

class ConfigurationCacheToolingApiInvocationIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements ToolingApiSpec {
    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

    def "can run tasks via tooling API when configuration cache is enabled"() {
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        configurationCacheRun("assemble")

        then:
        outputContains("script log statement")

        when:
        configurationCacheRun("assemble")

        then:
        outputDoesNotContain("script log statement")
    }

    def "can enable configuration cache using gradle property in gradle.properties"() {
        withConfigurationCacheEnabledInGradleProperties()
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        run("assemble")

        then:
        outputContains("script log statement")

        when:
        run("assemble")

        then:
        outputDoesNotContain("script log statement")
    }

    def "can enable configuration cache using system property in build arguments"() {
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        run("assemble", ENABLE_SYS_PROP)

        then:
        outputContains("script log statement")

        when:
        run("assemble", ENABLE_SYS_PROP)

        then:
        outputDoesNotContain("script log statement")
    }

    def "can enable configuration cache using system property in build JVM arguments"() {
        buildFile << """
            plugins {
                id("java")
            }
            println("script log statement")
        """

        when:
        toolingApiExecutor.withToolingApiJvmArgs(ENABLE_SYS_PROP)
        run("assemble")

        then:
        outputContains("script log statement")

        when:
        toolingApiExecutor.withToolingApiJvmArgs(ENABLE_SYS_PROP)
        run("assemble")

        then:
        outputDoesNotContain("script log statement")
    }

    def "can use test launcher tooling api"() {

        given:
        withConfigurationCacheEnabledInGradleProperties()
        settingsFile << ""
        buildFile << """
            plugins {
                id("java")
            }
            ${mavenCentralRepository()}
            dependencies { testImplementation("junit:junit:4.13") }
            println("script log statement")
        """
        file("src/test/java/my/MyTest.java") << """
            package my;
            import org.junit.Test;
            public class MyTest {
                @Test public void test() {}
            }
        """

        when:
        runTestClasses("my.MyTest")

        then:
        outputContains("script log statement")

        when:
        runTestClasses("my.MyTest")

        then:
        outputDoesNotContain("script log statement")
    }

    def "configuration cache is disabled for direct model requests"() {
        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        when:
        def model = fetchModel()

        then:
        model.message == "It works from project :"
        outputContains("script log statement")

        when:
        def model2 = fetchModel()

        then:
        model2.message == "It works from project :"
        outputContains("script log statement")
    }

    def "configuration cache is disabled for client provided build actions"() {
        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        when:
        def model = runBuildAction(new SomeToolingModelBuildAction())

        then:
        model.message == "It works from project :"
        outputContains("script log statement")

        when:
        def model2 = runBuildAction(new SomeToolingModelBuildAction())

        then:
        model2.message == "It works from project :"
        outputContains("script log statement")
    }

    def "configuration cache is disabled for client provided phased build actions"() {
        given:
        withConfigurationCacheEnabledInGradleProperties()
        buildWithSomeToolingModelAndScriptLogStatement()

        when:
        def model = runPhasedBuildAction(new SomeToolingModelBuildAction(), new SomeToolingModelBuildAction())

        then:
        model.left.message == "It works from project :"
        model.right.message == "It works from project :"
        outputContains("script log statement")

        when:
        def model2 = runPhasedBuildAction(new SomeToolingModelBuildAction(), new SomeToolingModelBuildAction())

        then:
        model2.left.message == "It works from project :"
        model2.right.message == "It works from project :"
        outputContains("script log statement")
    }

    private void withConfigurationCacheEnabledInGradleProperties() {
        file("gradle.properties").text = ENABLE_GRADLE_PROP
    }

    private void buildWithSomeToolingModelAndScriptLogStatement() {
        withSomeToolingModelBuilderPluginInBuildSrc()
        settingsFile << ""
        buildFile << """
            plugins {
                id("java")
            }
            plugins.apply(my.MyPlugin)
            println("script log statement")
        """
    }
}
