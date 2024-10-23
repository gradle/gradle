/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.annotations.RunFor
import org.gradle.performance.annotations.Scenario
import org.gradle.profiler.BuildContext
import org.gradle.profiler.mutations.AbstractFileChangeMutator

import static org.gradle.performance.annotations.ScenarioType.PER_DAY
import static org.gradle.performance.results.OperatingSystem.LINUX

@RunFor(
    @Scenario(type = PER_DAY, operatingSystems = [LINUX], testProjects = ["mediumSwiftMulti", "bigSwiftApp"])
)
class SwiftBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    def setup() {
        runner.minimumBaseVersion = '4.6'
    }

    def "up-to-date assemble (swift)"() {
        given:
        runner.tasksToRun = ["assemble"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    def "incremental compile"() {
        given:
        runner.tasksToRun = ["assemble"]
        runner.addBuildMutator { invocationSettings -> new ChangeSwiftFileMutator(new File(invocationSettings.projectDir, determineFileToChange(runner.testProject))) }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }

    static String determineFileToChange(String testProject) {
        switch (testProject) {
            case 'mediumSwiftMulti':
                return 'lib6api3/src/main/swift/Lib6Api3ImplApi2.swift'
            case 'bigSwiftApp':
                return 'src/main/swift/AppImpl54Api3.swift'
            default:
                throw new IllegalArgumentException("Invalid test project ${testProject}")
        }
    }

    private static class ChangeSwiftFileMutator extends AbstractFileChangeMutator {
        ChangeSwiftFileMutator(File sourceFile) {
            super(sourceFile)
            if (!sourceFile.absolutePath.endsWith('.swift')) {
                throw new IllegalArgumentException('Can only modify Swift source')
            }
        }

        @Override
        protected void applyChangeTo(BuildContext context, StringBuilder text) {
            def location = text.indexOf("public init() { }")
            text.insert(location, "var ${context.getUniqueBuildId()} : Int = 0\n    ")
        }
    }

}
