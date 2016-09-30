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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.MultiModelToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.gradle.BuildInvocations
import spock.lang.Ignore

/**
 * Tooling client provides progress listener for a composite build
 */
//TODO add more coverage once composite builds report events from included builds
@TargetGradleVersion(">=3.2")
class ProgressListenerCompositeBuildCrossVersionSpec extends MultiModelToolingApiSpecification {

    def "receive build progress events when requesting models"() {
        given:
        def buildA = singleProjectBuildInSubfolder("buildA")
        def buildB = singleProjectBuildInSubfolder("buildB")
        [buildA, buildB].each { build ->
            build.buildFile << """
                apply plugin: 'java'
                compileJava.options.fork = true  // forked as 'Gradle Test Executor 1'
            """

            build.file("src/main/java/example/MyClass.java") << """
                package example;
                public class MyClass {
                    public void foo() throws Exception {
                        Thread.sleep(100);
                    }
                }
            """
        }
        includeBuilds(buildA, buildB)

        when:
        def events = new ProgressEvents()
        withConnection {
            models(BuildInvocations).addProgressListener(events, EnumSet.of(OperationType.GENERIC)).get()
        }

        then:
        events.assertIsABuild()
    }
}
