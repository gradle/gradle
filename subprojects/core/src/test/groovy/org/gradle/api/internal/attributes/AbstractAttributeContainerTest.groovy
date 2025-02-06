/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.attributes

import org.gradle.api.attributes.Attribute
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.logging.CollectingTestOutputEventListener
import org.gradle.internal.logging.ConfigureLogging
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.internal.problems.NoOpProblemDiagnosticsFactory
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Abstract base class for testing functionality common to all {@link AbstractAttributeContainer} implementations.
 */
/* package */ abstract class AbstractAttributeContainerTest extends Specification {
    protected final CollectingTestOutputEventListener outputEventListener = new CollectingTestOutputEventListener()
    @Rule
    protected final ConfigureLogging logging = new ConfigureLogging(outputEventListener)

    def setup() {
        def diagnosticsFactory = new NoOpProblemDiagnosticsFactory()
        def buildOperationProgressEventEmitter = Mock(BuildOperationProgressEventEmitter)
        DeprecationLogger.reset()
        DeprecationLogger.init(WarningMode.All, buildOperationProgressEventEmitter, TestUtil.problemsService(), diagnosticsFactory.newUnlimitedStream())
    }

    /**
     * Returns a new instance of the container type tested by this class.
     *
     * @param attributes optional map of attributes with values to populate the container with if present
     * @return the container, populated with any given attributes from the argument
     */
    protected abstract <T> AbstractAttributeContainer getContainer(Map<Attribute<T>, T> attributes = [:])

    def "requesting a null key emits a deprecation message using #containerStatus"() {
        when:
        def result = preppedContainer.getAttribute(null)

        then:
        result == null

        and:
        def events = outputEventListener.events.findAll { it.logLevel == LogLevel.WARN }
        events.size() == 1
        events[0].message.startsWith('Using method getAttribute with a null key has been deprecated.')

        where:
        containerStatus                 | preppedContainer
        "empty container"               | getContainer()
        "container with elements"       | getContainer([(Attribute.of("testString", String)): "testValue", (Attribute.of("testInt", Integer)): 1])
    }
}
