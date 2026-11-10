/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import org.gradle.api.problems.FileLocation
import org.gradle.api.problems.LineInFileLocation
import org.gradle.api.problems.Severity
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions
import spock.lang.Issue

class ValidatePluginsSourceLocationIntegrationTest extends AbstractIntegrationSpec implements ValidatePluginsTrait {

    @Issue("https://github.com/gradle/gradle/issues/39713")
    def "reports source file and line number for missing property annotations in Java"() {
        given:
        source("src/main/java/com/example/MyTask.java") << """
            package com.example;

            import org.gradle.api.DefaultTask;
            import org.gradle.work.DisableCachingByDefault;

            @DisableCachingByDefault(because = "test")
            public class MyTask extends DefaultTask {
                public String getUnannotatedProperty() {
                    return "hello";
                }
            }
        """

        expect:
        assertValidationFailsWith(1)

        and:
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'Gradle:Plugin Validation:Missing annotation'
            contextualLabel == "Type 'com.example.MyTask' property 'unannotatedProperty' is missing an input or output annotation"
            details == 'Properties must be annotated so that Gradle knows how to handle them during up-to-date checking'
            solutions == [
                'Add an input or output annotation',
                'Mark it as @Internal',
            ]
            additionalData.asMap == [
                'typeName': 'com.example.MyTask',
                'propertyName': 'unannotatedProperty',
                'declaringClassName': 'com.example.MyTask',
                'sourceFilename': 'MyTask.java',
                'lineNumber': 10,
            ]
            oneLocation(LineInFileLocation).path == file("src/main/java/com/example/MyTask.java").absolutePath
            oneLocation(LineInFileLocation).line == 10
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/39713")
    def "reports declaring class and declaring source location for inherited properties"() {
        given:
        source("src/main/java/com/example/BaseTask.java") << """
            package com.example;

            import org.gradle.api.DefaultTask;

            public abstract class BaseTask extends DefaultTask {
                public String getInheritedUnannotated() {
                    return "base";
                }
            }
        """

        source("src/main/java/com/example/SubTask.java") << """
            package com.example;

            import org.gradle.api.tasks.Input;
            import org.gradle.work.DisableCachingByDefault;

            @DisableCachingByDefault(because = "test")
            public class SubTask extends BaseTask {
                @Input
                public String getValidProperty() {
                    return "valid";
                }
            }
        """

        expect:
        assertValidationFailsWith(2)

        and:
        verifyAll(findReceivedProblem { it.additionalData.asMap.typeName == 'com.example.SubTask' }) {
            severity == Severity.ERROR
            fqid == 'Gradle:Plugin Validation:Missing annotation'
            contextualLabel == "Type 'com.example.SubTask' property 'inheritedUnannotated' is missing an input or output annotation"
            additionalData.asMap == [
                'typeName': 'com.example.SubTask',
                'propertyName': 'inheritedUnannotated',
                'declaringClassName': 'com.example.BaseTask',
                'sourceFilename': 'BaseTask.java',
                'lineNumber': 8,
            ]
            oneLocation(LineInFileLocation).path == file("src/main/java/com/example/BaseTask.java").absolutePath
            oneLocation(LineInFileLocation).line == 8
        }
        verifyAll(findReceivedProblem { it.additionalData.asMap.typeName == 'com.example.BaseTask' }) {
            severity == Severity.ERROR
            fqid == 'Gradle:Plugin Validation:Missing annotation'
            contextualLabel == "Type 'com.example.BaseTask' property 'inheritedUnannotated' is missing an input or output annotation"
            additionalData.asMap == [
                'typeName': 'com.example.BaseTask',
                'propertyName': 'inheritedUnannotated',
                'declaringClassName': 'com.example.BaseTask',
                'sourceFilename': 'BaseTask.java',
                'lineNumber': 8,
            ]
            oneLocation(LineInFileLocation).path == file("src/main/java/com/example/BaseTask.java").absolutePath
            oneLocation(LineInFileLocation).line == 8
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/39713")
    def "missing source files do not break plugin validation"() {
        given:
        source("src/main/java/com/example/MyTask.java") << """
            package com.example;

            import org.gradle.api.DefaultTask;
            import org.gradle.work.DisableCachingByDefault;

            @DisableCachingByDefault(because = "test")
            public class MyTask extends DefaultTask {
                public String getUnannotatedProperty() {
                    return "hello";
                }
            }
        """

        buildFile << """
            tasks.named("validatePlugins") {
                sourceDirectories.setFrom()
            }
        """

        expect:
        assertValidationFailsWith(1)

        and:
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'Gradle:Plugin Validation:Missing annotation'
            contextualLabel == "Type 'com.example.MyTask' property 'unannotatedProperty' is missing an input or output annotation"
            originLocations == []
            additionalData.asMap.declaringClassName == 'com.example.MyTask'
            additionalData.asMap.sourceFilename == 'MyTask.java'
            additionalData.asMap.lineNumber == 10
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/39713")
    @Requires(value = JdkVersionTestPreconditions.KotlinSupportedJdk.class)
    def "reports source file and line number for missing property annotations in Kotlin"() {
        given:
        kotlinTaskSource << """
            import org.gradle.api.DefaultTask
            import org.gradle.work.DisableCachingByDefault

            @DisableCachingByDefault(because = "test")
            abstract class MyTask : DefaultTask() {
                val unannotatedProperty: String
                    get() = "hello"
            }
        """

        expect:
        assertValidationFailsWith(1)

        and:
        verifyAll(receivedProblem) {
            severity == Severity.ERROR
            fqid == 'Gradle:Plugin Validation:Missing annotation'
            contextualLabel == "Type 'MyTask' property 'unannotatedProperty' is missing an input or output annotation"
            additionalData.asMap.declaringClassName == 'MyTask'
            additionalData.asMap.sourceFilename == 'MyTask.kt'
            additionalData.asMap.lineNumber != null
            oneLocation(LineInFileLocation).path == file("src/main/kotlin/MyTask.kt").absolutePath
            oneLocation(LineInFileLocation).line != -1
        }
    }
}
