/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.TestBuildOperationExecutor

import java.util.function.Consumer

abstract class StepSpec<C extends Context> extends StepSpecBase<C> {
    final buildOperationExecutor = new TestBuildOperationExecutor()

    final delegate = Mock(DeferredExecutionAwareStep)
    final work = Stub(UnitOfWork)

    def setup() {
        _ * work.displayName >> displayName
        _ * work.identify(_, _) >> identity
    }

    protected void assertNoOperation() {
        assert buildOperationExecutor.log.records.empty
    }

    protected <T, R> void assertSuccessfulOperation(Class<BuildOperationType<T, R>> operationType, String displayName, R result) {
        withOnlyOperation(operationType) {
            assert it.descriptor.displayName == displayName
            assert it.result == result
        }
    }

    protected <T, R> void assertFailedOperation(Class<BuildOperationType<T, R>> operationType, String displayName, Throwable expectedFailure) {
        withOnlyOperation(operationType) {
            assert it.descriptor.displayName == displayName
            assert it.failure == expectedFailure
        }
    }

    protected <D, R, T extends BuildOperationType<D, R>> void withOnlyOperation(
        Class<T> operationType,
        Consumer<TestBuildOperationExecutor.Log.TypedRecord<D, R>> verifier
    ) {
        assert buildOperationExecutor.log.records.size() == 1
        interaction {
            verifier.accept(buildOperationExecutor.log.mostRecent(operationType))
        }
    }
}
