/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.test.fixtures.file.TestFile

import static org.gradle.plugin.devel.tasks.AbstractPluginValidationIntegrationSpec.Severity.WARNING

abstract class AbstractPluginValidationIntegrationSpec extends AbstractIntegrationSpec {

    def "detects missing annotations on Java properties"() {
        source << """
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

                public long getter() {
                    return 0L;
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
                    return new Options();
                }

                // Valid because it is annotated
                @CompileClasspath
                public java.util.List<java.io.File> getClasspath() {
                    return new java.util.ArrayList<>();
                }

                // Invalid because it has no annotation
                public long getBadTime() {
                    return System.currentTimeMillis();
                }

                // Invalid because it has some other annotation
                @Deprecated
                public String getOldThing() {
                    return null;
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
                
                @TaskAction void execute() {}
            }
        """

        expect:
        assertValidationFailsWith(
            "Type 'MyTask': property 'badTime' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'oldThing' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'options.badNested' is not annotated with an input or output annotation.": WARNING,
            "Type 'MyTask': property 'ter' is not annotated with an input or output annotation.": WARNING,
        )
    }

    enum Severity {
        WARNING("Warning"), ERROR("Error");

        private final String displayName

        Severity(String displayName) {
            this.displayName = displayName
        }

        @Override
        String toString() {
            return displayName
        }
    }

    abstract void assertValidationFailsWith(Map<String, Severity> messages)

    abstract TestFile source(String path)

    TestFile getSource() {
        source("java/MyTask.java")
    }
}
