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

package org.gradle.internal.featurelifecycle;

import org.gradle.internal.deprecation.DeprecatedFeatureUsage;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.time.Clock;

public class DeprecatedUsageBuildOperationProgressBroadcaster {

    private final Clock clock;
    private final BuildOperationListener listener;

    private final CurrentBuildOperationRef currentBuildOperationRef;

    public DeprecatedUsageBuildOperationProgressBroadcaster(Clock clock, BuildOperationListener listener, CurrentBuildOperationRef currentBuildOperationRef) {
        this.clock = clock;
        this.listener = listener;
        this.currentBuildOperationRef = currentBuildOperationRef;
    }

    void progress(DeprecatedFeatureUsage feature) {
        OperationIdentifier id = currentBuildOperationRef.getId();
        if (id != null) {
            listener.progress(id,
                new OperationProgressEvent(clock.getCurrentTime(),
                    new DefaultDeprecatedUsageProgressDetails(feature)));
        }

    }

}
