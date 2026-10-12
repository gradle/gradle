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
package org.gradle.api.plugins

import spock.lang.Specification

/**
 * Unit tests for {@link UnknownPluginException}.
 */
class UnknownPluginExceptionTest extends Specification {
    def "a cause can be attached after construction"() {
        // Callers that need both a cause and a plugin id rely on these constructors leaving the cause
        // uninitialized, since initializing it - even to null - makes initCause(...) throw.
        given:
        def cause = new RuntimeException('boom')

        when:
        constructed.initCause(cause)

        then:
        constructed.cause == cause
        constructed.pluginId == expectedPluginId

        where:
        constructed                                              | expectedPluginId
        new UnknownPluginException('message')                    | null
        new UnknownPluginException('message', 'some.plugin.id')  | 'some.plugin.id'
        new UnknownPluginException('message', (String) null)     | null
    }
}
