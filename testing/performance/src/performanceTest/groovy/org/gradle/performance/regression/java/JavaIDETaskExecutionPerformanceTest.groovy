/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.model.eclipse.EclipseProject

import static org.gradle.performance.annotations.ScenarioType.PER_COMMIT
import static org.gradle.performance.generator.JavaTestProjectGenerator.LARGE_MONOLITHIC_JAVA_PROJECT
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_COMMIT, operatingSystems = [LINUX], testProjects = ["largeJavaMultiProjectHierarchy","largeJavaMultiProject", "largeAndroidBuild", "nowInAndroidBuild"])
)
class JavaIDETaskExecutionPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.minimumBaseVersion = "2.11"
    }

    def "run compileJava via Tooling API"() {
        given:
        setupRunner()

        runner.toolingApi("Run task") {
            it.newBuild()
        }.run { builder ->
            builder.addProgressListener(new ProgressListener() {
                @Override
                void statusChanged(ProgressEvent event) {
                    // do nothing, just force the daemon to send all events
                }
            })
            builder.addArguments("compileJava")
            builder.run()
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressedWithHighRelativeMedianDifference()
    }

    private setupRunner() {
        def iterations = determineIterations()
        runner.warmUpRuns = iterations
        runner.runs = iterations
    }

    private determineIterations() {
        return runner.testProject == LARGE_MONOLITHIC_JAVA_PROJECT.projectName ? 200 : 40
    }

    private static void forEachEclipseProject(def elm, @DelegatesTo(value = EclipseProject) Closure<?> action) {
        action.delegate = elm
        action.call()
        elm.children?.each {
            forEachEclipseProject(it, action)
        }
    }


}
