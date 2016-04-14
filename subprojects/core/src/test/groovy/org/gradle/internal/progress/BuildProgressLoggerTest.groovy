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
    ProgressLogger progress = Mock()
    ProgressLogger confProgress = Mock()

    @Subject logger = new BuildProgressLogger(provider)

    def "logs initialisation stage"() {
        when: logger.buildStarted()

        then:
        1 * provider.start('Initialize build', 'Loading') >> progress
        0 * _

        when: logger.settingsEvaluated()

        then:
        1 * progress.progress("Configuring")
        0 * _

        when: logger.projectsLoaded(6)

        then:
        1 * provider.start("Configure projects", '0/6 projects') >> confProgress
        0 * _
    }

    def "logs configuration progress"() {
        def progress1 = Mock(ProgressLogger)
        def progress2 = Mock(ProgressLogger)

        when:
        logger.projectsLoaded(16)
        logger.beforeEvaluate(":")
        logger.beforeEvaluate(":foo:bar")

        then:
        1 * provider.start("Configure projects", '0/16 projects') >> confProgress
        1 * provider.start("Configure project :", 'root project') >> progress1
        1 * provider.start("Configure project :foo:bar", ':foo:bar') >> progress2
        0 * _

        when: logger.afterEvaluate(":foo:bar")

        then:
        1 * progress2.completed()
        1 * confProgress.progress("1/16 projects")
        0 * _

        when: logger.afterEvaluate(":")

        then:
        1 * progress1.completed()
        1 * confProgress.progress("2/16 projects")
        0 * _
    }

    def "logs configuration completion"() {
        when:
        logger.buildStarted()
        logger.projectsLoaded(16)
        logger.graphPopulated(10)

        then:
        1 * provider.start("Configure projects", _) >> confProgress
        1 * provider.start('Initialize build', _) >> progress
        0 * _

        then:
        1 * confProgress.completed()
        1 * progress.completed("Task graph ready")
        1 * provider.start("Execute tasks", "Building 0%");
        0 * _
    }

    def "logs execution progress"() {
        def executeProgress = Mock(ProgressLogger)

        when:
        logger.buildStarted()
        logger.projectsLoaded(16)
        logger.graphPopulated(10)

        then:
        1 * provider.start("Configure projects", _) >> confProgress
        1 * provider.start('Initialize build', _) >> progress
        1 * provider.start("Execute tasks", _) >> executeProgress

        when:
        logger.afterExecute()
        logger.afterExecute()

        then:
        1 * executeProgress.progress("Building 10%")
        1 * executeProgress.progress("Building 20%")
        0 * _

        when: logger.buildFinished()

        then:
        1 * executeProgress.completed()
        0 * _
    }

    def "don't log progress for projects configured after official configuration phase"() {
        //currently this can happen, see the ConfigurationOnDemandIntegrationTest
        when:
        logger.buildStarted()
        logger.projectsLoaded(16)
        logger.graphPopulated(10)

        then:
        1 * provider.start("Configure projects", _) >> confProgress
        1 * provider.start('Initialize build', _) >> progress

        when:
        logger.beforeEvaluate(":foo")
        logger.afterEvaluate(":bar")

        then:
        0 * _
    }

    def "build finished cleans up configuration logger"() {
        when:
        logger.buildStarted()
        logger.projectsLoaded(16)
        logger.buildFinished()

        then:
        1 * provider.start('Initialize build', _) >> progress
        1 * provider.start("Configure projects", _) >> confProgress
        1 * confProgress.completed()
    }

    def "build finished cleans up any unfinished configuration loggers"() {
        def progress1 = Mock(ProgressLogger)

        when:
        logger.buildStarted()
        logger.projectsLoaded(16)
        logger.beforeEvaluate(":")
        logger.buildFinished()

        then:
        1 * provider.start('Initialize build', _) >> progress
        1 * provider.start("Configure project :", 'root project') >> progress1
        1 * provider.start("Configure projects", _) >> confProgress
        1 * progress1.completed()
    }
}
