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

import org.gradle.api.GradleException
import spock.lang.Specification

class GenericHtmlReportGenerationExceptionTest extends Specification {
    def "propagates resolutions from a ResolutionProvider cause"() {
        given:
        def cause = new GradleException("underlying")
        cause.addResolution("Try clearing the report directory.")

        when:
        def exception = new GenericHtmlReportGenerationException("failed to generate report", cause)

        then:
        exception.getResolutions() == ["Try clearing the report directory."]
    }

    def "has no resolutions when the cause is not a ResolutionProvider"() {
        given:
        def cause = new RuntimeException("plain cause")

        when:
        def exception = new GenericHtmlReportGenerationException("failed to generate report", cause)

        then:
        exception.getResolutions().isEmpty()
    }

    def "can add additional resolutions"() {
        given:
        def cause = new GradleException("underlying")
        cause.addResolution("Try clearing the report directory.")

        when:
        def exception = new GenericHtmlReportGenerationException("failed to generate report", cause)
        exception.addResolution("Additional resolution.")

        then:
        exception.getResolutions() == ["Try clearing the report directory.", "Additional resolution."]
    }
}
