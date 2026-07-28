/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry

import groovy.json.StringEscapeUtils
import groovy.xml.XmlSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.GradleVersion
import org.gradle.util.internal.VersionNumber
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.lang.management.ManagementFactory

import static java.util.Arrays.asList

abstract class AbstractPluginFuncTest extends Specification implements TestFrameworkVersionData {

    public static final List<String> GRADLE_VERSIONS_UNDER_TEST = gradleVersionsUnderTest()

    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder()

    File gradleProperties

    File settingsFile

    File buildFile

    def setup() {
        gradleProperties = testProjectDir.newFile('gradle.properties')
        testJavaToolchainVersion().ifPresent { major ->
            // When a specific Java version is specified that tests should configure,
            // we have to configure the build to allow finding the version via an env variable.
            def expectedEnvVar = "JDK${major}"
            assert (System.getenv(expectedEnvVar) != null) : "Test toolchain version is configured but env var ${expectedEnvVar} is missing!"

            gradleProperties << "org.gradle.java.installations.fromEnv=${expectedEnvVar}"
        }

        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << "rootProject.name = 'hello-world'"
        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << baseBuildScript()

        testProjectDir.newFolder('src', 'test', 'java', 'acme')
        testProjectDir.newFolder('src', 'test', 'groovy', 'acme')
        testProjectDir.newFolder('src', 'test', 'kotlin', 'acme')

        writeJavaTestSource flakyAssertClass()
    }

    String markerFileExistsCheck(String id = "id") {
        """Files.exists(Paths.get("build/marker.file.${StringEscapeUtils.escapeJava(id)}"))"""
    }

    String flakyAssertClass() {
        """
            package acme;

            import java.nio.file.*;

            public class FlakyAssert {
                public static void flakyAssert(String id, int failures) {
                    Path marker = Paths.get("build/marker.file." + id);
                    try {
                        if (Files.exists(marker)) {
                            int counter = Integer.parseInt(new String(Files.readAllBytes(marker)));
                            if (++counter == failures) {
                                return;
                            }
                            Files.write(marker, Integer.toString(counter).getBytes());
                        } else {
                            Files.write(marker, "0".getBytes());
                        }
                        throw new RuntimeException("fail me!");
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }

                public static void flakyAssertPassFailPass(String id) {
                    Path marker = Paths.get("build/marker.file." + id);
                    try {
                        if (Files.exists(marker)) {
                            int counter = Integer.parseInt(new String(Files.readAllBytes(marker)));
                            ++counter;
                            Files.write(marker, Integer.toString(counter).getBytes());
                            if (counter == 1) {
                                throw new RuntimeException("fail me!");
                            }
                        } else {
                            Files.write(marker, "0".getBytes());
                        }
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            }
        """
    }

    File file(String path) {
        def file = new File(testProjectDir.root, path)
        assert file.parentFile.mkdirs() || file.parentFile.directory
        assert file.createNewFile() || file.file
        file
    }

    String baseBuildScript() {
        """
            plugins {
                id '${languagePlugin}'
                id 'org.gradle.test-retry'
            }

            repositories {
                mavenCentral()
            }

            ${buildConfiguration()}

            tasks.named("test").configure {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }

            ${jdkConfigurationForTest()}
        """
    }

    String jdkConfigurationForTest() {
        def maybeTestJavaVersion = testJavaToolchainVersion()

        if (maybeTestJavaVersion.present) {
            def testJavaVersion = maybeTestJavaVersion.get()
            println "Test runs with Java ${testJavaVersion}"

            """
                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(${testJavaVersion})
                    }
                }
            """
        } else {
            return ""
        }
   }

    abstract String getLanguagePlugin()

    String baseBuildScriptWithoutPlugin() {
        baseBuildScript() - "id 'org.gradle.test-retry'"
    }

    String baseBuildScriptWithNotAppliedTestRetryPlugin() {
        baseBuildScript().replace("id 'org.gradle.test-retry'", "id 'org.gradle.test-retry' apply false")
    }

    protected String buildConfiguration() {
        return """
            dependencies {
                testImplementation "${junit4Dependency()}"
            }
        """
    }

