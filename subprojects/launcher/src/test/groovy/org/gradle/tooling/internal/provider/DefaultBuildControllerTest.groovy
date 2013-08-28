/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider

import org.gradle.api.internal.GradleInternal
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import org.gradle.tooling.internal.protocol.ModelIdentifier
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.tooling.provider.model.UnknownModelException
import spock.lang.Specification

class DefaultBuildControllerTest extends Specification {
    def gradle = Stub(GradleInternal)
    def registry = Stub(ToolingModelBuilderRegistry)
    def controller = new DefaultBuildController(gradle, registry)

    def "adapts model not found exception to protocol exception"() {
        def modelId = Stub(ModelIdentifier) {
            getName() >> 'some.model'
        }
        def failure = new UnknownModelException("not found")

        given:
        _ * registry.getBuilder('some.model') >> { throw failure }

        when:
        controller.getModel(null, modelId)

        then:
        InternalUnsupportedModelException e = thrown()
        e.cause == failure
    }
}
