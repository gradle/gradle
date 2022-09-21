/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.See

import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.containsClass
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.multilineJavaClass

@Issue("https://github.com/gradle/gradle/issues/21624")
@See("https://checkstyle.sourceforge.io/config_system_properties.html#Enable_External_DTD_load")
class CheckstylePluginExternalDTDOptionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'java'
                id 'checkstyle'
            }

            ${mavenCentralRepository()}
        """

        file('src/main/java/org/sample/MyClass.java') << multilineJavaClass()
        file('config/checkstyle/checkstyle-common.xml') << checkStyleCommonXml()
        file('config/checkstyle/checkstyle.xml') << checkStyleMainXml()
    }

    def "can use enable_external_dtd_load feature on extension"() {
        given:
        buildFile """
            checkstyle {
                enableExternalDtdLoad = true
            }
        """

        when:
        fails 'checkstyleMain'

        then:
        assertFailedWithCheckstyleVerificationErrors()
    }

    def "can use enable_external_dtd_load feature on task"() {
        given:
        buildFile """
            tasks.withType(Checkstyle).configureEach {
                enableExternalDtdLoad = true
            }
        """

        when:
        fails 'checkstyleMain'

        then:
        assertFailedWithCheckstyleVerificationErrors()
    }

    def "can use enable_external_dtd_load feature on task to override extension value for a task"() {
        given:
        buildFile """
            checkstyle {
                enableExternalDtdLoad = false
            }

            tasks.withType(Checkstyle).configureEach {
                enableExternalDtdLoad = true
            }
        """

        when:
        fails 'checkstyleMain'

        then:
        assertFailedWithCheckstyleVerificationErrors()
    }

    def "if use enable_external_dtd_load feature NOT enabled, error if feature used in rules XML"() {
        given:
        buildFile """
            checkstyle {
                enableExternalDtdLoad = false
            }
        """

        when:
        fails 'checkstyleMain'

        then:
        assertFailedWithDtdProcessingError()
    }

    def "enable_external_dtd_load feature NOT enabled by default"() {
        when:
        fails 'checkstyleMain'

        then:
        assertFailedWithDtdProcessingError()
    }

    private void assertFailedWithCheckstyleVerificationErrors() {
        result.assertHasErrorOutput("Checkstyle rule violations were found. See the report at:")
        result.assertHasErrorOutput("Checkstyle files with violations: 1")
        result.assertHasErrorOutput("Checkstyle violations by severity: [error:2]")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.sample.MyClass"))
    }

    private void assertFailedWithDtdProcessingError(String taskName = 'checkstyleMain') {
        failure.assertHasCause("A failure occurred while executing org.gradle.api.plugins.quality.internal.CheckstyleAction")
        failure.assertHasCause("An unexpected error occurred configuring and executing Checkstyle.")
        failure.assertHasCause("java.lang.NullPointerException")
    }

    private String checkStyleCommonXml() {
        return """
            <module name="FileLength">
                <property name="max" value="1"/>
            </module>
            """
    }

    private String checkStyleMainXml() {
        // Leave the XML processing instruction at the very first position in the file or else the XML is not valid
        return """<?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                      "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                      "https://checkstyle.org/dtds/configuration_1_3.dtd" [
                <!ENTITY common SYSTEM "checkstyle-common.xml">
            ]>
            <module name="Checker">

                &common;

                <module name="TreeWalker">
                    <module name="MemberName">
                        <property name="format" value="^[a-z][a-zA-Z]+\$"/>
                    </module>
                </module>

            </module>
            """
    }
}
