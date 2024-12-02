/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.buildevents

import org.gradle.BuildResult
import org.gradle.api.logging.LogLevel
import org.gradle.execution.WorkValidationWarningReporter
import org.gradle.internal.logging.format.DurationFormatter
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.time.Clock
import spock.lang.Specification
import spock.lang.Subject

@Subject(BuildResultLogger)
class BuildResultLoggerTest extends Specification {
    private StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    private BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(0)
    private Clock clock = Mock(Clock)
    private DurationFormatter durationFormatter = Mock(DurationFormatter)
    def workValidationWarningReporter = Stub(WorkValidationWarningReporter)
    private BuildResultLogger subject = new BuildResultLogger(textOutputFactory, buildStartedTime, clock, durationFormatter, workValidationWarningReporter)

    def "logs build success with total time"() {
        when:
        1 * clock.currentTime >> 10
        subject.buildFinished(new BuildResult("Action", null, null));

        then:
        1 * durationFormatter.format(10L) >> { "10s" }
        textOutputFactory.category == BuildResultLogger.canonicalName
        textOutputFactory.logLevel == LogLevel.LIFECYCLE
        textOutputFactory.output == """
{successheader}ACTION SUCCESSFUL{normal} in 10s
"""
    }

    def "logs build failure with total time"() {
        when:
        1 * clock.currentTime >> 10
        subject.buildFinished(new BuildResult("Action", null, new RuntimeException()));

        then:
        1 * durationFormatter.format(10L) >> { "10s" }
        textOutputFactory.category == BuildResultLogger.canonicalName
        textOutputFactory.logLevel == LogLevel.ERROR
        textOutputFactory.output == """
{failureheader}ACTION FAILED{normal} in 10s
"""
    }
}
