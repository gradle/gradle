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

package org.gradle.problems.internal.transformers;

import org.gradle.api.GradleException;
import org.gradle.api.internal.plugins.DefaultPluginManager.OperationDetails;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.locations.PluginIdLocation;
import org.gradle.internal.operations.BuildOperationAncestryTracker;
import org.gradle.problems.internal.OperationListener;

import java.util.Objects;

public class PluginIdLocationTransformer extends BaseLocationTransformer {

    public PluginIdLocationTransformer(
        BuildOperationAncestryTracker buildOperationAncestryTracker,
        OperationListener operationListener
    ) {
        super(buildOperationAncestryTracker, operationListener);
    }

    @Override
    public Problem transform(Problem problem) {
        getExecuteTask(OperationDetails.class)
            .ifPresent(id -> {
                try {
                    OperationDetails operationDetails = operationListener.getOp(id, OperationDetails.class);
                    Objects.requireNonNull(operationDetails, "operationDetails should not be null");
                    String pluginId = operationDetails.getPluginId();
                    if (pluginId != null) {
                        problem.getLocations().add(new PluginIdLocation(pluginId));
                    }
                } catch (Exception ex) {
                    throw new GradleException("Problem while reporting problem", ex);
                }
            });

        return problem;
    }
}
