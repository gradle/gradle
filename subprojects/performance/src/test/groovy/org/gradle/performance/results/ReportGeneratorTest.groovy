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

package org.gradle.performance.results

import org.gradle.performance.ResultSpecification
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule

class ReportGeneratorTest extends ResultSpecification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final ReportGenerator generator = new ReportGenerator()
    final dbFile = tmpDir.file("results")
    final reportDir = tmpDir.file("report")

    def "generates report"() {
        setup:
        def store = new ResultsStore(dbFile)
        def result2 = results()
        result2.current << operation()
        result2.current << operation()
        store.report(result2)
        store.close()

        when:
        generator.generate(store, reportDir)

        then:
        reportDir.file("index.html").isFile()
    }
}
