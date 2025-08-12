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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.internal.consumer.DefaultGradleConnector

@ToolingApiVersion("current")
@TargetGradleVersion("current")
class IncompatibilityCrossVersionSpec extends ToolingApiSpecification {

    def buildPluginWith(String gradleVersion) {
        def gradleDist = buildContext.distribution(gradleVersion)

        file("other/settings.gradle") << """
            rootProject.name = 'other'
        """
        file("other/build.gradle") << """
            plugins {
                id("java")
            }
            dependencies {
                compile(gradleApi())
            }
        """
        file('other/src/main/java/com/example/MyTask.java') << """
            package com.example;

            import org.gradle.api.*;
            import org.gradle.api.tasks.*;

            public class MyTask extends DefaultTask {
                public MyTask() {
                    getInputs().file("somefile");
                    getInputs().files("afile", "anotherfile");
                    getInputs().dir("someDir");
                    getInputs();
                    getOutputs();
                }
            }
        """

        rawConnector(file("other"))
            .useInstallation(gradleDist.gradleHomeDir)
            .connect()
            .newBuild()
            .setJavaHome(AvailableJavaHomes.getAvailableJdk { md -> gradleDist.daemonWorksWith(md.javaMajorVersion)}.getJavaHome())
            .forTasks("jar")
            .addArguments("--stacktrace")
            .run()

        return file("other/build/libs/other.jar")
    }

    def "can use plugin built with minimum supported Gradle version"() {
        File pluginJar = buildPluginWith(DefaultGradleConnector.MINIMUM_SUPPORTED_GRADLE_VERSION.version)

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("${pluginJar.toURI()}")
                }
            }

            task myTask(type: com.example.MyTask)
        """

        expect:
        succeeds { c ->
            c.newBuild()
                .forTasks("myTask")
                .run()
            true
        }
    }

}
