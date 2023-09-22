/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.junit.Assume

/**
 * Test the worker API behavior across JDK versions.
 */
class WorkerExecutorJdkVersionsIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    def configureForVersion(JavaVersion javaVersion) {
        String version = javaVersion.majorVersion

        def target = AvailableJavaHomes.getJdk(javaVersion)
        Assume.assumeNotNull(target)

        file("included/src/main/java/com/example/BlankPlugin.java") << """
            package com.example;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import javax.inject.Inject;

            abstract class BlankPlugin implements Plugin<Project> {
                @Inject
                public BlankPlugin() {}
                @Override
                public void apply(Project p) {}
            }
        """
        file("included/src/main/java/com/example/TestWorkAction.java") << """
            package com.example;

            import org.gradle.workers.WorkAction;
            import org.gradle.workers.WorkParameters;
            import javax.inject.Inject;

            abstract class TestWorkAction implements WorkAction<WorkParameters.None> {
                @Inject
                public TestWorkAction() {}
                @Override
                public void execute() {
                    System.out.println("Version: " + System.getProperty("java.version"));
                }
            }
        """
        file("included/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${version})
                }
            }
            gradlePlugin {
                plugins {
                    p {
                        id = "test.worker"
                        implementationClass = "com.example.BlankPlugin"
                    }
                }
            }
        """
        settingsFile << "includeBuild 'included'"
        buildFile << """
            plugins {
                id 'jvm-toolchains'
                id 'test.worker'
            }
            import com.example.TestWorkAction

            def launcher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(${version})
            }.get()
            task runInDaemon(type: WorkerTask) {
                isolationMode = 'processIsolation'
                additionalForkOptions = {
                    it.executable = launcher.executablePath.asFile
                }
            }
        """

        executer.withArgument("-Porg.gradle.java.installations.paths=" + target.javaHome.absolutePath)
    }

    def "useful error message when using incompatible java version: #version"() {
        given:
        configureForVersion(version)

        when:
        executer.withStackTraceChecksDisabled()
        fails("runInDaemon")

        then:
        errorOutput.contains("Unsupported worker JDK version. Required: 8. Current: ${version.majorVersion}")

        where:
        // We can't test against Java 6 since the gradleApi dependencies are compiled to java 8.
        // However, the java 7 compiler is permissive enough to compile against the java 8 API classes.
        version << [JavaVersion.VERSION_1_7]
    }

    def "succeeds when running with compatible java version"() {
        given:
        configureForVersion(JavaVersion.current())

        when:
        succeeds("runInDaemon")

        then:
        outputContains("Version: " + JavaVersion.current().toString())
    }

}
