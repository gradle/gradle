/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks.testing

import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class TestReportTest extends AbstractProjectBuilderSpec {
    def reportTask = TestUtil.create(temporaryFolder).task(TestReport)

    def "infers dependencies and results dirs from input tests"() {
        def test1 = test("test1")
        def test2 = test("test2")
        def test3 = test("test3")

        when:
        reportTask.reportOn test1
        reportTask.reportOn([[test2], test3])

        then:
        reportTask.testResultDirs.files as List == [test1, test2, test3]*.binaryResultsDirectory*.getAsFile()*.get()
        reportTask.testResultDirs.buildDependencies.getDependencies(reportTask) == [test1, test2, test3] as Set
    }

    def "can attach result dirs"() {
        def binDir = temporaryFolder.file("other")

        when:
        reportTask.reportOn binDir

        then:
        reportTask.testResultDirs.files as List == [binDir]
    }

    def test(String name) {
        def test = TestUtil.createTask(Test, project, name)
        test.binaryResultsDirectory.set(temporaryFolder.file(name))
        return test
    }
}
