/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import org.gradle.test.fixtures.file.TestFile

class ResolvedGeneratedJarsIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        buildFile << testablePluginProject(applyJavaPlugin())
    }

    def "gradle api jar is generated only when requested"() {
        setup:
        productionCode()

        when:
        run("tasks")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars").assertIsEmptyDir()

        when:
        succeeds("classes")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars/gradle-api-${distribution.version.version}.jar").assertExists()
        assertSingleGenerationOutput(output, API_JAR_GENERATION_OUTPUT_REGEX)

    }

    def "gradle testkit jar is generated only when requested"() {
        setup:
        testCode()

        when:
        succeeds("classes")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars").assertIsEmptyDir()

        when:
        run("testClasses")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars/gradle-test-kit-${distribution.version.version}.jar").assertExists()
        assertSingleGenerationOutput(output, TESTKIT_GENERATION_OUTPUT_REGEX)
    }

    private TestFile productionCode() {
        file("src/main/java/org/acme/TestPlugin.java") << """
        package org.acme;
        import org.gradle.api.Project;
        import org.gradle.api.Plugin;

        public class TestPlugin implements Plugin<Project> {
            public void apply(Project p) {}
        }
        """
    }

    private TestFile testCode() {
        file("src/test/java/org/acme/BaseTestPluginTest.java") << """
        package org.acme;
        import org.gradle.testkit.runner.GradleRunner;
        import org.gradle.testkit.runner.BuildResult;
        import org.junit.Rule;
        import org.junit.rules.TemporaryFolder;
        
        public abstract class BaseTestPluginTest {
            @Rule public final TemporaryFolder testProjectDir = new TemporaryFolder();

            void run(String task) {
            
                BuildResult result = GradleRunner.create()
                    .withProjectDir(testProjectDir.getRoot())
                    .withArguments(task)
                    .build();
            }
        
        }
        """
    }

}
