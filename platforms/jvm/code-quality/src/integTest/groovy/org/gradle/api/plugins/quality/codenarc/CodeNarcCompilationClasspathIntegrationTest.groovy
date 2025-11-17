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

package org.gradle.api.plugins.quality.codenarc

import com.google.common.collect.Range
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.junit.Assume
import spock.lang.Issue

class CodeNarcCompilationClasspathIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    private final static String CONFIG_FILE_PATH = 'config/codenarc/rulesets.groovy'

    // TODO: We should merge these versions into CodeNarcCoverage
    private final static String MIN_SUPPORTED_COMPILATION_CLASSPATH_VERSION = '0.27.0'
    private final static String UNSUPPORTED_COMPILATION_CLASSPATH_VERSION = '0.26.0'

    private static String supportedCompilationClasspathVersion() {
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            return "1.6"
        }
        return MIN_SUPPORTED_COMPILATION_CLASSPATH_VERSION
    }

    def "compilation classpath can be specified for a CodeNarc task"() {
        given:
        buildFileWithCodeNarcAndCompilationClasspath(supportedCompilationClasspathVersion())
        cloneWithoutCloneableRuleEnabled()
        codeViolatingCloneWithoutCloneableRule()

        when:
        fails("codenarcMain")

        then:
        failure.assertHasCause('CodeNarc rule violations were found')
    }

    def "an informative error is shown when a compilation classpath is specified on a CodeNarc task when using an incompatible CodeNarc version"() {
        def jvm = AvailableJavaHomes.getJdkInRange(Range.atMost(15)) // UNSUPPORTED_COMPILATION_CLASSPATH_VERSION does not support JVM < 16
        Assume.assumeNotNull(jvm)
        withInstallations(jvm)

        given:
        buildFileWithCodeNarcAndCompilationClasspath(UNSUPPORTED_COMPILATION_CLASSPATH_VERSION)
        cloneWithoutCloneableRuleEnabled()
        codeViolatingCloneWithoutCloneableRule()
        buildFile << javaPluginToolchainVersion(jvm)

        when:
        fails("codenarcMain")

        then:
        failure.assertHasCause("The compilationClasspath property of CodeNarc task can only be non-empty when using CodeNarc $MIN_SUPPORTED_COMPILATION_CLASSPATH_VERSION or newer.")
    }

    @Issue("https://github.com/gradle/gradle/issues/35494")
    def "automatically adds the source set compile classpath to the compilationClasspath on CodeNarc task"() {
        // Groovy 4 includes a shadowed version of ASM that can only read up to Java 24 class files
        def jvm = AvailableJavaHomes.getJdkInRange(Range.atMost(24))
        withInstallations(jvm)
        println "Using JDK ${jvm.javaVersionMajor} at ${jvm.javaHome}"

        given:
        buildFile << """
            plugins {
                id 'codenarc'
                id 'groovy'
                id 'jvm-test-suite'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation 'org.spockframework:spock-core:2.4-M6-groovy-4.0'
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${jvm.javaVersionMajor})
                }
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
        """
        file("src/test/groovy/UsesAnnotationTest.groovy") << """
            import spock.lang.Shared
            import spock.lang.Specification

            class UsesAnnotationTest extends Specification {

                @Shared
                Object object = new Object()

                void 'a test'() {
                    expect:
                    true
                }

            }
        """
        file("config/codenarc/codenarc.xml") << """
            <ruleset xmlns="http://codenarc.org/ruleset/1.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://codenarc.org/ruleset/1.0 http://codenarc.org/ruleset-schema.xsd"
                     xsi:noNamespaceSchemaLocation="http://codenarc.org/ruleset-schema.xsd">

                <ruleset-ref path='rulesets/basic.xml'/>
                <ruleset-ref path='rulesets/serialization.xml'/>

            </ruleset>
        """

        when:
        succeeds("check")

        then:
        outputDoesNotContain("WARNING: Compilation error for non-default compiler phase (semantic analysis). Consider removing \"enhanced\" rules from your ruleset.")
    }

    private void buildFileWithCodeNarcAndCompilationClasspath(String codeNarcVersion) {
        buildFile << """
            plugins {
                id("groovy")
                id("codenarc")
            }

            ${mavenCentralRepository()}

            codenarc {
                toolVersion = '$codeNarcVersion'
                codenarc.configFile = file('$CONFIG_FILE_PATH')
            }

            dependencies {
                implementation localGroovy()
            }

            codenarcMain {
                compilationClasspath = configurations.compileClasspath
            }
        """
    }

    private void cloneWithoutCloneableRuleEnabled() {
        file(CONFIG_FILE_PATH) << '''
            ruleset {
                CloneWithoutCloneable
            }
        '''
    }

    private void codeViolatingCloneWithoutCloneableRule() {
        file('src/main/groovy/ViolatingClass.groovy') << '''
            class ViolatingClass {
                ViolatingClass clone() {}
            }
        '''
    }
}
