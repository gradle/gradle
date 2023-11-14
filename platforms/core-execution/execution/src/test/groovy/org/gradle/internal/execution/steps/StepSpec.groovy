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

import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableMap
import org.codehaus.groovy.runtime.InvokerHelper
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.spockframework.mock.EmptyOrDummyResponse
import org.spockframework.mock.IDefaultResponse
import org.spockframework.mock.IMockInvocation
import spock.lang.Specification

import java.lang.reflect.ParameterizedType
import java.util.function.Consumer

abstract class StepSpec<C extends Context> extends Specification {
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
    final buildOperationExecutor = new TestBuildOperationExecutor()

    final workId = ":test"
    final displayName = "job '$workId'"
    final identity = Stub(UnitOfWork.Identity) {
        uniqueId >> workId
    }
    final delegate = Mock(DeferredExecutionAwareStep)
    final work = Stub(UnitOfWork)
    final C context = createContext()

    /**
     * Spock helper to mock Guava's immutable collections and maps with empty instances.
     */
    static class GuavaImmutablesResponse implements IDefaultResponse {
        static final IDefaultResponse INSTANCE = new GuavaImmutablesResponse()

        @Override
        Object respond(IMockInvocation invocation) {
            if (ImmutableCollection.isAssignableFrom(invocation.method.returnType)
                || ImmutableMap.isAssignableFrom(invocation.method.returnType)) {
                return InvokerHelper.invokeStaticMethod(invocation.method.returnType, "of", null)
            } else {
                return EmptyOrDummyResponse.INSTANCE.respond(invocation);
            }
        }
    }

    /**
     * Create a stub context based on the type parameter of the extended StepSpec.
     */
    private C createContext() {
        def contextType = (getClass().getGenericSuperclass() as ParameterizedType).actualTypeArguments[0] as Class<C>
        return Stub(contextType, defaultResponse: GuavaImmutablesResponse.INSTANCE) as C
    }

    def setup() {
        _ * context.identity >> identity
        _ * work.displayName >> displayName
        _ * work.identify(_, _) >> identity
    }

    protected TestFile file(Object... path) {
        return temporaryFolder.file(path)
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
