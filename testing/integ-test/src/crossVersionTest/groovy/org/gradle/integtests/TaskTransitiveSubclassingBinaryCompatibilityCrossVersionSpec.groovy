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
import spock.lang.Issue

@TargetVersions("6.8+")
class TaskTransitiveSubclassingBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/16199")
    def "can subclass task subclass in plugin"() {
        setup:
        file('plugin/settings.gradle') << """
            rootProject.name = 'plugin'
        """
        file('plugin/build.gradle') << """
            plugins {
                id 'java-gradle-plugin'
                id 'groovy'
                id 'maven-publish'
            }

            group = "com.example"
            version = "0.1.1"

            repositories {
                mavenCentral()
            }

            gradlePlugin {
                plugins {
                    sofPlugin {
                        id = 'com.example.plugin'
                        implementationClass = 'SofPlugin'
                    }
                }
            }

            publishing {
                repositories {
                    maven {
                        name = "localRepo"
                        url = layout.buildDirectory.dir('repo')
                    }
                }
            }
        """
        file("plugin/src/main/groovy/SofPlugin.groovy") << """
            import org.gradle.api.Project
            import org.gradle.api.Plugin

            class SofPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register('sofExec', CustomJavaExec) { task ->
                        task.args = ["foo bar baz"] as List<String>
                    }
                }
            }
        """
        file("plugin/src/main/groovy/CustomJavaExec.groovy") << """
            import org.gradle.api.tasks.JavaExec

            class CustomJavaExec extends CustomBaseJavaExec {

                @Override
                JavaExec setArgs(List<String> args) {
                    println "args set: \$args"
                    super.setArgs(args)
                }
            }
        """
        file("plugin/src/main/groovy/CustomBaseJavaExec.groovy") << """
            import org.gradle.api.tasks.JavaExec

            class CustomBaseJavaExec extends JavaExec {
                // no setArgs overridden here
            }
        """
        file("settings.gradle") << """
            pluginManagement {
                repositories {
                    maven {
                        url = file('plugin/build/repo')
                    }
                }
            }
        """
        file("build.gradle") << """
            plugins {
                id 'java-library'
                id 'com.example.plugin' version '0.1.1'
            }

            tasks.named('sofExec') { JavaExec task ->
                classpath = sourceSets.main.runtimeClasspath
                mainClass = "ClientMain"
            }
        """
        file("src/main/java/ClientMain.java") << """
            public class ClientMain {
                public static void main(String[] args) {
                }
            }
        """

        expect:
        version previous withTasks 'publish' inDirectory(file("plugin")) run()
        version current requireDaemon() requireIsolatedDaemons() withTasks 'sofExec' run()
    }
}
