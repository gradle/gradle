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
package org.gradle.internal.operations;

import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

@ServiceScope(Scope.Global.class)
public class CurrentBuildOperationRef {

    private static final CurrentBuildOperationRef INSTANCE = new CurrentBuildOperationRef();

    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<BuildOperationRef> ref = new ThreadLocal<BuildOperationRef>();

    public static CurrentBuildOperationRef instance() {
        return INSTANCE;
    }

    @Nullable
    public BuildOperationRef get() {
        return ref.get();
    }

    @Nullable
    public OperationIdentifier getId() {
        BuildOperationRef operationState = get();
        return operationState == null ? null : operationState.getId();
    }

    @Nullable
    public OperationIdentifier getParentId() {
        BuildOperationRef operationState = get();
        return operationState == null ? null : operationState.getParentId();
    }

    public void set(@Nullable BuildOperationRef state) {
        if (state == null) {
            clear();
        } else {
            ref.set(state);
        }
    }

    public void clear() {
        ref.remove();
    }

    /**
     * Callable with generic exception.
     */
    public interface Callable<T, E extends Throwable> {
        T call() throws E;
    }

    public <T, E extends Throwable> T with(@Nullable BuildOperationRef state, Callable<T, E> block) throws E {
        BuildOperationRef oldState = get();
        try {
            set(state);
            return block.call();
        } finally {
            set(oldState);
        }
    }

    public void with(@Nullable BuildOperationRef state, Runnable block) {
        BuildOperationRef oldState = get();
        try {
            set(state);
            block.run();
        } finally {
            set(oldState);
        }
    }

}
