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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter
import org.gradle.tooling.internal.consumer.versioning.ModelMapping
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import org.gradle.tooling.internal.protocol.InternalActionAwareBuildController
import org.gradle.tooling.internal.protocol.InternalBuildControllerVersion2
import org.gradle.tooling.internal.protocol.InternalFailure
import org.gradle.tooling.internal.protocol.InternalFetchAwareBuildController
import org.gradle.tooling.internal.protocol.InternalFetchModelResult
import org.gradle.tooling.internal.protocol.InternalStreamedValueRelay
import org.gradle.util.GradleVersion
import spock.lang.Specification

class FetchAwareBuildControllerAdapterTest extends Specification {

    def delegate = Mock(FetchingBuildController)
    def controller = new FetchAwareBuildControllerAdapter(
        delegate,
        new ProtocolToModelAdapter(),
        new ModelMapping(),
        VersionDetails.from(GradleVersion.current()),
        new File("root")
    )

    def "preserves a shared failure's identity across fetch results"() {
        def shared = failure("shared", [])
        def rootA = failure("project :a failed", [shared])
        def rootB = failure("project :b failed", [shared])

        given:
        2 * delegate.fetch(null, _, null) >>> [result(rootA), result(rootB)]

        when:
        def failureA = controller.fetch(Object).failures.first()
        def failureB = controller.fetch(Object).failures.first()

        then:
        !failureA.is(failureB)
        failureA.causes.first().is(failureB.causes.first())
    }

    private InternalFailure failure(String message, List<InternalFailure> causes) {
        Stub(InternalFailure) {
            getMessage() >> message
            getOwnDescription() >> "java.lang.RuntimeException: $message\n"
            getDescription() >> "java.lang.RuntimeException: $message\n"
            getCauses() >> causes
            getProblems() >> []
        }
    }

    private InternalFetchModelResult<Object> result(InternalFailure failure) {
        Stub(InternalFetchModelResult) {
            getModel() >> null
            getFailures() >> [failure]
        }
    }

    private interface FetchingBuildController extends InternalBuildControllerVersion2, InternalActionAwareBuildController, InternalStreamedValueRelay, InternalFetchAwareBuildController {}
}
