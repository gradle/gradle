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
package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JacocoCoverage
import org.gradle.testing.jacoco.plugins.fixtures.JacocoReportFixture


@TargetCoverage({ JacocoCoverage.supportedVersionsByJdk })
class JacocoVersionCompatibilityIntegrationTest extends JacocoMultiVersionIntegrationTest {

    def "can run versions (offline: #offline)"() {
        given:
        javaProjectUnderTest.writeSourceFiles()
        javaProjectUnderTest.writeOfflineInstrumentation(offline)

        when:
        succeeds('test', 'jacocoTestReport')

        then:
        def report = htmlReport()
        report.totalCoverage() == 100
        report.assertVersion(version)

        where:
        offline << [false, true]
    }

    private JacocoReportFixture htmlReport(String basedir = "build/reports/jacoco/test/html") {
        return new JacocoReportFixture(file(basedir))
    }
}
