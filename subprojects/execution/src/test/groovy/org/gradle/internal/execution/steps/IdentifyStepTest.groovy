/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.steps

import org.gradle.internal.execution.ExecutionRequestContext
import org.gradle.internal.execution.IdentityContext
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.snapshot.ValueSnapshot
import org.gradle.internal.snapshot.ValueSnapshotter

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.NON_IDENTITY

class IdentifyStepTest extends StepSpec<ExecutionRequestContext> {
    def delegateResult = Mock(Result)
    def valueSnapshotter = Stub(ValueSnapshotter) {
        snapshot(_ as Object) >> Mock(ValueSnapshot)
        snapshot(_ as Object, _ as ValueSnapshot) >> Mock(ValueSnapshot)
    }
    def step = new IdentifyStep<>(valueSnapshotter, delegate)

    @Override
    protected ExecutionRequestContext createContext() {
        Stub(ExecutionRequestContext)
    }

    def "delegates with assigned workspace"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        _ * work.visitInputProperties(_) >> { UnitOfWork.InputPropertyVisitor visitor ->
            visitor.visitInputProperty("non-identity", NON_IDENTITY) { Mock(Object) }
            visitor.visitInputProperty("identity", UnitOfWork.IdentityKind.IDENTITY) { 123 }
        }
        _ * work.visitInputFileProperties(_) >> { UnitOfWork.InputFilePropertyVisitor visitor ->
            visitor.visitInputFileProperty(
                "non-identity-file",
                UnitOfWork.InputPropertyType.NON_INCREMENTAL,
                NON_IDENTITY,
                Mock(Object),
                { -> throw new RuntimeException("Shouldn't fingerprint this") })
            visitor.visitInputFileProperty(
                "identity-file",
                UnitOfWork.InputPropertyType.NON_INCREMENTAL,
                UnitOfWork.IdentityKind.IDENTITY,
                Mock(Object),
                { -> Mock(CurrentFileCollectionFingerprint) })
        }

        1 * delegate.execute(_) >> { IdentityContext delegateContext ->
            assert delegateContext.inputProperties.keySet() == ["identity"] as Set
            assert delegateContext.inputFileProperties.keySet() == ["identity-file"] as Set
            delegateResult
        }
    }
}
