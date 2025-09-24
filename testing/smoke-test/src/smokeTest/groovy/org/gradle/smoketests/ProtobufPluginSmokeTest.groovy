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
import org.gradle.util.GradleVersion
import spock.lang.Issue

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

class ProtobufPluginSmokeTest extends AbstractPluginValidatingSmokeTest {

    // https://central.sonatype.com/artifact/com.google.protobuf/protobuf-java/versions
    private static protobufToolsVersion = "4.31.1"

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
            // See: https://github.com/google/protobuf-gradle-plugin/blob/0cce976ae1fcb35f29ec67d418a52b8622105c67/src/main/groovy/com/google/protobuf/gradle/ToolsLocator.groovy#L103-L110
            .expectLegacyDeprecationWarning("Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: \"com.google.protobuf:protoc:4.31.1:$classifier@exe\". Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#dependency_multi_string_notation")
            .build()

        then:
        result.task(":generateProto").outcome == SUCCESS
        result.task(":compileJava").outcome == SUCCESS

        when:
        result = runner('compileJava')
            // See: https://github.com/google/protobuf-gradle-plugin/blob/0cce976ae1fcb35f29ec67d418a52b8622105c67/src/main/groovy/com/google/protobuf/gradle/ToolsLocator.groovy#L103-L110
            .expectLegacyDeprecationWarningIf(GradleContextualExecuter.isNotConfigCache(), "Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: \"com.google.protobuf:protoc:4.31.1:$classifier@exe\". Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#dependency_multi_string_notation")
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
}
