/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.progress;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.operations.BuildOperationContext;

/**
 * A BuildOperationExecutor for tests.
 * Simply execute given operations, does not support current/parent operations.
 */
public class TestBuildOperationExecutor implements BuildOperationExecutor {
    @Override
    public Operation getCurrentOperation() {
        return new Operation() {
            @Override
            public Object getId() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public <T> T run(String displayName, Transformer<T, ? super BuildOperationContext> factory) {
        return factory.transform(new TestBuildOperationContext());
    }

    @Override
    public <T> T run(BuildOperationDetails operationDetails, Transformer<T, ? super BuildOperationContext> factory) {
        return factory.transform(new TestBuildOperationContext());
    }

    @Override
    public void run(String displayName, Action<? super BuildOperationContext> action) {
        action.execute(new TestBuildOperationContext());
    }

    @Override
    public void run(BuildOperationDetails operationDetails, Action<? super BuildOperationContext> action) {
        action.execute(new TestBuildOperationContext());
    }

    private static class TestBuildOperationContext implements BuildOperationContext {
        @Override
        public void failed(@Nullable Throwable failure) {
        }
    }
}
