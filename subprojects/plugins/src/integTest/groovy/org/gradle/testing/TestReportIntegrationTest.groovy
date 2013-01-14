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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.junit.Rule
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample

class TestReportIntegrationTest extends AbstractIntegrationSpec {
    @Rule Sample sample

    // TODO - use default value for bin results dir, and deprecate
    // TODO - auto-wiring for test results
    // TODO - warn when duplicate class results are discarded
    // TODO - sample and int test
    // TODO - user guide + dsl guide
    // TODO - extract some kind of resolving collection

    @UsesSample("testing/testReport")
    def "can generate report for subprojects"() {
        given:
        sample sample

        when:
        run "testReport"

        then:
        def reportDir = sample.dir.file("build/reports/allTests")
        reportDir.file("index.html").assertIsFile()
        reportDir.file("org.gradle.sample.CoreTest.html").text.contains("hello from CoreTest.")
        reportDir.file("org.gradle.sample.UtilTest.html").text.contains("hello from UtilTest.")
    }
}
