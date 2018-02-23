/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.progress

import org.gradle.internal.logging.progress.ProgressLogger
import spock.lang.Specification
import spock.lang.Subject

class BuildProgressLoggerTest extends Specification {
    ProgressLoggerProvider provider = Mock()
    ProgressLogger buildProgress = Mock()

    @Subject buildProgressLogger = new BuildProgressLogger(provider)

    def "logs initialization phase"() {
        when:
        buildProgressLogger.buildStarted()

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress
        0 * _

        when:
        buildProgressLogger.projectsLoaded(1)

        then:
        1 * buildProgress.completed()
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _, 1) >> buildProgress
        0 * _
    }

    def "logs progress of nested build tasks during initialization phase"() {
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.nestedTaskGraphPopulated(50)

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress
        1 * buildProgress.completed()
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 50) >> buildProgress
        0 * _

        when:
        buildProgressLogger.afterNestedExecute(false)

        then:
        1 * buildProgress.progress("", false)
        0 * _

        when:
        buildProgressLogger.projectsLoaded(1)

        then:
        1 * buildProgress.completed()
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _, 1) >> buildProgress
        0 * _

        when:
        buildProgressLogger.afterNestedExecute(false)

        then:
        0 * _
    }

    def "logs configuration phase"() {
        when:
        buildProgressLogger.buildStarted()

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress

        when:
        buildProgressLogger.projectsLoaded(16)

        then:
        1 * buildProgress.completed()
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _, 16) >> buildProgress
        0 * _

        when:
        buildProgressLogger.beforeEvaluate(":foo:bar")

        then:
        0 * _

        when:
        buildProgressLogger.afterEvaluate(":foo:bar")

        then:
        1 * buildProgress.progress(_,false)
        0 * _

        when:
        buildProgressLogger.graphPopulated(10)

        then:
        1 * buildProgress.completed()
    }

    def "logs execution phase"() {
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.graphPopulated(10)

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress
        1 * provider.start(BuildProgressLogger.EXECUTION_PHASE_DESCRIPTION, _, 10) >> buildProgress

        when:
        buildProgressLogger.afterExecute(false)

        then:
        1 * buildProgress.progress(_, false)
        0 * _

        when:
        buildProgressLogger.beforeComplete()

        then:
        1 * buildProgress.completed(_, false)
        0 * _
    }

    def "don't log progress for projects configured after official configuration phase"() {
        //currently this can happen, see the ConfigurationOnDemandIntegrationTest
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.projectsLoaded(16)
        buildProgressLogger.graphPopulated(10)

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _, 16) >> buildProgress
        1 * provider.start(BuildProgressLogger.EXECUTION_PHASE_DESCRIPTION, _, 10) >> buildProgress

        when:
        buildProgressLogger.beforeEvaluate(":foo")
        buildProgressLogger.afterEvaluate(":bar")

        then:
        0 * _
    }

    def "build finished cleans up configuration logger"() {
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.projectsLoaded(16)

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _, 16) >> buildProgress

        when:
        buildProgressLogger.beforeComplete()

        then:
        1 * buildProgress.completed(_, false)
        0 * _
    }

    def "logs waiting message after build is complete but session is not"() {
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.beforeComplete()

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _, 0) >> buildProgress
        1 * buildProgress.completed({it.contains(BuildProgressLogger.WAITING_PHASE_DESCRIPTION)}, false)
        0 * _
    }
}
