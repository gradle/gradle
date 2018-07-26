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

import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Details about the execution of a registered listener.
 *
 * @since 4.10
 */
public final class ExecuteListenerBuildOperationType implements BuildOperationType<ExecuteListenerBuildOperationType.Details, ExecuteListenerBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {

        /**
         * Returns the application id of the registering script or plugin.
         *
         * @see org.gradle.api.internal.plugins.ApplyPluginBuildOperationType.Details#getApplicationId()}
         * @see org.gradle.configuration.ApplyScriptPluginBuildOperationType.Details#getApplicationId
         */
        Long getApplicationId();
    }

    public interface Result {
    }

    static class DetailsImpl implements Details {
        final Long applicationId;

        DetailsImpl(Long applicationId) {
            this.applicationId = applicationId;
        }

        @Override
        public Long getApplicationId() {
            return applicationId;
        }

    }

    static final Result RESULT = new Result() {};

    private ExecuteListenerBuildOperationType() {
    }
}
