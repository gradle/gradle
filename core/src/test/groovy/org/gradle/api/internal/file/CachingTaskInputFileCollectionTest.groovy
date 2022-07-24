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

package org.gradle.api.internal.file

import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class CachingTaskInputFileCollectionTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def collection = new CachingTaskInputFileCollection(TestFiles.resolver(), TestFiles.patternSetFactory, DefaultTaskDependencyFactory.withNoAssociatedProject(), Stub(PropertyHost))

    def "results are live prior to task execution"() {
        def files = []
        def f1 = tmpDir.file("f1")
        def f2 = tmpDir.file("f2")
        collection.from(files)

        expect:
        collection.files.empty

        files << f1
        collection.files as List == [f1]

        files << f2
        collection.files as List == [f1, f2]
    }

    def "results are live after task execution"() {
        def files = []
        def f1 = tmpDir.file("f1")
        def f2 = tmpDir.file("f2")
        collection.from(files)

        given:
        collection.prepareValue()
        collection.files
        collection.cleanupValue()

        expect:
        collection.files.empty

        files << f1
        collection.files as List == [f1]

        files << f2
        collection.files as List == [f1, f2]
    }

    def "results are cached during task execution"() {
        def files = []
        def f1 = tmpDir.file("f1")
        def f2 = tmpDir.file("f2")
        collection.from(files)
        files << f1

        given:
        collection.prepareValue()

        expect:
        collection.files as List == [f1]

        files << f2
        collection.files as List == [f1]

        files.clear()
        collection.files as List == [f1]
    }
}
