/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.performance.results.ReportGenerator
import org.gradle.performance.results.ResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AbstractPerformanceTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    static def resultStore = new ResultsStore(new File(System.getProperty("user.home"), ".gradle-performance-test-data/results"))
    static def textReporter = new TextFileDataReporter(new File("build/performance-tests/results.txt"))

    final def runner = new PerformanceTestRunner(
            testDirectoryProvider: tmpDir,
            current: new UnderDevelopmentGradleDistribution(),
            runs: 5,
            warmUpRuns: 1,
            targetVersions: ['1.0', '1.4', 'last']
    )

    def setup() {
        runner.reporter = new CompositeDataReporter([textReporter, resultStore])
    }

    static {
        // TODO - find a better way to generate the report (eg move to a finalizer task)
        System.addShutdownHook {
            resultStore.close()
            new ReportGenerator().generate(resultStore, new File("build/performance-tests/report"))
            resultStore.close()
        }
    }
}
