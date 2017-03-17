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
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _) >> buildProgress
        0 * _

        when:
        buildProgressLogger.settingsEvaluated()

        then:
        1 * buildProgress.completed()
        0 * _
    }

    def "logs configuration phase"() {
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.settingsEvaluated()

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _) >> buildProgress

        when:
        buildProgressLogger.projectsLoaded(16)

        then:
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _) >> buildProgress
        0 * _

        when:
        buildProgressLogger.beforeEvaluate(":foo:bar")

        then:
        0 * _

        when:
        buildProgressLogger.afterEvaluate(":foo:bar")

        then:
        1 * buildProgress.progress(_)
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
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _) >> buildProgress
        1 * provider.start(BuildProgressLogger.EXECUTION_PHASE_DESCRIPTION, _) >> buildProgress

        when:
        buildProgressLogger.afterExecute()

        then:
        1 * buildProgress.progress(_)
        0 * _

        when:
        buildProgressLogger.buildFinished()

        then:
        1 * buildProgress.completed()
        0 * _
    }

    def "don't log progress for projects configured after official configuration phase"() {
        //currently this can happen, see the ConfigurationOnDemandIntegrationTest
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.settingsEvaluated()
        buildProgressLogger.projectsLoaded(16)
        buildProgressLogger.graphPopulated(10)

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _) >> buildProgress
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _) >> buildProgress
        1 * provider.start(BuildProgressLogger.EXECUTION_PHASE_DESCRIPTION, _) >> buildProgress

        when:
        buildProgressLogger.beforeEvaluate(":foo")
        buildProgressLogger.afterEvaluate(":bar")

        then:
        0 * _
    }

    def "build finished cleans up configuration logger"() {
        when:
        buildProgressLogger.buildStarted()
        buildProgressLogger.settingsEvaluated()
        buildProgressLogger.projectsLoaded(16)

        then:
        1 * provider.start(BuildProgressLogger.INITIALIZATION_PHASE_DESCRIPTION, _) >> buildProgress
        1 * provider.start(BuildProgressLogger.CONFIGURATION_PHASE_DESCRIPTION, _) >> buildProgress

        when:
        buildProgressLogger.buildFinished()

        then:
        1 * buildProgress.completed()
        0 * _
    }
}
