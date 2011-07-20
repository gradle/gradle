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
package org.gradle.plugins.ide.eclipse.model.internal

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile

class FileReferenceFactoryTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    final TestFile rootDir = tmpDir.createDir("root")
    final FileReferenceFactory factory = new FileReferenceFactory()

    def setup() {
        factory.addPathVariable('ROOT_DIR', rootDir)
    }

    def "creates a reference to a file which is not under any root directory"() {
        TestFile file = tmpDir.file("file.txt")

        expect:
        def reference = factory.file(file)
        reference.file == file
        reference.path == relpath(file)
        !reference.relativeToPathVariable
    }

    def "creates a reference for a file under a root directory"() {
        TestFile file = rootDir.file("a/file.txt")

        expect:
        def reference = factory.file(file)
        reference.file == file
        reference.path == "ROOT_DIR/a/file.txt"
        reference.relativeToPathVariable
    }

    def "creates a reference for a root directory"() {
        expect:
        def reference = factory.file(rootDir)
        reference.file == rootDir
        reference.path == "ROOT_DIR"
        reference.relativeToPathVariable
    }

    def "creates a reference from a file path"() {
        TestFile file = tmpDir.file("file.txt")

        expect:
        def reference = factory.file(relpath(file))
        reference.file == file
        reference.path == relpath(file)
        !reference.relativeToPathVariable
    }

    def "creates a reference from a path starting with a variable"() {
        TestFile file = rootDir.file("a/file.txt")

        expect:
        def reference = factory.var("ROOT_DIR/a/file.txt")
        reference.file == file
        reference.path == "ROOT_DIR/a/file.txt"
        reference.relativeToPathVariable
    }

    private String relpath(File file) {
        return file.absolutePath.replace(File.separator, '/')
    }
}
