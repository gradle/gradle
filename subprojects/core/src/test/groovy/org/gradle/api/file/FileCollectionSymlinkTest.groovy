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

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.Project

import spock.lang.Specification
import spock.lang.Shared
import spock.lang.Unroll

// cannot use org.gradle.util.Resources here because the files it returns have already been canonicalized
class FileCollectionSymlinkTest extends Specification {
    @Shared Project project = new ProjectBuilder().build()

    @Shared File baseDir = getTestFile("symlinks")
    @Shared File file = new File(baseDir, "file")
    @Shared File symlink = new File(baseDir, "symlink")
    @Shared File symlinked = new File(baseDir, "symlinked")

    @Unroll("#desc can handle symlinks")
    def "file collection can handle symlinks"() {
        expect:
        fileCollection.contains(file)
        fileCollection.contains(symlink)
        fileCollection.contains(symlinked)
        fileCollection.files == [file, symlink, symlinked] as Set

        (fileCollection - project.files(symlink)).files == [file, symlinked] as Set

        where:
        desc                 | fileCollection
        "project.files()"    | project.files(file, symlink, symlinked)
        "project.fileTree()" | project.fileTree(baseDir)
    }

    private static File getTestFile(String name) {
        new File(FileCollectionSymlinkTest.getResource(name).toURI().path)
    }
}
