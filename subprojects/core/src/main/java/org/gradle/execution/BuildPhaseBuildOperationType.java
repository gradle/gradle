/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution;

import org.gradle.internal.operations.BuildOperationType;

/**
 * A marker interface the identifies a build operation type that is used to represent a build phase.
 */
public interface BuildPhaseBuildOperationType extends BuildOperationType<BuildPhaseBuildOperationType.Details, Void> {

    /**
     * Used just as a marker interface since there is no need to add any additional details since they are part of BuildOperationDescriptor.
     */
    interface Details {
    }
}
