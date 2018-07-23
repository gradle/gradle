/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.Action;
import org.gradle.internal.exceptions.Contextual;

public class DefaultProtectApiService implements ProtectApiService {
    private int nestedActionExecutionCount = 0;

    @Override
    public <T> Action<T> wrap(final Action<? super T> delegate) {
        return new Action<T>() {
            @Override
            public void execute(T t) {
                nestedActionExecutionCount++;
                try {
                    delegate.execute(t);
                } finally {
                    nestedActionExecutionCount--;
                }
            }
        };
    }

    @Override
    public void assertMethodExecutionAllowed(String methodName, Object target) {
        if (isUnderProtection()) {
            throw createIllegalStateException(methodName, target);
        }
    }

    private IllegalStateException createIllegalStateException(String methodName, Object target) {
        return new IllegalConfigurationStateException(String.format("%s on %s is protected and cannot be executed under the current context", methodName, target));
    }

    private boolean isUnderProtection() {
        return nestedActionExecutionCount != 0;
    }

    @Contextual
    private static class IllegalConfigurationStateException extends IllegalStateException {
        public IllegalConfigurationStateException(String message) {
            super(message);
        }
    }
}
