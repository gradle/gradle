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

package org.gradle.internal.build.event.types;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalBuildPhaseDescriptor;

public class DefaultBuildPhaseDescriptor extends DefaultOperationDescriptor implements InternalBuildPhaseDescriptor {
    private final String buildPhase;
    private final int workItemCount;

    public DefaultBuildPhaseDescriptor(BuildOperationDescriptor buildOperation, OperationIdentifier parentId, String buildPhase, int workItemCount) {
        super(
            buildOperation.getId(),
            buildOperation.getName(),
            buildOperation.getDisplayName(),
            parentId
        );
        this.buildPhase = buildPhase;
        this.workItemCount = workItemCount;
    }

    public DefaultBuildPhaseDescriptor(OperationIdentifier id, String name, String displayName, OperationIdentifier parentId, String buildPhase, int workItemCount) {
        super(id, name, displayName, parentId);
        this.buildPhase = buildPhase;
        this.workItemCount = workItemCount;
    }

    @Override
    public String getBuildPhase() {
        return buildPhase;
    }

    @Override
    public int getBuildItemsCount() {
        return workItemCount;
    }
}
