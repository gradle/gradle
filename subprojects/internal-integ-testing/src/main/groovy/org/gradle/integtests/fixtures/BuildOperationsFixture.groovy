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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.BuildOperationTypes
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile

class BuildOperationsFixture {

    private final TestFile traceFile

    private Map<Object, BuildOperationRecord> operations

    BuildOperationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        this.traceFile = projectDir.testDirectory.file("operations.txt")
        executer.beforeExecute {
            executer.withArgument("-D$BuildOperationTrace.SYSPROP=$traceFile.absolutePath")
        }
        executer.afterExecute {
            traceFile.withInputStream {
                operations = BuildOperationTrace.read(it)
            }
        }
    }

    boolean hasOperation(String displayName) {
        operation(displayName) != null
    }

    BuildOperationRecord operation(Class<?> type) {
        def detailsType = BuildOperationTypes.detailsType(type)
        operations.values().find { it.detailsType && detailsType.isAssignableFrom(it.detailsType as Class<?>) }
    }

    BuildOperationRecord operation(String displayName) {
        operations.values().find { it.displayName == displayName }
    }

    Map<String, ?> result(String displayName) {
        operation(displayName).result
    }

    String failure(String displayName) {
        operation(displayName).failure
    }

}
