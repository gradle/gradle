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
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.intellij.lang.annotations.Language

abstract class AbstractPluginFuncTest extends AbstractIntegrationSpec implements TestFrameworkVersionData {

    def setup() {
        executer.withRepositoryMirrors()

        settingsFile << "rootProject.name = 'hello-world'"
        buildFile << baseBuildScript()

        file('src/test/java/acme').mkdirs()
        file('src/test/groovy/acme').mkdirs()
        file('src/test/kotlin/acme').mkdirs()

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

    String baseBuildScript() {
        """
            plugins {
                id '${languagePlugin}'
                id 'org.gradle.test-retry-bundled'
            }

            ${mavenCentralRepository()}

            ${buildConfiguration()}

            tasks.named("test").configure {
                testLogging {
                    events "passed", "skipped", "failed"
                }
            }
        """
    }

    abstract String getLanguagePlugin()

    String baseBuildScriptWithoutPlugin() {
        baseBuildScript() - "id 'org.gradle.test-retry-bundled'"
    }

    String baseBuildScriptWithNotAppliedTestRetryPlugin() {
        baseBuildScript().replace("id 'org.gradle.test-retry-bundled'", "id 'org.gradle.test-retry-bundled' apply false")
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

    void writeTestSource(String source, String language, String extension) {
        def packageMatch = (source =~ /package\s+([\w.]+)/)
        def classMatch = (source =~ /(class|interface)\s+(\w+)\s+/)
        String packageName = packageMatch[0][1]
        String className = classMatch[0][2]
        String sourceFilePackage = "src/test/$language/${packageName.replace('.', '/')}"
        file("$sourceFilePackage/${className}.$extension") << source
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

    String reportedTestName(String testName) {
        testName
    }

    boolean assertTestReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        assertXmlReportContains(testClazz, testName, expectedSuccessCount, expectedFailCount)
        true
    }

    boolean assertXmlReportContains(String testClazz, String testName, int expectedSuccessCount, int expectedFailCount) {
        def xml = new XmlSlurper().parse(file("build/test-results/test/TEST-acme.${testClazz}.xml"))
        // assert summary
        xml.'**'.find { it.name() == 'testsuite' && it.@name == "acme.${testClazz}" && it.@tests == "${expectedFailCount + expectedSuccessCount}" }

        // assert details
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && it.@name == testName }
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && !it.failure.isEmpty() }.size() == expectedFailCount
        assert xml.'**'.findAll { it.name() == 'testcase' && it.@classname == "acme.${testClazz}" && it.failure.isEmpty() }.size() == expectedSuccessCount
        true
    }
}
