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

import org.gradle.integtests.fixtures.executer.GradleBackedArtifactBuilder
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.NoDaemonGradleExecuter
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Ignore

@Requires(
    value = UnitTestPreconditions.Jdk8OrEarlier,
    reason = "tests against old Gradle version that can only work with Java versions up to 8"
)
@ToolingApiVersion("current")
class IncompatibilityCrossVersionSpec extends ToolingApiSpecification {
    def buildPluginWith(String gradleVersion) {
        buildPluginWith(buildContext.distribution(gradleVersion))
    }
    def buildPluginWith(GradleDistribution gradleDist) {
        println "Building plugin with $gradleDist"
        def pluginDir = file("plugin")
        def pluginJar = pluginDir.file("plugin.jar")
        def builder = new GradleBackedArtifactBuilder(new NoDaemonGradleExecuter(gradleDist, temporaryFolder).withWarningMode(null), pluginDir)
        builder.sourceFile("com/example/MyTask.java") << """
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
        builder.buildJar(pluginJar)

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("${pluginJar.toURI()}")
                }
            }

            task myTask(type: com.example.MyTask)
        """
    }

    @TargetGradleVersion(">=3.3")
    def "can use plugin built with Gradle 3.0 with"() {
        expect:
        buildPluginWith("3.0")
        assertWorks()
    }

    // Gradle 3.2 and 3.2.1 leaked internal types that fail when used with
    // newer versions of Gradle.
    @Ignore
    @TargetGradleVersion(">=3.3")
    def "can use plugin built with Gradle 3.2.1 with"() {
        expect:
        buildPluginWith("3.2.1")
        assertWorks()
    }

    // Gradle 3.2 and 3.2.1 leaked internal types that fail when used with
    // older versions of Gradle.
    @Ignore
    @TargetGradleVersion("=3.0")
    def "can use plugin built with Gradle 3.2.1 with old version"() {
        expect:
        buildPluginWith("3.2.1")
        assertWorks()
    }

    private void assertWorks() {
        // So we don't try to use a classpath distribution
        toolingApi.requireDaemons()

        withConnector {
            // TestKit builds that use debug will set this to true
            it.embedded(true)
        }

        // Run the build
        withConnection { c ->
            c.newBuild().forTasks("myTask").run()
        }
    }
}
