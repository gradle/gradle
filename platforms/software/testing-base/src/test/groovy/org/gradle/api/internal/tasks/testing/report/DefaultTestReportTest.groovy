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
package org.gradle.api.internal.tasks.testing.report

import org.gradle.api.internal.tasks.testing.BuildableTestResultsProvider
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.DefaultParallelismConfiguration
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.operations.DefaultBuildOperationExecutor
import org.gradle.internal.operations.DefaultBuildOperationIdFactory
import org.gradle.internal.operations.DefaultBuildOperationQueueFactory
import org.gradle.internal.progress.NoOpProgressLoggerFactory
import org.gradle.internal.time.Clock
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import org.gradle.util.internal.ConfigureUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultTestReportTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    BuildOperationExecutor buildOperationExecutor
    DefaultTestReport report
    final TestFile reportDir = tmpDir.file('report')
    final TestFile indexFile = reportDir.file('index.html')
    final TestResultsProvider testResultProvider = Mock()
    final WorkerLeaseService workerLeaseService = new TestWorkerLeaseService()

    def reportWithMaxThreads(int numThreads) {
        def parallelismConfiguration = new DefaultParallelismConfiguration(false, numThreads)
        buildOperationExecutor = new DefaultBuildOperationExecutor(
                Mock(BuildOperationListener), Mock(Clock), new NoOpProgressLoggerFactory(),
                new DefaultBuildOperationQueueFactory(workerLeaseService), new DefaultExecutorFactory(), parallelismConfiguration, new DefaultBuildOperationIdFactory(), new DefaultProblems(Mock(BuildOperationProgressEventEmitter)))
        return new DefaultTestReport(buildOperationExecutor)
    }

    def generatesReportWhenThereAreNoTestResults() {
        given:
        report = reportWithMaxThreads(1)
        emptyResultSet()

        when:
        report.generateReport(testResultProvider, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(0)
        index.assertHasFailures(0)
        index.assertHasIgnored(0)
        index.assertHasNoDuration()
        index.assertHasNoSuccessRate()
        index.assertHasNoNavLinks()
    }

    def "generates report with aggregated index page for build with no failures - #numThreads parallel thread(s)"() {
        given:
        report = reportWithMaxThreads(numThreads)
        def testTestResults = passingBuildResults()

        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(2)
        index.assertHasFailures(0)
        index.assertHasIgnored(0)
        index.assertHasSuccessRate(100)
        index.assertHasDuration("2.000s")
        index.assertHasOverallResult("success")
        index.assertHasNoFailedTests()
        index.assertHasNoIgnoredTests()

        def passingPackageDetails = index.packageDetails("org.gradle.passing");
        passingPackageDetails.assertNumberOfTests(1);
        passingPackageDetails.assertNumberOfFailures(0);
        passingPackageDetails.assertNumberOfIgnored(0);
        passingPackageDetails.assertDuration("1.000s");
        passingPackageDetails.assertSuccessRate("100%");
        passingPackageDetails.assertPassed()
        passingPackageDetails.assertLinksTo("packages/org.gradle.passing.html");

        def passingSubPackageDetails = index.packageDetails("org.gradle.passing.subpackage");
        passingSubPackageDetails.assertNumberOfTests(1);
        passingSubPackageDetails.assertNumberOfFailures(0);
        passingSubPackageDetails.assertNumberOfIgnored(0);
        passingSubPackageDetails.assertDuration("1.000s");
        passingSubPackageDetails.assertSuccessRate("100%");
        passingSubPackageDetails.assertPassed()
        passingSubPackageDetails.assertLinksTo("packages/org.gradle.passing.subpackage.html");

        def passedClassDetails = index.classDetails("org.gradle.passing.Passed");
        passedClassDetails.assertNumberOfTests(1);
        passedClassDetails.assertNumberOfFailures(0);
        passedClassDetails.assertNumberOfIgnored(0);
        passedClassDetails.assertDuration("1.000s");
        passedClassDetails.assertSuccessRate("100%");
        passedClassDetails.assertPassed()
        passedClassDetails.assertLinksTo("classes/org.gradle.passing.Passed.html");

        def alsoPassedClassDetails = index.classDetails("org.gradle.passing.subpackage.AlsoPassed");
        alsoPassedClassDetails.assertNumberOfTests(1);
        alsoPassedClassDetails.assertNumberOfFailures(0);
        alsoPassedClassDetails.assertNumberOfIgnored(0);
        alsoPassedClassDetails.assertDuration("1.000s");
        alsoPassedClassDetails.assertSuccessRate("100%");
        alsoPassedClassDetails.assertPassed()
        alsoPassedClassDetails.assertLinksTo("classes/org.gradle.passing.subpackage.AlsoPassed.html");

        where:
        numThreads << [1, 4]
    }

    def "generates report with aggregated index page for failing build - #numThreads parallel thread(s)"() {
        given:
        report = reportWithMaxThreads(numThreads)
        def testTestResults = failingBuildResults()

        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(7)
        index.assertHasFailures(1)
        index.assertHasIgnored(2)
        index.assertHasSuccessRate(80)
        index.assertHasDuration("7.000s")
        index.assertHasOverallResult("failures")

        index.assertHasFailedTest('classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed', 'failed')

        index.assertHasIgnoredTest('classes/org.gradle.ignoring.SomeIgnoredSomePassed', 'ignored')
        index.assertHasIgnoredTest('classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed', 'ignored')

        def passingPackageDetails = index.packageDetails("org.gradle.passing");
        passingPackageDetails.assertNumberOfTests(2);
        passingPackageDetails.assertNumberOfFailures(0);
        passingPackageDetails.assertNumberOfIgnored(0);
        passingPackageDetails.assertDuration("2.000s");
        passingPackageDetails.assertSuccessRate("100%");
        passingPackageDetails.assertPassed()
        passingPackageDetails.assertLinksTo("packages/org.gradle.passing.html");

        def ignoringPackageDetails = index.packageDetails("org.gradle.ignoring");
        ignoringPackageDetails.assertNumberOfTests(2);
        ignoringPackageDetails.assertNumberOfFailures(0);
        ignoringPackageDetails.assertNumberOfIgnored(1);
        ignoringPackageDetails.assertDuration("2.000s");
        ignoringPackageDetails.assertSuccessRate("100%");
        ignoringPackageDetails.assertIgnored()
        ignoringPackageDetails.assertLinksTo("packages/org.gradle.ignoring.html");

        def failingPackageDetails = index.packageDetails("org.gradle.failing");
        failingPackageDetails.assertNumberOfTests(3);
        failingPackageDetails.assertNumberOfFailures(1);
        failingPackageDetails.assertNumberOfIgnored(1);
        failingPackageDetails.assertDuration("3.000s");
        failingPackageDetails.assertSuccessRate("50%");
        failingPackageDetails.assertFailed()
        failingPackageDetails.assertLinksTo("packages/org.gradle.failing.html");

        def passedClassDetails = index.classDetails("org.gradle.passing.Passed");
        passedClassDetails.assertNumberOfTests(1);
        passedClassDetails.assertNumberOfFailures(0);
        passedClassDetails.assertNumberOfIgnored(0);
        passedClassDetails.assertDuration("1.000s");
        passedClassDetails.assertSuccessRate("100%");
        passedClassDetails.assertPassed()
        passedClassDetails.assertLinksTo("classes/org.gradle.passing.Passed.html");

        def alsoPassedClassDetails = index.classDetails("org.gradle.passing.AlsoPassed");
        alsoPassedClassDetails.assertNumberOfTests(1);
        alsoPassedClassDetails.assertNumberOfFailures(0);
        alsoPassedClassDetails.assertNumberOfIgnored(0);
        alsoPassedClassDetails.assertDuration("1.000s");
        alsoPassedClassDetails.assertSuccessRate("100%");
        alsoPassedClassDetails.assertPassed()
        alsoPassedClassDetails.assertLinksTo("classes/org.gradle.passing.AlsoPassed.html");

        def someIgnoredClassDetails = index.classDetails("org.gradle.ignoring.SomeIgnoredSomePassed");
        someIgnoredClassDetails.assertNumberOfTests(2);
        someIgnoredClassDetails.assertNumberOfFailures(0);
        someIgnoredClassDetails.assertNumberOfIgnored(1);
        someIgnoredClassDetails.assertDuration("2.000s");
        someIgnoredClassDetails.assertSuccessRate("100%");
        someIgnoredClassDetails.assertIgnored()
        someIgnoredClassDetails.assertLinksTo("classes/org.gradle.ignoring.SomeIgnoredSomePassed.html");

        def someFailedClassDetails = index.classDetails("org.gradle.failing.SomeIgnoredSomePassedSomeFailed");
        someFailedClassDetails.assertNumberOfTests(3);
        someFailedClassDetails.assertNumberOfFailures(1);
        someFailedClassDetails.assertNumberOfIgnored(1);
        someFailedClassDetails.assertDuration("3.000s");
        someFailedClassDetails.assertSuccessRate("50%");
        someFailedClassDetails.assertFailed()
        someFailedClassDetails.assertLinksTo("classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed.html");

        where:
        numThreads << [1, 4]
    }

    def "generates report with aggregated package pages - #numThreads parallel thread(s)"() {
        given:
        report = reportWithMaxThreads(numThreads)
        def testTestResults = failingBuildResults()

        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def passingPackageFile = results(reportDir.file('packages/org.gradle.passing.html'))
        passingPackageFile.assertHasTests(2)
        passingPackageFile.assertHasFailures(0)
        passingPackageFile.assertHasSuccessRate(100)
        passingPackageFile.assertHasDuration("2.000s")
        passingPackageFile.assertHasNoFailedTests()
        passingPackageFile.assertHasNoIgnoredTests()
        passingPackageFile.assertHasLinkTo('../index', 'all')

        def passedClassDetails = passingPackageFile.classDetails("Passed");
        passedClassDetails.assertNumberOfTests(1);
        passedClassDetails.assertNumberOfFailures(0);
        passedClassDetails.assertNumberOfIgnored(0);
        passedClassDetails.assertDuration("1.000s");
        passedClassDetails.assertSuccessRate("100%");
        passedClassDetails.assertPassed()
        passedClassDetails.assertLinksTo("classes/org.gradle.passing.Passed.html");

        def alsoPassedClassDetails = passingPackageFile.classDetails("AlsoPassed");
        alsoPassedClassDetails.assertNumberOfTests(1);
        alsoPassedClassDetails.assertNumberOfFailures(0);
        alsoPassedClassDetails.assertNumberOfIgnored(0);
        alsoPassedClassDetails.assertDuration("1.000s");
        alsoPassedClassDetails.assertSuccessRate("100%");
        alsoPassedClassDetails.assertPassed()
        alsoPassedClassDetails.assertLinksTo("classes/org.gradle.passing.AlsoPassed.html");

        def ignoredPackageFile = results(reportDir.file('packages/org.gradle.ignoring.html'))
        ignoredPackageFile.assertHasTests(2)
        ignoredPackageFile.assertHasFailures(0)
        ignoredPackageFile.assertHasIgnored(1)
        ignoredPackageFile.assertHasSuccessRate(100)
        ignoredPackageFile.assertHasDuration("2.000s")
        passingPackageFile.assertHasNoFailedTests()
        ignoredPackageFile.assertHasIgnoredTest('../classes/org.gradle.ignoring.SomeIgnoredSomePassed', 'ignored')
        ignoredPackageFile.assertHasLinkTo('../index', 'all')

        def someIgnoredClassDetails = ignoredPackageFile.classDetails("SomeIgnoredSomePassed");
        someIgnoredClassDetails.assertNumberOfTests(2);
        someIgnoredClassDetails.assertNumberOfFailures(0);
        someIgnoredClassDetails.assertNumberOfIgnored(1);
        someIgnoredClassDetails.assertDuration("2.000s");
        someIgnoredClassDetails.assertSuccessRate("100%");
        someIgnoredClassDetails.assertIgnored()
        someIgnoredClassDetails.assertLinksTo("classes/org.gradle.passing.SomeIgnoredSomePassed.html");

        def failingPackageFile = results(reportDir.file('packages/org.gradle.failing.html'))
        failingPackageFile.assertHasTests(3)
        failingPackageFile.assertHasFailures(1)
        failingPackageFile.assertHasIgnored(1)
        failingPackageFile.assertHasSuccessRate(50)
        failingPackageFile.assertHasDuration("3.000s")
        failingPackageFile.assertHasFailedTest('../classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed', 'failed')
        failingPackageFile.assertHasIgnoredTest('../classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed', 'ignored')
        failingPackageFile.assertHasLinkTo('../index', 'all')

        def someFailedClassDetails = failingPackageFile.classDetails("SomeIgnoredSomePassedSomeFailed");
        someFailedClassDetails.assertNumberOfTests(3);
        someFailedClassDetails.assertNumberOfFailures(1);
        someFailedClassDetails.assertNumberOfIgnored(1);
        someFailedClassDetails.assertDuration("3.000s");
        someFailedClassDetails.assertSuccessRate("50%");
        someFailedClassDetails.assertFailed()
        someFailedClassDetails.assertLinksTo("classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed.html");

        where:
        numThreads << [1, 4]
    }

    def "generates report with class pages - #numThreads parallel thread(s)"() {
        given:
        report = reportWithMaxThreads(numThreads)
        def testTestResults = failingBuildResults()

        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def passedClassFile = results(reportDir.file('classes/org.gradle.passing.Passed.html'))
        passedClassFile.assertHasTests(1)
        passedClassFile.assertHasFailures(0)
        passedClassFile.assertHasIgnored(0)
        passedClassFile.assertHasSuccessRate(100)
        passedClassFile.assertHasDuration("1.000s")
        passedClassFile.assertHasLinkTo('../index', 'all')
        passedClassFile.assertHasLinkTo('../packages/org.gradle.passing', 'org.gradle.passing')

        def passedTestDetails = passedClassFile.testDetails('passed')
        passedTestDetails.assertDuration("1.000s")
        passedTestDetails.assertPassed()

        def alsoPassedClassFile = results(reportDir.file('classes/org.gradle.passing.AlsoPassed.html'))
        alsoPassedClassFile.assertHasTests(1)
        alsoPassedClassFile.assertHasFailures(0)
        alsoPassedClassFile.assertHasIgnored(0)
        alsoPassedClassFile.assertHasSuccessRate(100)
        alsoPassedClassFile.assertHasDuration("1.000s")
        alsoPassedClassFile.assertHasLinkTo('../index', 'all')
        alsoPassedClassFile.assertHasLinkTo('../packages/org.gradle.passing', 'org.gradle.passing')
        alsoPassedClassFile.assertHasStandardOutput('this is\nstandard output')
        alsoPassedClassFile.assertHasStandardError('this is\nstandard error')

        def alsoPassedTestDetails = alsoPassedClassFile.testDetails('passedToo')
        alsoPassedTestDetails.assertDuration("1.000s")
        alsoPassedTestDetails.assertPassed()

        def someIgnoredClassFile = results(reportDir.file('classes/org.gradle.ignoring.SomeIgnoredSomePassed.html'))
        someIgnoredClassFile.assertHasTests(2)
        someIgnoredClassFile.assertHasFailures(0)
        someIgnoredClassFile.assertHasIgnored(1)
        someIgnoredClassFile.assertHasSuccessRate(100)
        someIgnoredClassFile.assertHasDuration("2.000s")
        someIgnoredClassFile.assertHasLinkTo('../index', 'all')
        someIgnoredClassFile.assertHasLinkTo('../packages/org.gradle.ignoring', 'org.gradle.ignoring')

        def passedInIgnoredTestDetails = someIgnoredClassFile.testDetails('passed')
        passedInIgnoredTestDetails.assertDuration("1.000s")
        passedInIgnoredTestDetails.assertPassed()

        def ignoredTestDetails = someIgnoredClassFile.testDetails('ignored')
        ignoredTestDetails.assertDuration("-") //is this right? it seems an ignored test may still have a duration?
        ignoredTestDetails.assertIgnored()

        def failingClassFile = results(reportDir.file('classes/org.gradle.failing.SomeIgnoredSomePassedSomeFailed.html'))
        failingClassFile.assertHasTests(3)
        failingClassFile.assertHasFailures(1)
        failingClassFile.assertHasIgnored(1)
        failingClassFile.assertHasSuccessRate(50)
        failingClassFile.assertHasDuration("3.000s")
        failingClassFile.assertHasLinkTo('../index', 'all')
        failingClassFile.assertHasLinkTo('../packages/org.gradle.failing', 'org.gradle.failing')

        def passedInFailingTestDetails = failingClassFile.testDetails('passed')
        passedInFailingTestDetails.assertDuration("1.000s")
        passedInFailingTestDetails.assertPassed()

        def ignoredInFailingTestDetails = failingClassFile.testDetails('ignored')
        ignoredInFailingTestDetails.assertDuration("-") //is this right? it seems an ignored test may still have a duration?
        ignoredInFailingTestDetails.assertIgnored()

        def failingTestDetails = failingClassFile.testDetails('failed')
        failingTestDetails.assertDuration("1.000s")
        failingTestDetails.assertFailed()

        failingClassFile.assertHasFailure('failed', 'something failed\n\nthis is the failure\nat someClass\n')

        where:
        numThreads << [1, 4]
    }

    def "aggregate same tests run with different results - #numThreads parallel thread(s)"() {
        given:
        report = reportWithMaxThreads(numThreads)
        def firstTestResults = aggregatedBuildResultsRun1()
        def secondTestResults = aggregatedBuildResultsRun2()

        when:
        report.generateReport(new AggregateTestResultsProvider([firstTestResults, secondTestResults]), reportDir)

        then:
        def passedClassFile = results(reportDir.file('classes/org.gradle.aggregation.FooTest.html'))
        passedClassFile.assertHasTests(2)
        passedClassFile.allTestDetails('first').size() == 2
        passedClassFile.assertHasFailures(0)
        passedClassFile.assertHasIgnored(0)
        passedClassFile.assertHasSuccessRate(100)
        passedClassFile.assertHasLinkTo('../index', 'all')
        passedClassFile.assertHasLinkTo('../packages/org.gradle.aggregation', 'org.gradle.aggregation')

        def mixedClassFile = results(reportDir.file('classes/org.gradle.aggregation.BarTest.html'))
        mixedClassFile.assertHasTests(2)
        mixedClassFile.assertHasFailures(1)
        mixedClassFile.assertHasIgnored(0)
        mixedClassFile.assertHasSuccessRate(50)
        mixedClassFile.assertHasLinkTo('../index', 'all')
        mixedClassFile.assertHasLinkTo('../packages/org.gradle.aggregation', 'org.gradle.aggregation')

        def failingTestDetails = mixedClassFile.allTestDetails('second')
        failingTestDetails.any { it -> it.failed() }

        def failingPackageFile = results(reportDir.file('packages/org.gradle.aggregation.html'))
        failingPackageFile.assertHasFailedTest('../classes/org.gradle.aggregation.BarTest', 'second')

        mixedClassFile.assertHasFailure('second', 'something failed\n\nthis is the failure\nat someClass\n')

        where:
        numThreads << [1, 4]
    }

    def "aggregate same tests different methods run with different results - #numThreads parallel thread(s)"() {
        given:
        report = reportWithMaxThreads(numThreads)
        def firstTestResults = aggregatedBuildResultsRun1()
        def secondTestResults = aggregatedBuildResultsRun2('Alternative')

        when:
        report.generateReport(new AggregateTestResultsProvider([firstTestResults, secondTestResults]), reportDir)

        then:
        def passedClassFile = results(reportDir.file('classes/org.gradle.aggregation.FooTest.html'))
        passedClassFile.assertHasTests(2)
        passedClassFile.testDetails('first').assertPassed()
        passedClassFile.testDetails('firstAlternative').assertPassed()
        passedClassFile.assertHasFailures(0)
        passedClassFile.assertHasIgnored(0)
        passedClassFile.assertHasSuccessRate(100)
        passedClassFile.assertHasLinkTo('../index', 'all')
        passedClassFile.assertHasLinkTo('../packages/org.gradle.aggregation', 'org.gradle.aggregation')

        def mixedClassFile = results(reportDir.file('classes/org.gradle.aggregation.BarTest.html'))
        mixedClassFile.assertHasTests(2)
        mixedClassFile.assertHasFailures(1)
        mixedClassFile.assertHasIgnored(0)
        mixedClassFile.assertHasSuccessRate(50)
        mixedClassFile.assertHasLinkTo('../index', 'all')
        mixedClassFile.assertHasLinkTo('../packages/org.gradle.aggregation', 'org.gradle.aggregation')

        def failingTestDetails = mixedClassFile.testDetails('secondAlternative')
        failingTestDetails.assertDuration("1.100s");
        failingTestDetails.assertFailed();

        def failingPackageFile = results(reportDir.file('packages/org.gradle.aggregation.html'))
        failingPackageFile.assertHasFailedTest('../classes/org.gradle.aggregation.BarTest', 'secondAlternative')

        mixedClassFile.assertHasFailure('secondAlternative', 'something failed\n\nthis is the failure\nat someClass\n')

        where:
        numThreads << [1, 4]
    }

    def reportsOnClassesInDefaultPackage() {
        given:
        report = reportWithMaxThreads(1)
        def testTestResults = buildResults {
            testClassResult("Test") {
                testcase("test1") {
                    duration = 0
                }
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasLinkTo('packages/default-package', 'default-package')
        index.assertHasLinkTo('classes/Test', 'Test')

        def packageFile = results(reportDir.file('packages/default-package.html'))
        packageFile.assertHasLinkTo('../classes/Test', 'Test')
    }

    def escapesHtmlContentInReport() {
        given:
        report = reportWithMaxThreads(1)
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase("test1 < test2") {
                    failure("<a failure>", "<a failure>")

                    stdout "</html> & "
                    stderr "</div> & "
                }
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def testClassFile = results(reportDir.file('classes/org.gradle.Test.html'))
        testClassFile.assertHasTest('test1 < test2')
        testClassFile.assertHasFailure('test1 < test2', '<a failure>')
        testClassFile.assertHasStandardOutput('</html> & ')
        testClassFile.assertHasStandardError('</div> & ')
    }

    def encodesUnicodeCharactersInReport() {
        given:
        report = reportWithMaxThreads(1)
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase('\u0107') {
                    duration = 0
                    stdout "out:\u0256"
                    stderr "err:\u0102"
                }
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def testClassFile = results(reportDir.file('classes/org.gradle.Test.html'))
        testClassFile.assertHasTest('\u0107')
        testClassFile.assertHasStandardOutput('out:\u0256')
        testClassFile.assertHasStandardError('err:\u0102')
    }

    TestResultsProvider buildResults(Closure closure) {
        ConfigureUtil.configure(closure, new BuildableTestResultsProvider())
    }

    TestResultsProvider passingBuildResults() {
        buildResults {
            testClassResult("org.gradle.passing.Passed") {
                testcase("passed") {
                    duration = 1000;
                }
            }
            testClassResult("org.gradle.passing.subpackage.AlsoPassed") {
                testcase("passedToo") {
                    duration = 1000;
                    stdout "this is\nstandard output"
                    stderr "this is\nstandard error"
                }
            }
        }
    }

    TestResultsProvider failingBuildResults() {
        buildResults {
            testClassResult("org.gradle.passing.Passed") {
                testcase("passed") {
                    duration = 1000;
                }
            }
            testClassResult("org.gradle.passing.AlsoPassed") {
                testcase("passedToo") {
                    duration = 1000;
                    stdout "this is\nstandard output"
                    stderr "this is\nstandard error"
                }
            }
            testClassResult("org.gradle.ignoring.SomeIgnoredSomePassed") {
                testcase("passed") {
                    duration = 1000;
                }
                testcase("ignored") {
                    duration = 1000;
                    ignore()
                }
            }
            testClassResult("org.gradle.failing.SomeIgnoredSomePassedSomeFailed") {
                testcase("passed") {
                    duration = 1000;
                }
                testcase("ignored") {
                    duration = 1000;
                    ignore()
                }
                testcase("failed") {
                    duration = 1000;
                    failure("something failed", "this is the failure\nat someClass")
                }
            }
        }
    }

    TestResultsProvider aggregatedBuildResultsRun1() {
        buildResults {
            testClassResult("org.gradle.aggregation.FooTest") {
                testcase("first") {
                    duration = 1000;
                }
            }
            testClassResult("org.gradle.aggregation.BarTest") {
                testcase("second") {
                    duration = 1000;
                    stdout "this is\nstandard output"
                    stderr "this is\nstandard error"
                }
            }
        }
    }

    TestResultsProvider aggregatedBuildResultsRun2(methodNameSuffix = "") {
        buildResults {
            testClassResult("org.gradle.aggregation.FooTest") {
                testcase("first" + methodNameSuffix) {
                    duration = 1000;
                }
            }
            testClassResult("org.gradle.aggregation.BarTest") {
                testcase("second" + methodNameSuffix) {
                    duration = 1100;
                    stdout "failed on second run\nstandard output"
                    stderr "failed on second run\nstandard error"
                    failure("something failed", "this is the failure\nat someClass")
                }
            }
        }
    }

    def results(TestFile file) {
        return new HtmlTestResultsFixture(file)
    }

    def emptyResultSet() {
        _ * testResultProvider.visitClasses(_)
    }
}
