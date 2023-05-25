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

public class BuildOperationProxyFactory {
    public static RunnableBuildOperation createRunnableProxy(final RunnableBuildOperation runnable) {
        return new RunnableBuildOperation() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return runnable.description();
            }

            @Override
            public void run(BuildOperationContext context) throws Exception {
                try {
                    BuildOperationContextTracker.push(context);
                    runnable.run(context);
                } finally {
                    BuildOperationContextTracker.pop();
                }
            }
        };
    }

    public static <T> CallableBuildOperation<T> createCallableProxy(final CallableBuildOperation<T> callable) {
        return new CallableBuildOperation<T>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return callable.description();
            }

            @Override
            public T call(BuildOperationContext context) throws Exception {
                try {
                    BuildOperationContextTracker.push(context);
                    return callable.call(context);
                } finally {
                    BuildOperationContextTracker.pop();
                }
            }
        };
    }
}
