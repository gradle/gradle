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

import spock.lang.Specification
import org.gradle.api.Buildable
import org.gradle.api.tasks.TaskDependency

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

    def resolveAddsTargetCollectionToContext() {
        MinimalFileSet fileSet = Mock()
        FileCollectionAdapter adapter = new FileCollectionAdapter(fileSet)
        FileCollectionResolveContext context = Mock()

        when:
        adapter.visitContents(context)

        then:
        1 * context.add(fileSet)
        0 * _._
    }

    def getBuildDependenciesDelegatesToTargetCollectionWhenItImplementsBuildable() {
        TestFileSet fileSet = Mock()
        TaskDependency expectedDependency = Mock()
        FileCollectionAdapter adapter = new FileCollectionAdapter(fileSet)

        when:
        def dependencies = adapter.buildDependencies

        then:
        dependencies == expectedDependency
        1 * fileSet.buildDependencies >> expectedDependency
    }
}

interface TestFileSet extends MinimalFileSet, Buildable {

}
