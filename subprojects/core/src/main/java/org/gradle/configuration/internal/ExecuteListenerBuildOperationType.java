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

package org.gradle.configuration.internal;

import org.gradle.configuration.project.NotifyProjectBeforeEvaluatedBuildOperationType;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Execution of a lifecycle listener/callback.
 *
 * Expected to be the child operation of an operation indicating the lifecycle event (e.g. {@link NotifyProjectBeforeEvaluatedBuildOperationType}).
 *
 * @since 4.10
 */
@UsedByScanPlugin
public final class ExecuteListenerBuildOperationType implements BuildOperationType<ExecuteListenerBuildOperationType.Details, ExecuteListenerBuildOperationType.Result> {

    public interface Details {

        /**
         * The application ID of the script or plugin that registered the listener.
         *
         * @see org.gradle.api.internal.plugins.ApplyPluginBuildOperationType.Details#getApplicationId()
         * @see org.gradle.configuration.ApplyScriptPluginBuildOperationType.Details#getApplicationId()
         */
        long getApplicationId();

        /**
         * A human friendly description of where the listener was registered by the user.
         *
         * General contract is public-type-simplename.method-name.
         * e.g. Project.beforeEvaluate
         */
        String getRegistrationPoint();
    }

    public interface Result {
    }

    static class DetailsImpl implements Details {
        final UserCodeApplicationId applicationId;
        final String registrationPoint;

        DetailsImpl(UserCodeApplicationId applicationId, String registrationPoint) {
            this.applicationId = applicationId;
            this.registrationPoint = registrationPoint;
        }

        @Override
        public long getApplicationId() {
            return applicationId.longValue();
        }

        @Override
        public String getRegistrationPoint() {
            return registrationPoint;
        }
    }

    static final Result RESULT = new Result() {
    };

    private ExecuteListenerBuildOperationType() {
    }
}
