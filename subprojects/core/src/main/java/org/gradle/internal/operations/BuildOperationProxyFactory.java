/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.GradleExceptionWithProblem;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class BuildOperationProxyFactory {
    public static RunnableBuildOperation createRunnableProxy(final RunnableBuildOperation runnable) {
        return new RunnableBuildOperationContextProxy(runnable);
    }

    public static <T> CallableBuildOperation<T> createCallableProxy(final CallableBuildOperation<T> callable) {
        return new CallableBuildOperationContextProxy<T>(callable);
    }

    private static class RunnableBuildOperationContextProxy implements RunnableBuildOperation {
        private final RunnableBuildOperation runnable;

        public RunnableBuildOperationContextProxy(RunnableBuildOperation runnable) {
            this.runnable = runnable;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return runnable.description();
        }

        @Override
        public void run(BuildOperationContext context) throws Exception {
            try {
                runnable.run(context);
            } catch (GradleExceptionWithProblem e) {
                throw collectAndThrowCause(e);
            } catch (Throwable t) {
                Problems.collect(t);
                throw t;
            }
        }
    }

    private static class CallableBuildOperationContextProxy<T> implements CallableBuildOperation<T> {
        private final CallableBuildOperation<T> callable;

        public CallableBuildOperationContextProxy(CallableBuildOperation<T> callable) {
            this.callable = callable;
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return callable.description();
        }

        @Override
        public T call(BuildOperationContext context) throws Exception {
            try {
                return callable.call(context);
            } catch (GradleExceptionWithProblem e) {
                throw collectAndThrowCause(e);
            } catch (Throwable t) {
                Problems.collect(t);
                throw t;
            }
        }
    }

    private static RuntimeException collectAndThrowCause(GradleExceptionWithProblem e) {
        e.getProblems().forEach(Problems::collect);
        throw throwAsUncheckedException(e.getCause());
    }
}
