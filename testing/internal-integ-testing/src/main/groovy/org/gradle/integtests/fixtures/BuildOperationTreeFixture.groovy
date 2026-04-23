/*
 * Copyright 2020 the original author or authors.
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


import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationTree

class BuildOperationTreeFixture extends BuildOperationTreeQueries {

    private final BuildOperationTree operations

    BuildOperationTreeFixture(BuildOperationTree operations) {
        this.operations = operations
    }

    @Override
    List<BuildOperationRecord> getRoots() {
        operations.roots
    }

    @Override
    List<BuildOperationRecord> getRecords() {
        operations.records.values().toList()
    }

    @Override
    List<BuildOperationRecord> parentsOf(def child) {
        def parents = []
        def parentId = child.parentId
        while (parentId != null) {
            def parent = operations.records.get(parentId)
            parents.add(0, parent)
            parentId = parent.parentId
        }
        return parents
    }

    @Override
    BuildOperationRecord withId(Long id) {
        operations.records.get(id)
    }
}
