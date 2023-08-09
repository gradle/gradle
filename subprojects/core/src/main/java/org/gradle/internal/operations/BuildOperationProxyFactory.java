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
    public static RunnableBuildOperation createRunnableProxy(final RunnableBuildOperation runnable, Problems problems) {
        return new RunnableBuildOperationContextProxy(runnable, problems);
    }

    public static <T> CallableBuildOperation<T> createCallableProxy(final CallableBuildOperation<T> callable, Problems problems) {
        return new CallableBuildOperationContextProxy<T>(callable, problems);
    }

    private static class BuildOperationProxy{
        protected final Problems problems;

        public BuildOperationProxy(Problems problems) {
            this.problems = problems;
        }

        protected RuntimeException collectAndThrowCause(GradleExceptionWithProblem e) {
            e.getProblems().forEach(problems::collectError);
            throw throwAsUncheckedException(e.getCause());
        }

    }

    private static class RunnableBuildOperationContextProxy extends BuildOperationProxy implements RunnableBuildOperation {
        private final RunnableBuildOperation runnable;

        public RunnableBuildOperationContextProxy(RunnableBuildOperation runnable, Problems problems) {
            super(problems);
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
                problems.collectError(t);
                throw t;
            }
        }
    }

    private static class CallableBuildOperationContextProxy<T>  extends BuildOperationProxy implements CallableBuildOperation<T> {
        private final CallableBuildOperation<T> callable;

        public CallableBuildOperationContextProxy(CallableBuildOperation<T> callable, Problems problems) {
            super(problems);
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
                problems.collectError(t);
                throw t;
            }
        }
    }
}
