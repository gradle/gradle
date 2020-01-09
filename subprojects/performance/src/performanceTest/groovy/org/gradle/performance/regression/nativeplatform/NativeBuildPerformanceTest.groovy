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

package org.gradle.performance.regression.nativeplatform

import org.gradle.performance.AbstractCrossVersionGradleProfilerPerformanceTest
import org.gradle.profiler.mutations.ApplyChangeToNativeSourceFileMutator
import spock.lang.Unroll

class NativeBuildPerformanceTest extends AbstractCrossVersionGradleProfilerPerformanceTest {
    def setup() {
        runner.minimumBaseVersion = '4.1' // minimum version that contains new C++ plugins
        runner.targetVersions = ["6.2-20200108160029+0000"]
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
        "bigCppApp"        | '256m'
        "bigCppMulti"      | '1g'
    }

    @Unroll
    def "assemble with #changeType file change on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ["assemble"]
        runner.gradleOpts = ["-Xms$maxMemory", "-Xmx$maxMemory"]
        runner.addBuildMutator { settings ->
            new ApplyChangeToNativeSourceFileMutator(new File(settings.getProjectDir(), fileToChange))
        }

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject   | maxMemory | fileToChange
        "bigCppApp"   | '256m'    | 'src/main/cpp/lib250.cpp'
        "bigCppApp"   | '256m'    | 'src/main/headers/lib250.h'
        "bigCppMulti" | '1g'      | 'project101/src/main/cpp/project101lib4.cpp'
        "bigCppMulti" | '1g'      | 'project101/src/main/public/project101lib4.h'
        changeType = fileToChange.endsWith('.h') ? 'header' : 'source'
    }

}
