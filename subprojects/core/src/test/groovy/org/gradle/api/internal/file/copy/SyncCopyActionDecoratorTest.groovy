/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file.copy

import org.gradle.api.Action
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.WorkspaceTest
import org.gradle.util.TestUtil

class SyncCopyActionDecoratorTest extends WorkspaceTest {

    FileCopier copier

    def setup() {
        copier = new FileCopier(
                TestFiles.deleter(),
                TestFiles.directoryFileTreeFactory(),
                TestFiles.fileCollectionFactory(testDirectory),
                TestFiles.resolver(testDirectory),
                TestFiles.patternSetFactory,
                TestUtil.propertyFactory(),
                TestFiles.fileSystem(),
                TestUtil.instantiatorFactory().decorateLenient(),
                TestFiles.documentationRegistry()
        )
    }

    void deletesExtraFilesFromDestinationDirectoryAtTheEndOfVisit() {
        given:
        file("src").with {
            createFile("subdir/included.txt")
            createFile("included.txt")
        }

        file("dest").with {
            createFile("subdir/included.txt")
            createFile("subdir/extra.txt")
            createFile("included.txt")
            createFile("extra.txt")
            createDir("extra")
        }

        when:
        def result = copier.sync({
            it.from "src"
            it.into "dest"
        } as Action)

        then:
        result.didWork
        file("dest").assertHasDescendants("subdir/included.txt", "included.txt")
    }

}
