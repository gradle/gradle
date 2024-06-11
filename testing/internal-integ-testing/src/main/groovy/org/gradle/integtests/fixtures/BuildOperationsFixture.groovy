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
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.test.fixtures.file.TestDirectoryProvider

import java.util.regex.Pattern

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
        }
    }

    @Override
    List<BuildOperationRecord> getRoots() {
        getTree().roots
    }

    List<BuildOperationRecord> getDanglingChildren() {
        new BuildOperationTreeFixture(BuildOperationTrace.readPartialTree(path)).roots.findAll { it.parentId != null }
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord root(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        return getTree().root(type, predicate)
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> BuildOperationRecord first(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        return getTree().first(type, predicate)
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> boolean isType(BuildOperationRecord record, Class<T> type) {
        return getTree().isType(record, type)
    }

    @Override
    @SuppressWarnings("GrUnnecessaryPublicModifier")
    public <T extends BuildOperationType<?, ?>> List<BuildOperationRecord> all(Class<T> type, Spec<? super BuildOperationRecord> predicate = Specs.satisfyAll()) {
        return getTree().all(type, predicate)
    }

    @Override
    BuildOperationRecord first(Pattern displayName) {
        return getTree().first(displayName)
    }

    @Override
    List<BuildOperationRecord> all() {
        return getTree().all()
    }

    @Override
    List<BuildOperationRecord> all(Pattern displayName) {
        return getTree().all(displayName)
    }

    @Override
    BuildOperationRecord only(Pattern displayName) {
        return getTree().only(displayName)
    }

    @Override
    List<BuildOperationRecord> parentsOf(BuildOperationRecord child) {
        return getTree().parentsOf(child)
    }

    @Override
    void none(Pattern displayName) {
        getTree().none(displayName)
    }

    @Override
    void debugTree(
        Spec<? super BuildOperationRecord> predicate = Specs.SATISFIES_ALL,
        Spec<? super BuildOperationRecord> progressPredicate = Specs.SATISFIES_ALL
    ) {
        getTree().debugTree(predicate, progressPredicate)
    }

    private BuildOperationTreeFixture getTree() {
        if (tree == null) {
            tree = new BuildOperationTreeFixture(BuildOperationTrace.read(path))
        }
        return tree
    }
}
