/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.cc.impl.promo

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.cc.impl.AbstractConfigurationCacheIntegrationTest
import org.gradle.internal.cc.impl.fixtures.SomeToolingModel
import org.gradle.internal.cc.impl.fixtures.ToolingApiBackedGradleExecuter
import org.gradle.internal.cc.impl.fixtures.ToolingApiSpec

import static org.gradle.integtests.fixtures.logging.ConfigurationCacheOutputNormalizer.PROMO_PREFIX

class ConfigurationCacheToolingApiPromoIntegrationTest extends AbstractConfigurationCacheIntegrationTest implements ToolingApiSpec {
    @Override
    GradleExecuter createExecuter() {
        return new ToolingApiBackedGradleExecuter(distribution, temporaryFolder)
    }

    def setup() {
        settingsFile "" // The build must have a settings file for TAPI to work.
    }

    def "shows promo message when running tasks through tooling API"() {
        buildFile """
            plugins {
                id("java")
            }
        """

        when:
        run("assemble")

        then:
        postBuildOutputContains(PROMO_PREFIX)
    }

    def "shows promo message when running tests through tooling API"() {
        given:
        buildFile """
            plugins {
                id("java")
            }
            ${mavenCentralRepository()}
            dependencies { testImplementation("junit:junit:4.13") }
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
        postBuildOutputContains(PROMO_PREFIX)
    }

    def "shows no promo message when building model"() {
        given:
        withModelBuilderPlugin()

        when:
        fetchModel()

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)
    }

    def "shows no promo message when building model and running tasks"() {
        given:
        withModelBuilderPlugin()

        when:
        fetchModel(SomeToolingModel, "assemble")

        then:
        postBuildOutputDoesNotContain(PROMO_PREFIX)
    }

    private void withModelBuilderPlugin() {
        withSomeToolingModelBuilderPluginInBuildSrc()
        buildFile """
            plugins {
                id("java")
            }
            plugins.apply(my.MyPlugin)
        """
    }
}
