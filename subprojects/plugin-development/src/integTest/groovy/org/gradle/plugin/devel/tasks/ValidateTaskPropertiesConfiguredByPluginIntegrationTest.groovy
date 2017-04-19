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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ValidateTaskPropertiesConfiguredByPluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java-gradle-plugin"
        """
    }

    def "detects missing annotations on Java properties"() {
        buildFile << """
            validateTaskProperties {
                failOnWarning = true
            }
        """
        file('src/main/java/com/example/TestPlugin.java') << """
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class TestPlugin implements Plugin<Project> {
                public void apply(Project project) {}
            }
        """

        file("src/main/java/com/example/MyTask.java") << """
            package com.example;

            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class MyTask extends DefaultTask {
                // Should be ignored because it's not a getter
                public void getVoid() {
                }

                // Should be ignored because it's not a getter
                public int getWithParameter(int count) {
                    return count;
                }

                // Ignored because not a JavaBean getter
                public long getter() {
                    return System.currentTimeMillis();
                }

                // Ignored because static
                public static int getStatic() {
                    return 0;
                }

                // Ignored because injected
                @javax.inject.Inject
                public org.gradle.api.internal.file.FileResolver getInjected() {
                    throw new UnsupportedOperationException();
                }

                // Valid because it is annotated
                @Input
                public long getGoodTime() {
                    return 0;
                }

                // Valid because it is annotated
                @Nested
                public Options getOptions() {
                    return null;
                }

                // Invalid because it has no annotation
                public long getBadTime() {
                    return System.currentTimeMillis();
                }

                public static class Options {
                    // Valid because it is annotated
                    @Input
                    public int getGoodNested() {
                        return 1;
                    }

                    // Invalid because there is no annotation
                    public int getBadNested() {
                        return -1;
                    }
                }
            }
        """

        expect:
        fails "check"
        failure.assertHasCause "Task property validation failed"
        failure.assertHasCause "Warning: Task type 'com.example.MyTask' property 'badTime': is not annotated with an input or output annotation."
        failure.assertHasCause "Warning: Task type 'com.example.MyTask' property 'options.badNested': is not annotated with an input or output annotation."

        file("build/reports/task-properties/report.txt").text == """
            Warning: Task type 'com.example.MyTask' property 'badTime': is not annotated with an input or output annotation.
            Warning: Task type 'com.example.MyTask' property 'options.badNested': is not annotated with an input or output annotation.
        """.stripIndent().trim()
    }
}
