/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.smoketests


import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.os.OperatingSystem
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class ProtobufPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    // https://central.sonatype.com/artifact/com.google.protobuf/protobuf-java/versions
    private static protobufToolsVersion = "4.31.1"

    // The protobuf plugin calls the deprecated Configuration.setVisible(boolean) method.
    // The deprecation warning is expected until https://github.com/google/protobuf-gradle-plugin/issues/815 is fixed
    private static final String PROTOBUF_SET_VISIBLE_FOLLOWUP = "https://github.com/google/protobuf-gradle-plugin/issues/815"
    private static final String SET_VISIBLE_DEPRECATION =
        "The Configuration.setVisible(boolean) method has been deprecated. " +
            "This is scheduled to be removed in Gradle 10. " +
            "Consult the upgrading guide for further information: " +
            "${DOCS.getDocumentationFor("upgrading_version_9", "deprecate-visible-property")}"

    @Issue("https://plugins.gradle.org/plugin/com.google.protobuf")
    def "protobuf plugin"() {
        given:
        buildFile << """
            plugins {
                id('java')
                id("com.google.protobuf") version "${TestedVersions.protobufPlugin}"
            }

            ${mavenCentralRepository()}

            protobuf {
                protoc {
                    artifact = "com.google.protobuf:protoc:$protobufToolsVersion"
                }
            }
            dependencies {
                implementation "com.google.protobuf:protobuf-java:$protobufToolsVersion"
            }
        """

        and:
        file("src/main/proto/sample.proto") << """
            syntax = "proto3";
            option java_package = "my.proto";
            option java_multiple_files = true;
            message Msg {
                string text = 1;
            }
        """
        file("src/main/java/my/Sample.java") << """
            package my;
            import my.proto.Msg;
            public class Sample {}
        """

        String classifier = getDeprecationClassifier()

        when:
        def result = runner('compileJava')
            .expectDeprecationWarning(SET_VISIBLE_DEPRECATION, PROTOBUF_SET_VISIBLE_FOLLOWUP)
            .build()

        then:
        result.task(":generateProto").outcome == SUCCESS
        result.task(":compileJava").outcome == SUCCESS

        when:
        // The second invocation is a configuration cache hit, so configuration (and the nag) does not re-run
        result = runner('compileJava')
            .expectDeprecationWarningIf(GradleContextualExecuter.notConfigCache, SET_VISIBLE_DEPRECATION, PROTOBUF_SET_VISIBLE_FOLLOWUP)
            .build()

        then:
        result.task(":generateProto").outcome == UP_TO_DATE
        result.task(":compileJava").outcome == UP_TO_DATE
    }

    private static String getDeprecationClassifier() {
        String classifier
        if (OperatingSystem.current().isWindows()) {
            classifier = "windows-x86_64"
        } else if (OperatingSystem.current().isLinux()) {
            classifier = "linux-x86_64"
        } else if (OperatingSystem.current().isMacOsX()) {
            classifier = "osx-aarch_64"
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: ${OperatingSystem.current().name}")
        }
        classifier
    }

    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            'com.google.protobuf': Versions.of(TestedVersions.protobufPlugin)
        ]
    }

    @Override
    Map<String, String> getExtraPluginsRequiredForValidation() {
        ['java': '']
    }

    @Override
    String getSubprojectExtensionAccess(String testedPluginId, String version) {
        "protobuf {}"
    }

    @Override
    List<String> getSubprojectExtensionDeprecations(String testedPluginId, String version) {
        [parentMethodInvocationDeprecation('protobuf'), SET_VISIBLE_DEPRECATION]
    }
}
