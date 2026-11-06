/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class AggregateTestReportRendererTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "links to reports relative to the aggregate report"() {
        def reportDirectory = tmpDir.file("build/reports/aggregate-test-results").toPath()

        expect:
        AggregateTestReportRenderer.link(reportDirectory, tmpDir.file(report).toPath()) == link

        where:
        report                                      | link
        "build/reports/tests/test/index.html"       | "../tests/test/index.html"
        "sub/build/reports/tests/test/index.html"   | "../../../sub/build/reports/tests/test/index.html"
        "build/reports/tests/with space/index.html" | "../tests/with%20space/index.html"
        "build/reports/tests/a#b?c/index.html"      | "../tests/a%23b%3Fc/index.html"
    }

}
