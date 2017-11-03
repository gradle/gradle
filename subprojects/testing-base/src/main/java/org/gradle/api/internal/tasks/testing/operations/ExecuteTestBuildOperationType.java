/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.operations;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * Build Operations for Test Results
 *
 * @since 4.4
 * */
@UsedByScanPlugin
public final class ExecuteTestBuildOperationType implements BuildOperationType<ExecuteTestBuildOperationType.Details, ExecuteTestBuildOperationType.Result> {

    /**
     * Details of a running Test
     *
     * @since 4.4
     * */
    @UsedByScanPlugin
    public interface Details {
        /**
         * the descriptor of the executed test.
         * */
        TestDescriptor getTestDescriptor();
    }

    /**
     * Result of a running Test
     *
     * @since 4.4
     * */
    @UsedByScanPlugin
    public interface Result {
        /**
         * The result of the executed test.
         *
         * */
        TestResult getResult();
    }

    private ExecuteTestBuildOperationType() {
    }
}
