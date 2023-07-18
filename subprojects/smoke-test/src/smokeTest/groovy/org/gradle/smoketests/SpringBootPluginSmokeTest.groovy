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


import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@Requires(UnitTestPreconditions.Jdk17OrLater)
class SpringBootPluginSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {
    @Issue('https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-gradle-plugin')
    def 'spring boot plugin'() {
        given:
        buildFile << """
            plugins {
                id "application"
                id "org.springframework.boot" version "${TestedVersions.springBoot}" // TODO:Finalize Upload Removal - Issue #21439
                id "io.spring.dependency-management" version "${TestedVersions.springDependencyManagement}"
            }

            ${mavenCentralRepository()}

            application {
                applicationDefaultJvmArgs = ['-DFOO=42']
            }

            dependencies {
                implementation 'org.springframework.boot:spring-boot-starter'
            }

            testing.suites.test {
                useJUnitJupiter()
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-test'
                }
            }
        """.stripIndent()

        file('src/main/java/example/Application.java') << """
            package example;

            import org.springframework.boot.SpringApplication;
            import org.springframework.boot.autoconfigure.SpringBootApplication;

            @SpringBootApplication
            public class Application {
                public static void main(String[] args) {
                    SpringApplication.run(Application.class, args);
                    System.out.println("FOO: " + System.getProperty("FOO"));
                }
            }
        """.stripIndent()
        file("src/test/java/example/ApplicationTest.java") << """
            package example;

            import org.junit.jupiter.api.Test;
            import org.springframework.boot.test.context.SpringBootTest;

            @SpringBootTest
            class ApplicationTest {
                @Test
                void contextLoads() {
                }
            }
        """

        when:
        def smokeTestRunner = runner('assembleBootDist', 'check')
        // verified manually: the 3.0.2 version of Spring Boot plugin removed the deprecated API usage
        def buildResult = smokeTestRunner.build()

        then:
        buildResult.task(':assembleBootDist').outcome == SUCCESS
        buildResult.task(':check').outcome == SUCCESS

        when:
        smokeTestRunner = runner('bootRun')
        def runResult = smokeTestRunner.build()

        then:
        runResult.task(':bootRun').outcome == SUCCESS
        runResult.output.contains("FOO: 42")
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'org.springframework.boot': Versions.of(TestedVersions.springBoot)
        ]
    }
}
