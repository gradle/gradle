/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.GradleVersion
import spock.lang.Issue

import static org.gradle.util.internal.TextUtil.normaliseFileSeparators

// TODO: This needs a better home - Possibly in the test kit package in the future
@Issue("https://github.com/gradle/gradle-private/issues/3247")
// The gradleApi() dependency has missing JARs unless the test is run with the full Gradle distribution
@Requires([IntegTestPreconditions.NotEmbeddedExecutor, UnitTestPreconditions.NotJava8OnMacOs])
class ApplyPluginIntegSpec extends AbstractIntegrationSpec {

    def testProjectPath

    def setup() {
        testProjectPath = normalisedPathOf(file("test-project-dir"))
    }

    @Issue("GRADLE-2358")
    def "can reference plugin by id in unit test"() {

        given:
        file("src/main/groovy/org/acme/TestPlugin.groovy") << """
            package org.acme
            import org.gradle.api.*

            class TestPlugin implements Plugin<Project> {
                void apply(Project project) {
                    println "testplugin applied"
                }
            }
        """

        file("src/main/resources/META-INF/gradle-plugins/testplugin.properties") << "implementation-class=org.acme.TestPlugin"

        file("src/test/groovy/org/acme/TestPluginSpec.groovy") << """
            import spock.lang.Specification
            import ${ProjectBuilder.name}
            import ${Project.name}
            import org.acme.TestPlugin

            class TestPluginSpec extends Specification {
                def "can apply plugin by id"() {
                    when:
                    def projectDir = new File('${testProjectPath}')
                    Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
                    project.apply(plugin: "testplugin")

                    then:
                    project.plugins.withType(TestPlugin).size() == 1
                }
            }
        """

        and:
        buildFile << spockBasedBuildScript()
        buildFile << addOpens()

        expect:
        succeeds("test")
    }

    @Issue("GRADLE-3068")
    def "can use gradleApi in test"() {
        given:
        file("src/test/groovy/org/acme/ProjectBuilderTest.groovy") << """
            package org.acme
            import org.gradle.api.Project
            import org.gradle.testfixtures.ProjectBuilder
            import org.junit.Test

            class ProjectBuilderTest {
                @Test
                void "can evaluate ProjectBuilder"() {
                    def projectDir = new File('${testProjectPath}')
                    def userHome = new File('${gradleUserHome}')
                    def project = ProjectBuilder.builder()
                                    .withProjectDir(projectDir)
                                    .withGradleUserHomeDir(userHome)
                                    .build()
                    project.apply(plugin: 'groovy')
                    project.evaluate()
                }
            }
        """

        and:
        buildFile << junitBasedBuildScript()
        buildFile << addOpens()

        expect:
        succeeds("test")
    }

    def "generated Gradle API JAR in custom Gradle user home is reused across multiple invocations"() {
        requireOwnGradleUserHomeDir()

        given:
        file("src/test/groovy/org/acme/ProjectBuilderTest.groovy") << """
            package org.acme
            import org.gradle.api.Project
            import org.gradle.testfixtures.ProjectBuilder
            import spock.lang.Specification

            class ProjectBuilderTest extends Specification {
                def "generates Gradle API JAR and reuses it"() {
                    given:
                    def gradleVersion = '${GradleVersion.current().getVersion()}'
                    def gradleUserHome = new File('${gradleUserHome}')
                    def generatedGradleJarCacheDir = new File(gradleUserHome, "caches/\$gradleVersion/generated-gradle-jars")
                    def gradleApiJar = new File(generatedGradleJarCacheDir, "gradle-api-\${gradleVersion}.jar")

                    when:
                    def project = createProject(gradleUserHome)

                    then:
                    gradleApiJar.exists()
                    long lastModified = gradleApiJar.lastModified()

                    when:
                    project = createProject(gradleUserHome)

                    then:
                    gradleApiJar.exists()
                    lastModified == gradleApiJar.lastModified()
                }

                static Project createProject(File gradleUserHome) {
                    def project = ProjectBuilder.builder().withGradleUserHomeDir(gradleUserHome).build()
                    project.plugins.apply('java')
                    project.dependencies.add('implementation', project.dependencies.gradleApi())
                    project
                }
            }
        """

        and:
        buildFile << spockBasedBuildScript()
        buildFile << addOpens()

        expect:
        succeeds('test')
    }

    static String junitBasedBuildScript() {
        """
            ${basicBuildScript()}

            dependencies {
                testImplementation  'junit:junit:4.13'
            }
        """
    }

    static String spockBasedBuildScript() {
        """
            ${basicBuildScript()}

            configurations.all { exclude group: 'org.codehaus.groovy' }

            testing {
                suites {
                    test {
                        useSpock()
                    }
                }
            }
        """
    }

    /**
     * Configures tests to include open access to java.base/java.lang modules
     * ProjectBuilder usages will fail without this
     */
    static String addOpens() {
        """
        // Needed when using ProjectBuilder
        class AddOpensArgProvider implements CommandLineArgumentProvider {
            private final Test test;
            public AddOpensArgProvider(Test test) {
                this.test = test;
            }
            @Override
            Iterable<String> asArguments() {
                return test.javaVersion.isCompatibleWith(JavaVersion.VERSION_1_9)
                    ? ["--add-opens=java.base/java.lang=ALL-UNNAMED"]
                    : []
            }
        }
        tasks.withType(Test).configureEach {
            jvmArgumentProviders.add(new AddOpensArgProvider(it))
        }
        """
    }

    static String basicBuildScript() {
        """
            apply plugin: 'groovy'

            ${mavenCentralRepository()}

            dependencies {
                implementation gradleApi()
                implementation localGroovy()
            }
        """
    }

    private String getGradleUserHome() {
        normalisedPathOf(executer.gradleUserHomeDir)
    }

    static String normalisedPathOf(File file) {
        normaliseFileSeparators(file.absolutePath)
    }

}
