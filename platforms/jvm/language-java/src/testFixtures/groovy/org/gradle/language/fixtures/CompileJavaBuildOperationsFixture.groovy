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

package org.gradle.language.fixtures

import org.gradle.api.internal.tasks.compile.CompileJavaBuildOperationType
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestDirectoryProvider

class CompileJavaBuildOperationsFixture {
    private BuildOperationsFixture operations

    CompileJavaBuildOperationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        operations = new BuildOperationsFixture(executer, projectDir)
    }

    BuildOperationRecord getAt(String taskPath) {
        operations.only(CompileJavaBuildOperationType) {
            operations.parentsOf(it).contains(operations.only(ExecuteTaskBuildOperationType) { it.details.taskPath == taskPath })
        }
    }
}
