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

package org.gradle.internal.instrumentation.agent

import spock.lang.Specification

import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

class AgentInitializerTest extends Specification {

    def setup() {
        resetInstalled()
    }

    def cleanup() {
        resetInstalled()
    }

    def "ensureInstrumentationAgentConfigured does not throw when called twice"() {
        given:
        def initializer = new AgentInitializer(enabledAgentStatus())

        when:
        initializer.ensureInstrumentationAgentConfigured()
        initializer.ensureInstrumentationAgentConfigured()

        then:
        noExceptionThrown()
    }

    def "maybeConfigureInstrumentationAgent throws when called after the transformer is already installed"() {
        given:
        def initializer = new AgentInitializer(enabledAgentStatus())

        when:
        initializer.maybeConfigureInstrumentationAgent()
        initializer.maybeConfigureInstrumentationAgent()

        then:
        thrown(IllegalStateException)
    }

    private static AgentStatus enabledAgentStatus() {
        return new AgentStatus() {
            @Override
            boolean isAgentInstrumentationEnabled() { true }
        }
    }

    private static void resetInstalled() {
        Field field = DefaultClassFileTransformer.getDeclaredField("INSTALLED")
        field.setAccessible(true)
        ((AtomicBoolean) field.get(null)).set(false)
    }
}
