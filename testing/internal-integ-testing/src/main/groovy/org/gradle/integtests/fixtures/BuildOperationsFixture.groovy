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

import groovy.transform.CompileStatic
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.test.fixtures.file.TestDirectoryProvider

@CompileStatic
class BuildOperationsFixture extends BuildOperationTreeQueries {

    private String path
    private BuildOperationTreeFixture tree

    BuildOperationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir) {
        this(executer, projectDir, "operations")
    }

    BuildOperationsFixture(GradleExecuter executer, TestDirectoryProvider projectDir, String operationsTraceName) {
        this.path = projectDir.testDirectory.file(operationsTraceName).absolutePath
        executer.beforeExecute {
            this.tree = null
            executer.withArgument("-D$BuildOperationTrace.SYSPROP=$path")
            // disable memory hungry tree generation
            executer.withArgument("-D$BuildOperationTrace.TREE_SYSPROP=false")
        }
    }

    @Override
    List<BuildOperationRecord> getRoots() {
        getTree().roots
    }

    @Override
    List<BuildOperationRecord> getRecords() {
        getTree().records
    }

    @Override
    List<BuildOperationRecord> parentsOf(def child) {
        getTree().parentsOf(child)
    }

    @Override
    BuildOperationRecord withId(Long child) {
        getTree().withId(child)
    }

    List<BuildOperationRecord> getDanglingChildren() {
        new BuildOperationTreeFixture(BuildOperationTrace.readPartialTree(path)).roots.findAll { it.parentId != null }
    }

    private BuildOperationTreeFixture getTree() {
        if (tree == null) {
            tree = new BuildOperationTreeFixture(BuildOperationTrace.readTree(path))
        }
        return tree
    }
}
