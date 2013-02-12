/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.initialization

import org.gradle.api.artifacts.ResolvableDependencies
import org.gradle.logging.ProgressLogger
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification

class DependencyResolutionLoggerTest extends Specification {
    ProgressLoggerFactory loggerFactory = Stub()
    ResolvableDependencies dependencies = Mock()
    ProgressLogger progressLogger = Mock()
    DependencyResolutionLogger logger = new DependencyResolutionLogger(loggerFactory)

    def "generates progress logging events as dependency sets are resolved"() {
        def progressLoggerFactory = Mock(ProgressLoggerFactory)
        logger = new DependencyResolutionLogger(progressLoggerFactory)
        when:
        logger.beforeResolve(dependencies)

        then:
        1 * progressLoggerFactory.newOperation(DependencyResolutionLogger) >> progressLogger
        1 * progressLogger.setDescription("Resolve ${dependencies}")
        1 * progressLogger.setShortDescription("Resolving ${dependencies}")
        1 * progressLogger.started()
        0 * progressLogger._

        when:
        logger.afterResolve(dependencies)

        then:
        1 * progressLogger.completed()
        0 * progressLogger._
    }

    def "stacks loggers in case resolution triggers nested resolution"() {
        def otherDeps = Mock(ResolvableDependencies, name: "otherDeps")
        def otherLogger = Mock(ProgressLogger, name: "otherLogger")

        loggerFactory.newOperation(_) >>> [progressLogger, otherLogger]

        when:
        logger.beforeResolve(dependencies)
        logger.beforeResolve(otherDeps)

        and:
        logger.afterResolve(otherDeps)
        logger.afterResolve(dependencies)

        then:
        1 * otherLogger.completed()
        then:
        1 * progressLogger.completed()

        when:
        logger.afterResolve(dependencies)
        then:
        thrown(IllegalStateException)
    }

    def "cannot complete resolution without starting it first"() {
        when:
        logger.afterResolve(dependencies)

        then:
        thrown(IllegalStateException)
    }

    def "fails fast if afterResolve called multiple times"() {
        when:
        logger.beforeResolve(dependencies)

        logger.afterResolve(dependencies)
        logger.afterResolve(dependencies) //again

        then:
        thrown(IllegalStateException)
    }
}
