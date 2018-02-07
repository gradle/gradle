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

import org.gradle.initialization.ParallelismBuildOptions
import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.mutator.AbstractFileChangeMutator
import spock.lang.Unroll

class SwiftBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {

    def setup() {
        runner.minimumVersion = '4.5'
        runner.targetVersions = ["4.6-20180201071900+0000"]
        runner.args += ["--parallel", "--${ParallelismBuildOptions.MaxWorkersOption.LONG_OPTION}=6"]
    }

    @Unroll
    def "clean assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.cleanTasks = ["clean"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                       | maxMemory
        'mediumSwiftMulti'                | '1G'
    }

    @Unroll
    def "up-to-date assemble on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | maxMemory
        'mediumSwiftMulti' | '1G'
    }

    @Unroll
    def "incremental compile on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.addBuildExperimentListener(new ChangeSwiftFileMutator(fileToChange))

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject        | maxMemory | fileToChange
        "mediumSwiftMulti" | '1g'      | 'lib6api3/src/main/swift/Lib6Api3Impl2Api.swift'
    }

    private static class ChangeSwiftFileMutator extends AbstractFileChangeMutator {

        ChangeSwiftFileMutator(String sourceFilePath) {
            super(sourceFilePath)
            if (!sourceFilePath.endsWith('.swift')) {
                throw new IllegalArgumentException('Can only modify Swift source')
            }
        }

        @Override
        protected void applyChangeTo(StringBuilder text) {
            def newText = text.replaceFirst(/Lib6Api3Impl2Api \{/, "Lib6Api3Impl2Api {\n    var ${uniqueText} : Int = 0")
            text.replace(0, text.length(), newText)
        }
    }

}
