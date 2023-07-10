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

package org.gradle.workers.internal

import spock.lang.Issue

class WorkerExecutorCompositeBuildIntegrationTest extends AbstractWorkerExecutorIntegrationTest {
    def plugin = testDirectory.createDir("plugin")
    def lib = testDirectory.createDir("lib")

    @Issue("https://github.com/gradle/gradle/issues/10317")
    def "can use worker api with composite builds using #pluginId"() {
        if (pluginId == 'legacy-worker-plugin' ) {
            executer.expectDocumentedDeprecationWarning("The WorkerExecutor.submit() method has been deprecated. This is scheduled to be removed in Gradle 8.0. Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. See https://docs.gradle.org/current/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details.")
        }

        settingsFile << """
            pluginManagement {
                includeBuild "plugin"
            }
            rootProject.name = "app"

            includeBuild "lib"
        """

        withFileHelperInPluginBuild()
        withTypedWorkerPluginInPluginBuild()

        lib.file('settings.gradle') << """
            pluginManagement {
                includeBuild "../plugin"
            }
        """
        lib.file("build.gradle") << """
            buildscript {
                dependencies {
                    classpath files('foo.jar')
                }
            }

            plugins {
                id "java"
                id "org.gradle.test.${pluginId}"
            }

            group = "org.gradle.test"
            version = "1.0"

            jar {
                from runWork
            }
        """

        buildFile << """
            plugins {
                id "java"
                id "org.gradle.test.${pluginId}"
            }

            dependencies {
                implementation "org.gradle.test:lib:1.0"
            }

            runWork.dependsOn compileJava
        """

        expect:
        succeeds("runWork")

        and:
        file("build/workOutput").text == "foo"
        lib.file("build/workOutput").text == "foo"

        where:
        pluginId << [ 'typed-worker-plugin' ]
    }

    private void withTypedWorkerPluginInPluginBuild() {
        plugin.file("src/main/java/TypedParameter.java") << """
            import org.gradle.workers.WorkParameters;
            import org.gradle.api.file.RegularFileProperty;

            public interface TypedParameter extends WorkParameters {
                RegularFileProperty getOutputFile();
            }
        """

        plugin.file("src/main/java/TypedWorkAction.java") << """
            import org.gradle.workers.WorkAction;
            import org.gradle.test.FileHelper;

            abstract public class TypedWorkAction implements WorkAction<TypedParameter> {
                public void execute() {
                    FileHelper.write("foo", getParameters().getOutputFile().getAsFile().get());
                }
            }
        """

        plugin.file("src/main/java/TypedWorkerTask.java") << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.Action;
            import org.gradle.api.file.RegularFileProperty;
            import org.gradle.api.tasks.*;
            import org.gradle.workers.*;
            import javax.inject.Inject;
            import java.io.File;

            public class TypedWorkerTask extends DefaultTask {
                private final WorkerExecutor workerExecutor;
                private final RegularFileProperty outputFile;

                @Inject
                public TypedWorkerTask(WorkerExecutor workerExecutor) {
                    this.workerExecutor = workerExecutor;
                    this.outputFile = getProject().getObjects().fileProperty();
                }

                @TaskAction
                public void runWork() {
                    WorkQueue queue = workerExecutor.noIsolation();
                    queue.submit(TypedWorkAction.class, new Action<TypedParameter>() {
                        public void execute(TypedParameter parameters) {
                            parameters.getOutputFile().set(outputFile);
                        }
                    });

                }

                @OutputFile
                RegularFileProperty getOutputFile() {
                    return outputFile;
                }
            }
        """

        plugin.file("src/main/java/TypedWorkerPlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import java.io.File;

            public class TypedWorkerPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().create("runWork", TypedWorkerTask.class)
                        .getOutputFile().set(new File(project.getBuildDir(), "workOutput"));
                }
            }
        """

        plugin.file("build.gradle") << """
            apply plugin: "java-gradle-plugin"

            gradlePlugin {
                plugins {
                    TypedWorkerPlugin {
                        id = "org.gradle.test.typed-worker-plugin"
                        implementationClass = "TypedWorkerPlugin"
                    }
                }
            }
        """
    }

    private void withFileHelperInPluginBuild() {
        plugin.file("src/main/java/org/gradle/test/FileHelper.java") << fixture.fileHelperClass
    }
}