    static String flakyAssert(String id = "id", int failures = 1) {
        return """acme.FlakyAssert.flakyAssert("${StringEscapeUtils.escapeJava(id)}", $failures);"""
    }

    static String flakyAssertPassFailPass(String id = "id") {
        return """acme.FlakyAssert.flakyAssertPassFailPass("${StringEscapeUtils.escapeJava(id)}");"""
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    void writeTestSource(
        String source,
        String language,
        String extension
    ) {
        String packageName = (source =~ /package\s+([\w.]+)/)[0][1]
        String className = (source =~ /(class|interface)\s+(\w+)\s+/)[0][2]
        String sourceFilePackage = "src/test/$language/${packageName.replace('.', '/')}"
        new File(testProjectDir.root, sourceFilePackage).mkdirs()
        testProjectDir.newFile("$sourceFilePackage/${className}.$extension") << source
    }

    void writeJavaTestSource(@Language("JAVA") String source) {
        writeTestSource(source, 'java', 'java')
    }

    void writeGroovyTestSource(@Language("Groovy") String source) {
        writeTestSource(source, 'groovy', 'groovy')
    }

    void writeKotlinTestSource(@Language("kotlin") String source) {
        writeTestSource(source, 'kotlin', 'kt')
    }

    GradleRunner gradleRunner(String gradleVersion, String... arguments = ['test', '-S']) {
        GradleRunner.create()
            .withDebug(ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0)
            .withProjectDir(testProjectDir.root)
            .withArguments(arguments)
            .withPluginClasspath()
            .forwardOutput()
            .tap {
                gradleVersion == GradleVersion.current().toString() ? null : it.withGradleVersion(gradleVersion)
            }
    }

    def assertTestReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        assertXmlReportContains(testClazz, testName, expectedSuccessCount, expectedFailCount)
        true
    }

    String reportedTestName(String testName) {
        testName
    }

    def assertXmlReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        def xml = new XmlSlurper().parse(new File(testProjectDir.root, "build/test-results/test/TEST-acme.${testClazz}.xml"))
        // assert summary
        xml.'**'.find { it.name() == 'testsuite' && it.@name == "acme.${testClazz}" && it.@tests == "${expectedFailCount + expectedSuccessCount}" }

        // assert details
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && it.@name == testName }
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && !it.failure.isEmpty() }.size() == expectedFailCount
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && it.failure.isEmpty() }.size() == expectedSuccessCount
        true
    }

    /**
     * Returns the Java major version that runs this test class.
     */

    static int javaMajorVersion() {
        def version = System.getProperty("java.version")
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3)) // "1.8" → 8
        }
        return Integer.parseInt(version.split("\\.")[0]) // "17.0.2" → 17
    }

    /**
     * Returns the Java major version to configure builds running in this test class.
     */
    static Optional<Integer> testJavaToolchainVersion() {
        Optional.ofNullable(System.getProperty("testJavaToolchainVersion"))
            .map(s -> s.toInteger())
    }

    /**
     * Returns the Java major version builds running in this test class use.
     */
    static int effectiveTestJavaMajorVersion() {
        testJavaToolchainVersion().orElseGet { javaMajorVersion() }
    }

    static private List<String> gradleVersionsUnderTest() {
        def testJavaVersion = testJavaToolchainVersion()
        def explicitGradleVersions = System.getProperty('org.gradle.test.gradleVersions')
        if (explicitGradleVersions) {
            def gradleVersions = asList(explicitGradleVersions.split("\\|"))

            if (testJavaVersion?.orElse(null) >= 25) {
                // Java 25 is only supported for Gradle 9.1.0 and above
                removedVersionsBefore(9, 1, gradleVersions)
            } else {
                gradleVersions
            }

        } else {
            [GradleVersion.current().toString()]
        }
    }

    static private List<String> removedVersionsBefore(int major, int minor, List<String> versions) {
        versions.findAll {
            def version = VersionNumber.parse(it)
            (version.major == major && version.minor >= minor) || version.major > major
        }
    }
}
