/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.GradleVersion
import org.junit.Assume

@TargetVersions(["6.8"])
class ArchiveTaskPluginCompatibilityCrossVersionTest extends CrossVersionIntegrationSpec {

    def setup() {
        file("plugin/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
                id 'maven-publish'
            }

            group = 'org.example.plugin'
            version = '0.1'

            repositories {
                jcenter()
            }

            dependencies {
                testImplementation 'junit:junit:4.12'
            }

            gradlePlugin {
                plugins {
                    myplugin {
                        id = 'my.plugin'
                        implementationClass = 'org.example.MyPlugin'
                    }
                }
            }

            publishing {
                repositories {
                    maven {
                        name = "BuildRepo"
                        url = "file://\$project.projectDir/build/repo"
                    }
                }
            }
        """
        file('plugin/src/main/java/org/example/MyPlugin.java') << """
            package org.example;

            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.tasks.bundling.*;
            import org.gradle.plugins.ear.Ear;

            public class MyPlugin implements Plugin<Project> {
                public void apply(Project project) {
                    project.getTasks().register("customZip", Zip.class, new ConfigurationAction());
                    project.getTasks().register("customTar", Tar.class, new ConfigurationAction());
                    project.getTasks().register("customJar", Jar.class, new ConfigurationAction());
                    project.getTasks().register("customWar", War.class, new ConfigurationAction());
                    project.getTasks().register("customEar", Ear.class, new ConfigurationAction());
                    project.getTasks().register("customJvmJar", org.gradle.jvm.tasks.Jar.class, new ConfigurationAction());

                    TaskProvider<Task> customArchives = project.getTasks().register("customArchives");
                    customArchives.configure(new Action<Task>() {
                        @Override
                        public void execute(Task task) {
                            task.dependsOn("customZip", "customTar", "customJar", "customWar", "customEar", "customJvmJar");
                        }
                    });
                }
            }
        """
        file('client/build.gradle') << """
            plugins {
                id 'my.plugin' version '0.1'
            }
        """

        file('client/settings.gradle') << """
            pluginManagement {
                repositories {
                    maven {
                        url '../plugin/build/repo'
                    }
                    gradlePluginPortal()
                }
            }
        """
    }

    def "API methods removed from AbstractArchiveTask are still available for plugins"() {
        setup:
        Assume.assumeTrue(previous.version.baseVersion <= GradleVersion.version("6.8") &&
                          current.version.baseVersion >= GradleVersion.version("7.0"))

        file('plugin/src/main/java/org/example/ConfigurationAction.java') << """
            package org.example;

            import java.io.File;
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.bundling.AbstractArchiveTask;

            public class ConfigurationAction implements Action<AbstractArchiveTask> {

                @Override
                public void execute(final AbstractArchiveTask task) {
                    task.from("build");
                    task.setArchiveName("archiveName");
                    task.setDestinationDir(new File("destinationDir"));
                    task.setBaseName("baseName");
                    task.setAppendix("appendix");
                    task.setVersion("version");
                    task.setExtension("extension");
                    task.setClassifier("classifier");

                    task.doLast(new Action<Task>() {
                        @Override
                        public void execute(Task t) {
                            System.out.println(task.getName() + " archiveName=" + task.getArchiveName());
                            System.out.println(task.getName() + " destinationDir=" + task.getDestinationDir());
                            System.out.println(task.getName() + " baseName=" + task.getBaseName());
                            System.out.println(task.getName() + " appendix=" + task.getAppendix());
                            System.out.println(task.getName() + " version=" + task.getVersion());
                            System.out.println(task.getName() + " extension=" + task.getExtension());
                            System.out.println(task.getName() + " classifier=" + task.getClassifier());
                            System.out.println(task.getName() + " archivePath=" + task.getArchivePath());
                        }
                    });
                }
            }
        """

        when:
        version previous withTasks 'publish' inDirectory(file("plugin")) run()
        def result = version current requireDaemon() requireIsolatedDaemons() withTasks 'customArchives' inDirectory(file('client')) run()

        then:
        ["customZip", "customTar", "customJar", "customWar", "customEar", "customJvmJar"].each {taskName ->
            assert result.output.contains("$taskName archiveName=archiveName")
            assert result.output.contains("$taskName destinationDir=${file('client/destinationDir').absolutePath}")
            assert result.output.contains("$taskName baseName=baseName")
            assert result.output.contains("$taskName appendix=appendix")
            assert result.output.contains("$taskName version=version")
            assert result.output.contains("$taskName extension=extension")
            assert result.output.contains("$taskName classifier=classifier")
            assert result.output.contains("$taskName archivePath=${file('client/destinationDir/archiveName').absolutePath}")
        }
    }

    def "AbstractArchiveTask can be still configured via convention mapping referencing removed methods"() {
        setup:
        Assume.assumeTrue(previous.version.baseVersion <= GradleVersion.version("6.8") &&
            current.version.baseVersion >= GradleVersion.version("7.0"))

        file('plugin/src/main/java/org/example/ConfigurationAction.java') << """
            package org.example;

            import java.io.File;
            import org.gradle.api.Action;
            import org.gradle.api.Task;
            import org.gradle.api.tasks.bundling.AbstractArchiveTask;
            import java.util.concurrent.Callable;

            public class ConfigurationAction implements Action<AbstractArchiveTask> {

                @Override
                public void execute(final AbstractArchiveTask task) {
                    task.from(new File("${testDirectory.createFile("file.txt").absolutePath}"));

                    // reset default configuration
                    task.getArchiveExtension().set((String) null);

                    task.getConventionMapping().map("archiveName", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "conventionArchiveName";
                        }
                    });

                    task.getConventionMapping().map("destinationDir", new Callable<File>() {
                        @Override
                        public File call() throws Exception {
                            return new File("conventionDestinationDir");
                        }
                    });

                    task.getConventionMapping().map("baseName", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "conventionBaseName";
                        }
                    });

                    task.getConventionMapping().map("appendix", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "conventionAppendix";
                        }
                    });

                    task.getConventionMapping().map("version", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "conventionVersion";
                        }
                    });

                    task.getConventionMapping().map("extension", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "conventionExtension";
                        }
                    });

                    task.getConventionMapping().map("classifier", new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            return "conventionClassifier";
                        }
                    });

                    task.doLast(new Action<Task>() {
                        @Override
                        public void execute(Task t) {
                            System.out.println(task.getName() + " archiveName=" + task.getArchiveName());
                            System.out.println(task.getName() + " destinationDir=" + task.getDestinationDir());
                            System.out.println(task.getName() + " baseName=" + task.getBaseName());
                            System.out.println(task.getName() + " appendix=" + task.getAppendix());
                            System.out.println(task.getName() + " version=" + task.getVersion());
                            System.out.println(task.getName() + " extension=" + task.getExtension());
                            System.out.println(task.getName() + " classifier=" + task.getClassifier());
                            System.out.println(task.getName() + " archivePath=" + task.getArchivePath());
                        }
                    });
                }
            }
        """

        when:
        version previous withTasks 'publish' inDirectory(file("plugin")) run()
        def result = version current requireDaemon() requireIsolatedDaemons() withTasks 'customArchive' inDirectory(file('client')) run()

        then:
        ["customZip", "customTar", "customJar", "customWar", "customEar", "customJvmJar"].each {taskName ->
            assert result.output.contains("$taskName archiveName=conventionArchiveName")
            assert result.output.contains("$taskName destinationDir=${file('client/conventionDestinationDir').absolutePath}")
            assert result.output.contains("$taskName baseName=conventionBaseName")
            assert result.output.contains("$taskName appendix=conventionAppendix")
            assert result.output.contains("$taskName version=conventionVersion")
            assert result.output.contains("$taskName extension=conventionExtension")
            assert result.output.contains("$taskName classifier=conventionClassifier")
            assert result.output.contains("$taskName archivePath=${file('client/conventionDestinationDir/conventionArchiveName').absolutePath}")
        }
    }
}
