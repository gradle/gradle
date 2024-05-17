/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections

import org.gradle.api.Buildable
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import spock.lang.Specification

class FileCollectionAdapterTest extends Specification {

    def delegatesToTargetCollectionToBuildSetOfFiles() {
        MinimalFileSet fileSet = Mock()
        FileCollectionAdapter adapter = new FileCollectionAdapter(fileSet)
        def expectedFiles = [new File('a'), new File('b')] as Set

        when:
        def files = adapter.files

        then:
        files == expectedFiles
        1 * fileSet.getFiles() >> expectedFiles
        0 * _._
    }

    def visitDependenciesDelegatesToTargetCollectionWhenItImplementsBuildable() {
        TestFileSet fileSet = Mock()
        FileCollectionAdapter adapter = new FileCollectionAdapter(fileSet)
        TaskDependencyResolveContext context = Mock()

        when:
        adapter.visitDependencies(context)

        then:
        1 * context.add(fileSet)
    }

    def visitDependenciesDoesNotDelegateToTargetCollectionWhenItDoesNotImplementBuildable() {
        MinimalFileSet fileSet = Mock()
        FileCollectionAdapter adapter = new FileCollectionAdapter(fileSet)
        TaskDependencyResolveContext context = Mock()

        when:
        adapter.visitDependencies(context)

        then:
        0 * context._
    }

    interface TestFileSet extends MinimalFileSet, Buildable {
    }
}
