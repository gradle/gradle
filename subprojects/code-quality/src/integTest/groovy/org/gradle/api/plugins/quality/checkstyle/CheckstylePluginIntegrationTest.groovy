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
package org.gradle.api.plugins.quality.checkstyle

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import spock.lang.Issue
import spock.lang.See

import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.containsClass
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.javaClassWithNewLineAtEnd
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.multilineJavaClass
import static org.gradle.api.plugins.quality.checkstyle.CheckstylePluginMultiProjectTest.simpleCheckStyleConfig

class CheckstylePluginIntegrationTest extends WellBehavedPluginTest {
    @Override
    String getMainTask() {
        return "check"
    }

    /**
     * To ensure the plugins fails (as expected) with configuration cache, do NOT add a repository to the build here,
     * the tests in the base class are relying on a failure during eager dependency resolution with CC.
     */
    def setup() {
        buildFile << """
            apply plugin: 'groovy'
        """
    }


    @Issue("https://github.com/gradle/gradle/issues/21301")
    def "can pass a URL in configProperties"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}

            dependencies { implementation localGroovy() }

            checkstyle {
                configProperties["some"] = new URL("https://gradle.org/")
            }
        """

        file('src/main/java/Dummy.java') << javaClassWithNewLineAtEnd()
        file('config/checkstyle/checkstyle.xml') << simpleCheckStyleConfig()

        when:
        succeeds 'check'

        then:
        executedAndNotSkipped ':checkstyleMain'
    }

    // region: Enable External DTDs
    @Issue("https://github.com/gradle/gradle/issues/21624")
    @See("https://checkstyle.sourceforge.io/config_system_properties.html#Enable_External_DTD_load")
    def "can use enable_external_dtd_load feature on extension"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}

            checkstyle {
                enableExternalDtdLoad = true
            }
        """

        file('src/main/java/org/sample/MyClass.java') << multilineJavaClass()
        file('config/checkstyle/checkstyle-common.xml') << checkStyleCommonXml()
        file('config/checkstyle/checkstyle.xml') << checkStyleMainXml()

        when:
        fails 'checkstyleMain'

        then:
        result.assertHasErrorOutput("Checkstyle rule violations were found. See the report at:")
        result.assertHasErrorOutput("Checkstyle files with violations: 1")
        result.assertHasErrorOutput("Checkstyle violations by severity: [error:2]")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.sample.MyClass"))
    }

    @Issue("https://github.com/gradle/gradle/issues/21624")
    @See("https://checkstyle.sourceforge.io/config_system_properties.html#Enable_External_DTD_load")
    def "can use enable_external_dtd_load feature on task"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}

            tasks.withType(Checkstyle).configureEach {
                enableExternalDtdLoad = true
            }
        """

        file('src/main/java/org/sample/MyClass.java') << multilineJavaClass()
        file('config/checkstyle/checkstyle-common.xml') << checkStyleCommonXml()
        file('config/checkstyle/checkstyle.xml') << checkStyleMainXml()

        when:
        fails 'checkstyleMain'

        then:
        result.assertHasErrorOutput("Checkstyle rule violations were found. See the report at:")
        result.assertHasErrorOutput("Checkstyle files with violations: 1")
        result.assertHasErrorOutput("Checkstyle violations by severity: [error:2]")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.sample.MyClass"))
    }

    @Issue("https://github.com/gradle/gradle/issues/21624")
    @See("https://checkstyle.sourceforge.io/config_system_properties.html#Enable_External_DTD_load")
    def "can use enable_external_dtd_load feature on task to override extension value for a task"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}

            checkstyle {
                enableExternalDtdLoad = false
            }

            tasks.withType(Checkstyle).configureEach {
                enableExternalDtdLoad = true
            }
        """

        file('src/main/java/org/sample/MyClass.java') << multilineJavaClass()
        file('config/checkstyle/checkstyle-common.xml') << checkStyleCommonXml()
        file('config/checkstyle/checkstyle.xml') << checkStyleMainXml()

        when:
        fails 'checkstyleMain'

        then:
        result.assertHasErrorOutput("Checkstyle rule violations were found. See the report at:")
        result.assertHasErrorOutput("Checkstyle files with violations: 1")
        result.assertHasErrorOutput("Checkstyle violations by severity: [error:2]")
        file("build/reports/checkstyle/main.xml").assertContents(containsClass("org.sample.MyClass"))
    }

    @Issue("https://github.com/gradle/gradle/issues/21624")
    @See("https://checkstyle.sourceforge.io/config_system_properties.html#Enable_External_DTD_load")
    def "if use enable_external_dtd_load feature NOT enabled, error if feature used in rules XML"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}

            checkstyle {
                enableExternalDtdLoad = false
            }
        """

        file('src/main/java/org/sample/MyClass.java') << multilineJavaClass()
        file('config/checkstyle/checkstyle-common.xml') << checkStyleCommonXml()
        file('config/checkstyle/checkstyle.xml') << checkStyleMainXml()

        when:
        fails 'checkstyleMain'

        then:
        failedDueToXmlDTDProcessingError()
    }

    @Issue("https://github.com/gradle/gradle/issues/21624")
    @See("https://checkstyle.sourceforge.io/config_system_properties.html#Enable_External_DTD_load")
    def "enable_external_dtd_load feature NOT enabled by default"() {
        given:
        buildFile """
            apply plugin: 'checkstyle'

            ${mavenCentralRepository()}
        """

        file('src/main/java/org/sample/MyClass.java') << multilineJavaClass()
        file('config/checkstyle/checkstyle-common.xml') << checkStyleCommonXml()
        file('config/checkstyle/checkstyle.xml') << checkStyleMainXml()

        when:
        fails 'checkstyleMain'

        then:
        failedDueToXmlDTDProcessingError()
    }

    private failedDueToXmlDTDProcessingError() {
        result.assertHasErrorOutput("A failure occurred while executing org.gradle.api.plugins.quality.internal.CheckstyleAction")
        result.assertHasErrorOutput("java.lang.NullPointerException")
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
    // endregion: Enable External DTDs
}
