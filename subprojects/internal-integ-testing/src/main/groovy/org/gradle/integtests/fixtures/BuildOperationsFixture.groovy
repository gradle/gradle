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

import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.BuildOperationType
import org.gradle.internal.operations.BuildOperationTypes
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.internal.operations.trace.BuildOperationTree
import org.gradle.test.fixtures.file.TestDirectoryProvider

import java.util.concurrent.ConcurrentLinkedQueue

class BuildOperationsFixture {

    private final String path

    private BuildOperationTree operations

    BuildOperationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        this.path = projectDir.testDirectory.file("operations").absolutePath

        executer.beforeExecute {
            executer.withArgument("-D$BuildOperationTrace.SYSPROP=$path")
        }
        executer.afterExecute {
            operations = BuildOperationTrace.read(path)
        }
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord operation(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        def detailsType = BuildOperationTypes.detailsType(type)
        operations.records.values().find {
            it.detailsType && detailsType.isAssignableFrom(it.detailsType) && predicate.isSatisfiedBy(it)
        }
    }

    BuildOperationRecord operation(String displayName) {
        operations.records.values().find { it.displayName == displayName }
    }

    Map<String, ?> result(String displayName) {
        operation(displayName).result
    }

    String failure(String displayName) {
        operation(displayName).failure
    }

    boolean hasOperation(String displayName) {
        operation(displayName) != null
    }

    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> boolean hasOperation(Class<T> type) {
        operation(type) != null
    }

    List<BuildOperationRecord> search(BuildOperationRecord parent, Spec<? super BuildOperationRecord> predicate) {
        def matches = []
        def search = new ConcurrentLinkedQueue<BuildOperationRecord>()

        def operation = parent
        while (operation != null) {
            if (predicate.isSatisfiedBy(operation)) {
                matches << operation
            }
            search.addAll(operation.children)
            operation = search.poll()
        }

        matches
    }
}
