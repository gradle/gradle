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

package org.gradle.configuration.project;

import org.gradle.internal.operations.BuildOperationType;

/**
 * Execution of a project's afterEvaluate hooks
 *
 * @since 4.9
 */
public final class NotifyProjectAfterEvaluatedBuildOperationType implements BuildOperationType<NotifyProjectAfterEvaluatedBuildOperationType.Details, NotifyProjectAfterEvaluatedBuildOperationType.Result> {

    public interface Details {

        String getProjectPath();

        String getBuildPath();

    }

    public interface Result {

    }

    final static Result RESULT = new Result() {
    };

    private NotifyProjectAfterEvaluatedBuildOperationType() {
    }

}
