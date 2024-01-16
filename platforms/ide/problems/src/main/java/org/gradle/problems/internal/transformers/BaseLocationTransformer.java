/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.problems.internal.transformers;

import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Optional;


public class BaseLocationTransformer<T> implements BuildOperationListener {

    private final Class<? extends T> detailsClass;
    private final HashMap<OperationIdentifier, T> detailsMap = new HashMap<>();
    private final BuildOperationAncestryTracker buildOperationAncestryTracker;

    @Inject
    public BaseLocationTransformer(
        Class<? extends T> detailsClass,
        BuildOperationAncestryTracker buildOperationAncestryTracker,
        BuildOperationListenerManager buildOperationListenerManager
    ) {
        this.detailsClass = detailsClass;
        this.buildOperationAncestryTracker = buildOperationAncestryTracker;
        buildOperationListenerManager.addListener(this);
    }

    Optional<T> getExecuteTask(OperationIdentifier id) {
        return buildOperationAncestryTracker
            .findClosestMatchingAncestor(id, detailsMap::containsKey)
            .map(detailsMap::get);
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        if (detailsClass.isInstance(buildOperation.getDetails())) {
            detailsMap.put(buildOperation.getId(), detailsClass.cast(buildOperation.getDetails()));
        }
    }

    @Override
    public void progress(OperationIdentifier operationIdentifier, OperationProgressEvent progressEvent) {
        // Pass
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {

    }
}
