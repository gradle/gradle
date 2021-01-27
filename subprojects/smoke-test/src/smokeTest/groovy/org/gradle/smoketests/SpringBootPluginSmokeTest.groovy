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

package org.gradle.smoketests


import spock.lang.Issue

import static org.gradle.internal.reflect.TypeValidationContext.Severity.WARNING
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class SpringBootPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    @Issue('https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin')
    def 'spring boot plugin'() {
        given:
        buildFile << """
            plugins {
                id "application"
                id "org.springframework.boot" version "${TestedVersions.springBoot}"
            }

            bootRun {
                sourceResources sourceSets.main
            }
        """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            public class Application {
                public static void main(String[] args) {}
            }
        """.stripIndent()

        when:
        def buildResult = runner('assembleBootDist', 'check').build()

        then:
        buildResult.task(':assembleBootDist').outcome == SUCCESS
        buildResult.task(':check').outcome == UP_TO_DATE // no tests
        expectNoDeprecationWarnings(buildResult)

        when:
        def runResult = runner('bootRun').build()

        then:
        runResult.task(':bootRun').outcome == SUCCESS

        expectNoDeprecationWarnings(runResult)
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.springframework.boot': Versions.of(TestedVersions.springBoot)
        ]
    }

    @Override
    void configureValidation(String pluginId, String version) {
        validatePlugins {
            onPlugin(pluginId) {
                failsWith "Type 'CreateBootStartScripts': property 'mainClassName' is annotated with @Optional that is not allowed for @ReplacedBy properties.", WARNING
            }
        }
    }
}
