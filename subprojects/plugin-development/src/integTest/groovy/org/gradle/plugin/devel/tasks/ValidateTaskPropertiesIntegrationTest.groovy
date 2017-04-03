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

class ValidateTaskPropertiesIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java"

            dependencies {
                compile gradleApi()
            }

            tasks.create("validateTaskProperties", org.gradle.plugin.devel.tasks.ValidateTaskProperties) { validator ->
                def sourceSet = sourceSets.main
                validator.dependsOn sourceSet.output
                validator.classesDir = sourceSet.output.classesDir
                validator.classpath = sourceSet.runtimeClasspath
                validator.failOnWarning = true
            }
            tasks.check.dependsOn validateTaskProperties
        """
    }

    def "detects missing annotations on Java properties"() {
        file("src/main/java/MyTask.java") << """
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
        fails "validateTaskProperties"
        failure.assertHasCause "Task property validation failed"
        failure.assertHasCause "Warning: Task type 'MyTask' declares property that is not annotated: 'badTime'."
        failure.assertHasCause "Warning: Task type 'MyTask' declares property that is not annotated: 'options.badNested'."
    }

    def "detects missing annotation on Groovy properties"() {
        buildFile << """
            apply plugin: "groovy"

            dependencies {
                compile localGroovy()
            }
        """
        file("src/main/groovy/MyTask.groovy") << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*

            class MyTask extends DefaultTask {
                @Input
                long goodTime

                @Nested Options options

                @javax.inject.Inject
                org.gradle.api.internal.file.FileResolver fileResolver

                long badTime

                static class Options {
                    @Input String goodNested
                    String badNested
                }
            }
        """

        expect:
        fails "validateTaskProperties"
        failure.assertHasCause "Task property validation failed"
        failure.assertHasCause "Warning: Task type 'MyTask' declares property that is not annotated: 'badTime'."
        failure.assertHasCause "Warning: Task type 'MyTask' declares property that is not annotated: 'options.badNested'."
    }

    def "no problems with Copy task"() {
        file("src/main/java/MyTask.java") << """
            public class MyTask extends org.gradle.api.tasks.Copy {}
        """

        expect:
        succeeds "validateTaskProperties"
    }

    def "does not report missing properties for Provider types"() {
        file("src/main/java/MyTask.java") << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.provider.Provider;
            import org.gradle.api.provider.PropertyState;
            
            import java.io.File;
            import java.util.concurrent.Callable;

            public class MyTask extends DefaultTask {
                private final Provider<String> text;
                private final PropertyState<File> file;
                private final PropertyState<Pojo> pojo;

                public MyTask() {
                    text = getProject().provider(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "Hello World!";
                        }
                    });
                    file = getProject().property(File.class);
                    file.set(new File("some/dir"));
                    pojo = getProject().property(Pojo.class);
                }

                @Input
                public String getText() {
                    return text.get();
                }

                @OutputFile
                public File getFile() {
                    return file.get();
                }

                @Nested
                public Pojo getPojo() {
                    return pojo.get();
                }
            }
        """

        file("src/main/java/Pojo.java") << """
            import org.gradle.api.tasks.Input;

            public class Pojo {
                private final Boolean enabled;
                
                public Pojo(Boolean enabled) {
                    this.enabled = enabled;
                }

                @Input
                public Boolean isEnabled() {
                    return enabled;
                }
            }
        """

        expect:
        succeeds "validateTaskProperties"
    }
}
