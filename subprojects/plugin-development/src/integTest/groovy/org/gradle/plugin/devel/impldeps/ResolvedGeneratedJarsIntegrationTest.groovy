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
import spock.lang.Issue

import java.util.zip.ZipFile

class ResolvedGeneratedJarsIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        buildFile << testablePluginProject(applyJavaPlugin())
    }

    def "gradle api jar is generated only when requested"() {
        setup:
        productionCode()

        def version = distribution.version.version
        def generatedJarsDirectory = "user-home/caches/$version/generated-gradle-jars"

        when:
        succeeds("tasks")

        then:
        file(generatedJarsDirectory).assertIsEmptyDir()

        when:
        succeeds("classes")

        then:
        file("$generatedJarsDirectory/gradle-api-${version}.jar").assertExists()

    }

    def "gradle testkit jar is generated only when requested"() {
        setup:
        testCode()

        def version = distribution.version.version
        def generatedJarsDirectory = "user-home/caches/$version/generated-gradle-jars"

        when:
        succeeds("classes")

        then:
        file(generatedJarsDirectory).assertIsEmptyDir()

        when:
        succeeds("testClasses")

        then:
        file("$generatedJarsDirectory/gradle-test-kit-${version}.jar").assertExists()
    }

    @Issue(['https://github.com/gradle/gradle/issues/9990', 'https://github.com/gradle/gradle/issues/10038'])
    def "generated jars (api & test-kit) are valid archives"() {
        setup:
        productionCode()
        testCode()

        def version = distribution.version.version
        def generatedJars = [
            'gradle-api',
            'gradle-test-kit'
        ].collect { file("user-home/caches/$version/generated-gradle-jars/${it}-${version}.jar" )}

        when:
        run "classes", "testClasses"

        then:
        generatedJars.findAll {
            new ZipFile(it).withCloseable {
                def names = it.entries()*.name
                names.size() != names.toUnique().size()
            }
        } == []
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
