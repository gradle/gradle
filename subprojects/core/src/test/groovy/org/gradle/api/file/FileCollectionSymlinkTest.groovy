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
package org.gradle.api.file

import org.gradle.api.Project
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.UsesNativeServices
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

@Requires(UnitTestPreconditions.Symlinks)
@UsesNativeServices
class FileCollectionSymlinkTest extends Specification {
    @Shared Project project = ProjectBuilder.builder().build()
    @Shared @ClassRule TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())
    @Shared TestFile baseDir = temporaryFolder.createDir('baseDir')
    @Shared TestFile file = baseDir.file("file")
    @Shared TestFile symlink = baseDir.file("symlink")
    @Shared TestFile symlinked = baseDir.file("symlinked")

    def setupSpec() {
        file.text = 'some contents'
        symlinked.text = 'target of symlink'
        symlink.createLink(symlinked)
    }

    def "#desc can handle symlinks"() {
        expect:
        fileCollection.contains(file)
        fileCollection.contains(symlink)
        fileCollection.contains(symlinked)
        fileCollection.files == [file, symlink, symlinked] as Set

        (fileCollection - project.getLayout().files(symlink)).files == [file, symlinked] as Set

        where:
        desc                                    | fileCollection
        "project.files()"                       | project.files(file, symlink, symlinked)
        "project.layout.files()"                | project.getLayout().files(file, symlink, symlinked)
        "project.fileTree()"                    | project.fileTree(baseDir)
    }
}
