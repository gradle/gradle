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

package org.gradle.internal.operations;

import javax.annotation.Nullable;

public class RootBuildOperationRef {

    private BuildOperationRef rootBuildOperationRef;
    private final CurrentBuildOperationRef currentBuildOperationRef;

    public RootBuildOperationRef(CurrentBuildOperationRef currentBuildOperationRef) {
        this.currentBuildOperationRef = currentBuildOperationRef;
    }

    @Nullable
    public OperationIdentifier maybeGetId() {
        return rootBuildOperationRef == null ? null : rootBuildOperationRef.getId();
    }

    public BuildOperationRef get() {
        if (rootBuildOperationRef == null) {
            throw new IllegalStateException("root build operation ref is null");
        }
        return rootBuildOperationRef;
    }

    public void setFromCurrent() {
        this.rootBuildOperationRef = currentBuildOperationRef.get();
    }

    public void clear() {
        this.rootBuildOperationRef = null;
    }

}
