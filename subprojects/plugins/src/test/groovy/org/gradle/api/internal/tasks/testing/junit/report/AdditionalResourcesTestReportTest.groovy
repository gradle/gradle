/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report

import org.gradle.api.internal.tasks.testing.BuildableTestResultsProvider
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ConfigureUtil
import org.junit.Rule
import spock.lang.Specification

class AdditionalResourcesTestReportTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final TestFile reportDir = tmpDir.file('report')
    final File resourceFile1 = File.createTempFile('additionalResource', '.txt')
    final File resourceFile2 = File.createTempFile('additionalResource', '.txt')
    final DefaultTestReport report = new AdditionalResourcesTestReport(resourceFile1, resourceFile2)

    def additionalResourcesProcessing() {
        given:
        def firstTestResults = aggregatedBuildResultsRun1()
        when:
        report.generateReport(new AggregateTestResultsProvider([firstTestResults]), reportDir)

        then:
        def passedClassFile = new HtmlTestResultsFixture(reportDir.file('classes/org.gradle.aggregation.FooTest.html'))
        passedClassFile.assertHasTests(1)
        assert passedClassFile.content.select("a[href=${resourceFile1.name}]").find { it.text() == resourceFile1.name }
        assert passedClassFile.content.select("a[href=${resourceFile2.name}]").find { it.text() == resourceFile2.name }
    }


    TestResultsProvider buildResults(Closure closure) {
        ConfigureUtil.configure(closure, new BuildableTestResultsProvider())
    }

    TestResultsProvider aggregatedBuildResultsRun1() {
        buildResults {
            testClassResult("org.gradle.aggregation.FooTest") {
                testcase("first") {
                    duration = 1000;
                }
            }
        }
    }

}

class AdditionalResourcesTestReport extends DefaultTestReport {
    File[] resources

    AdditionalResourcesTestReport(File... resources) {
        this.resources = resources

    }

    @Override
    protected List<IAdditionalTestResultResource> additionalResources() {
        return [new IAdditionalTestResultResource() {
            @Override
            List<File> findResources(TestResult test) {
                resources
            }
        }]
    }
}


