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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class TestTaskDoesntFailWith21DueToValidationProblems extends AbstractIntegrationSpec {

    @Requires(IntegTestPreconditions.NotEmbeddedExecutor)
    def "test succeeds with java 21 and JUnit Jupiter"() {
        given:
        file("src/test/java/ProjectTest.java") << """
        import org.gradle.api.Project;
        import org.gradle.api.internal.project.DefaultProject;
        import org.gradle.testfixtures.ProjectBuilder;
        import org.junit.jupiter.api.Test;

        import java.io.File;

        import static org.junit.jupiter.api.Assertions.assertTrue;

        class ProjectTest {
            @Test
            void testFailure() {
                System.err.println("version " + System.getProperty("java.version"));
                File dir = new File(".").getAbsoluteFile().getParentFile();
                File projectDir = new File(dir + "/src/test/resources/example-project");
                assertTrue(projectDir.exists());
                Project project = ProjectBuilder.builder().withProjectDir(projectDir).build();
                ((DefaultProject) project).evaluate();
            }

        }""".stripIndent()
        file("src/test/resources/example-project/build.gradle") << "plugins { id 'java' }"
        file("src/test/resources/example-project/settings.gradle") << "rootProject.name = 'example-project'"
        buildFile << """
            plugins {
                id 'java-gradle-plugin'
            }

            repositories {
                mavenCentral()
                maven {
                    url = 'https://repo.gradle.org/gradle/libs-releases/'
                }
            }

            dependencies {
                testImplementation(platform('org.junit:junit-bom:5.11.4'))
                testImplementation('org.junit.jupiter:junit-jupiter')
                testRuntimeOnly('org.junit.platform:junit-platform-launcher')
            }

            test {
                useJUnitPlatform()
            }""".stripIndent()
        settingsFile """
            rootProject.name = 'gradle-8-12-problem-reporter-bug'
            """.stripIndent()

        when:
        executer.withJvm(AvailableJavaHomes.getJdk21()).withArgument("--configuration-cache")

        then:
        succeeds("test")
    }
}
