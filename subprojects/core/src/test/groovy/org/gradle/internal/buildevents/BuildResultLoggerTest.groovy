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
import org.gradle.execution.WorkValidationWarningReporter
import org.gradle.internal.logging.format.DurationFormatter
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.time.Clock
import org.gradle.internal.time.FixedClock
import org.gradle.util.internal.TextUtil
import spock.lang.Specification
import spock.lang.Subject

import java.util.concurrent.TimeUnit

@Subject(BuildResultLogger)
class BuildResultLoggerTest extends Specification {
    private static final long BUILD_START_MS = 0L
    private static final long BUILD_DURATION_MS = TimeUnit.SECONDS.toMillis(10)

    private BuildStartedTime buildStartedTime = BuildStartedTime.startingAt(BUILD_START_MS)
    private Clock clock = FixedClock.createAt(BUILD_START_MS + BUILD_DURATION_MS)

    private StyledTextOutputFactory textOutputFactory = new TestStyledTextOutputFactory()
    private DurationFormatter durationFormatter = Mock(DurationFormatter)
    def workValidationWarningReporter = Stub(WorkValidationWarningReporter)
    private BuildResultLogger subject = new BuildResultLogger(textOutputFactory, buildStartedTime, clock, durationFormatter, workValidationWarningReporter)

    def "logs build success with total time"() {
        when:
        subject.buildFinished(new BuildResult("Action", null, null));

        then:
        1 * durationFormatter.format(BUILD_DURATION_MS) >> { "10s" }
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{LIFECYCLE}\n{successheader}ACTION SUCCESSFUL{normal} in 10s\n"
    }

    def "logs build failure with total time"() {
        when:
        subject.buildFinished(new BuildResult("Action", null, new RuntimeException()));

        then:
        1 * durationFormatter.format(BUILD_DURATION_MS) >> { "10s" }
        TextUtil.normaliseLineSeparators(textOutputFactory as String) == "{org.gradle.internal.buildevents.BuildResultLogger}{ERROR}\n{failureheader}ACTION FAILED{normal} in 10s\n"
    }
}
