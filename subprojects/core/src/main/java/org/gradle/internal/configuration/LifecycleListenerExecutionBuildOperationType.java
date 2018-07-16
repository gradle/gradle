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

package org.gradle.internal.configuration;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationType;

import static org.gradle.internal.configuration.LifecycleListenerExecutionBuildOperationType.Details;
import static org.gradle.internal.configuration.LifecycleListenerExecutionBuildOperationType.Result;

public class LifecycleListenerExecutionBuildOperationType implements BuildOperationType<Details, Result> {

    interface Details {
        Long getApplicationId();
    }

    interface Result {
    }


    static class DetailsImpl implements Details {

        private final Long applicationId;

        DetailsImpl(Long applicationId) {
            this.applicationId = applicationId;
        }

        @Override
        public Long getApplicationId() {
            return applicationId;
        }

        BuildOperationDescriptor.Builder desc() {
            return BuildOperationDescriptor
                .displayName("Execute lifecycle listener")
                .details(this);
        }
    }

    static Result RESULT = new Result() {};

}
