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


import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * TODO
 *
 * @since 4.10
 */
public final class ExecuteListenerBuildOperationType implements BuildOperationType<ExecuteListenerBuildOperationType.Details, ExecuteListenerBuildOperationType.Result> {

    @UsedByScanPlugin
    public interface Details {
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

        BuildOperationDescriptor.Builder desc() {
            return BuildOperationDescriptor
                .displayName("Execute listener")
                .details(this);
        }
    }

    static final Result RESULT = new Result() {};

    private ExecuteListenerBuildOperationType() {
    }
}
