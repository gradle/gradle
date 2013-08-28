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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.UnknownModelException
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.protocol.InternalBuildController
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException
import spock.lang.Specification

class BuildControllerAdapterTest extends Specification {
    def internalController = Stub(InternalBuildController)
    def controller = new BuildControllerAdapter(Stub(ProtocolToModelAdapter), internalController, Stub(ModelMapping))

    def "unpacks unsupported model exception"() {
        def failure = new RuntimeException()

        given:
        _ * internalController.getModel(_) >> { throw new InternalUnsupportedModelException().initCause(failure) }

        when:
        controller.getModel(String)

        then:
        UnknownModelException e = thrown()
        e.message == /No model of type 'String' is available in this build./
        e.cause == failure
    }
}
