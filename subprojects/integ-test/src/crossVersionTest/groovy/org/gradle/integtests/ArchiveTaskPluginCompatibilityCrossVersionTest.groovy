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

    def "API methods removed from AbstractArchiveTask are still available for plugins"() {
        setup:
        Assume.assumeTrue(previous.version.baseVersion <= GradleVersion.version("6.8") &&
                          current.version.baseVersion >= GradleVersion.version("7.0"))

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
        file('plugin/src/main/java/org/example/ConfigurationAction.java') << """
            package org.example;

            import java.io.File;
            import org.gradle.api.Action;
            import org.gradle.api.tasks.bundling.AbstractArchiveTask;

            public class ConfigurationAction implements Action<AbstractArchiveTask> {

                @Override
                public void execute(AbstractArchiveTask task) {
                    task.setArchiveName("archiveName");
                    System.out.println(task.getName() + " archiveName=" + task.getArchiveName());

                    task.setDestinationDir(new File("destinationDir"));
                    System.out.println(task.getName() + " destinationDir=" + task.getDestinationDir());

                    task.setBaseName("baseName");
                    System.out.println(task.getName() + " baseName=" + task.getBaseName());

                    task.setAppendix("appendix");
                    System.out.println(task.getName() + " appendix=" + task.getAppendix());

                    task.setVersion("version");
                    System.out.println(task.getName() + " version=" + task.getVersion());

                    task.setExtension("extension");
                    System.out.println(task.getName() + " extension=" + task.getExtension());

                    task.setClassifier("classifier");
                    System.out.println(task.getName() + " classifier=" + task.getClassifier());

                    System.out.println(task.getName() + " archivePath=" + task.getArchivePath());
                }
            }
        """

        file('client/build.gradle') << """
            plugins {
                id 'java-library'
                id 'my.plugin' version '0.1'
            }

            repositories {
                jcenter()
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
}
