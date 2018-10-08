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

import javax.annotation.Nullable;

public class CurrentBuildOperationRef {

    private static final CurrentBuildOperationRef INSTANCE = new CurrentBuildOperationRef();

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
            ref.remove();
        } else {
            ref.set(state);
        }
    }

    public void clear() {
        ref.remove();
    }

}
