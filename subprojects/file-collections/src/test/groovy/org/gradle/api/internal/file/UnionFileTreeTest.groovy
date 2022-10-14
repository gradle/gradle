/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file

import org.gradle.api.file.FileCollection
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import spock.lang.Specification

class UnionFileTreeTest extends Specification {
    private final UnionFileTree set = new UnionFileTree(TestFiles.taskDependencyFactory(), "<display name>")

    def setup() {
        NativeServicesTestFixture.initialize()
    }

    def canAddFileTree() {
        FileTreeInternal set1 = Mock(FileTreeInternal)
        when:
        set.addToUnion(set1)

        then:
        set.sourceCollections == [set1]
    }

    def cannotAddFileCollection() {
        when:
        set.addToUnion(Mock(FileCollection))

        then:
        def e = thrown(UnsupportedOperationException)
        e.message == "Can only add FileTree instances to <display name>."
    }
}
