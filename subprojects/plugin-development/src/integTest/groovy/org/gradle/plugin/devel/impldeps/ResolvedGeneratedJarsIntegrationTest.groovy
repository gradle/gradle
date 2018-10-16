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
        succeeds("tasks")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars").assertIsEmptyDir()

        when:
        succeeds("classes")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars/gradle-api-${distribution.version.version}.jar").assertExists()

    }

    def "gradle testkit jar is generated only when requested"() {
        setup:
        testCode()

        when:
        succeeds("classes")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars").assertIsEmptyDir()

        when:
        succeeds("testClasses")

        then:
        file("user-home/caches/${distribution.version.version}/generated-gradle-jars/gradle-test-kit-${distribution.version.version}.jar").assertExists()
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
        import org.junit.Test;
        import static org.junit.Assert.assertTrue;
        
        public abstract class BaseTestPluginTest {
            GradleRunner runner() {
                return GradleRunner.create();
            }

            @Test 
            void commonTest() {
                assertTrue(true);
            }         
        }
        """
    }

}
