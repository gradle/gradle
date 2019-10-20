/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.test.fixtures.file.TestFile
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition


@Requires(TestPrecondition.JDK9_OR_LATER)
class GradleBuildInstantExecutionSmokeTest extends AbstractSmokeTest {

    def "can build gradle with instant execution enabled"() {

        given:
        new TestFile("build/gradleBuildCurrent").copyTo(testProjectDir.root)

        and:
        def supportedTasks = [":baseServices:test"]

        when:
        def result = instantRun(*supportedTasks)

        then:
        result.output.count("Calculating task graph as no instant execution cache is available") == 1

        when:
        result = instantRun(*supportedTasks)

        then:
        result.output.count("Reusing instant execution cache") == 1
    }

    private BuildResult instantRun(String... tasks) {
        def testArgs = [
            "-Dorg.gradle.unsafe.instant-execution=true",
            "-PbuildSrcCheck=false"
        ]
        return runner(*(tasks + testArgs)).build()
    }
}
