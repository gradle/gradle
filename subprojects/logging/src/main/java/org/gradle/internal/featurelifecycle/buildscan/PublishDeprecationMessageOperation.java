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

package org.gradle.internal.featurelifecycle.buildscan;

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.RunnableBuildOperation;

import static org.gradle.internal.featurelifecycle.buildscan.DeprecationMessagesBuildOperationType.*;

public class PublishDeprecationMessageOperation implements RunnableBuildOperation {
    private final String message;
    private final String location;

    public PublishDeprecationMessageOperation(String message, String location) {
        this.message = message;
        this.location = location;
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        return BuildOperationDescriptor.displayName("Woo!")
            .details(new Details() {
            });
    }

    @Override
    public void run(BuildOperationContext context) {
        context.setResult(new Result() {
            @Override
            public String getLocation() {
                return location;
            }

            @Override
            public String getMessage() {
                return message;
            }
        });
    }
}
