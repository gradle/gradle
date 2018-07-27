/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.internal.logging.progress.ProgressLogger
import spock.lang.Specification
import spock.lang.Subject

class DefaultCleanupProgressMonitorTest extends Specification {

    def progressLogger = Mock(ProgressLogger) {
        getDescription() >> "Progress"
    }

    @Subject def progressMonitor = new DefaultCleanupProgressMonitor(progressLogger)

    def "reports deleted and skipped"() {
        when:
        progressMonitor.incrementDeleted()

        then:
        1 * progressLogger.progress("Progress: 1 entry deleted")

        when:
        progressMonitor.incrementSkipped()

        then:
        1 * progressLogger.progress("Progress: 1 entry deleted, 1 skipped")

        when:
        progressMonitor.incrementDeleted()

        then:
        1 * progressLogger.progress("Progress: 2 entries deleted, 1 skipped")
    }

    def "always reports deleted, even if all entries are skipped"() {
        when:
        progressMonitor.incrementSkipped()

        then:
        1 * progressLogger.progress("Progress: 0 entries deleted, 1 skipped")
    }
}
