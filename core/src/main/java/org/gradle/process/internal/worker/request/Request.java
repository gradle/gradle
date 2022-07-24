/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process.internal.worker.request;

import com.google.common.base.Preconditions;
import org.gradle.internal.operations.BuildOperationRef;

public class Request {
    private final Object arg;
    private final BuildOperationRef buildOperation;

    public Request(Object arg, BuildOperationRef buildOperation) {
        Preconditions.checkNotNull(buildOperation);
        this.arg = arg;
        this.buildOperation = buildOperation;
    }

    public Object getArg() {
        return arg;
    }

    public BuildOperationRef getBuildOperation() {
        return buildOperation;
    }
}
