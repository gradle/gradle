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



package org.gradle.initialization

import spock.lang.Specification
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.logging.ProgressLogger
import org.gradle.api.invocation.Gradle
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.BuildResult

class BuildProgressLoggerTest extends Specification {
    private final ProgressLoggerFactory progressLoggerFactory = Mock()
    private final ProgressLogger progressLogger = Mock()
    private final Gradle gradle = Mock()
    private final TaskExecutionGraph graph = Mock()
    private final BuildResult result = Mock()
    private final BuildProgressLogger logger = new BuildProgressLogger(progressLoggerFactory)

    def logsBuildStages() {
        _ * gradle.getTaskGraph() >> graph
        _ * result.getGradle() >> gradle

        when:
        logger.buildStarted(gradle)

        then:
        1 * progressLoggerFactory.start(BuildProgressLogger.name) >> progressLogger
        1 * progressLogger.progress('Loading')

        when:
        logger.graphPopulated(graph)

        then:
        1 * progressLogger.progress('Building')

        when:
        logger.buildFinished(result)

        then:
        1 * progressLogger.completed()
    }

    def ignoresNestedBuilds() {
        _ * gradle.getParent() >> Mock(Gradle)
        
        when:
        logger.buildStarted(gradle)

        then:
        0 * progressLoggerFactory._
    }
}
