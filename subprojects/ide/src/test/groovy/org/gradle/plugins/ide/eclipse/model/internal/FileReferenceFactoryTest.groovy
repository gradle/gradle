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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Matchers
import org.junit.Rule
import spock.lang.Specification

class FileReferenceFactoryTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final TestFile rootDir = tmpDir.createDir("root")
    final FileReferenceFactory factory = new FileReferenceFactory()

    def setup() {
        factory.addPathVariable('ROOT_DIR', rootDir)
    }

    def "creates a reference to a file which is not under any root directory"() {
        TestFile file = tmpDir.file("file.txt")

        expect:
        def reference = factory.fromFile(file)
        reference.file == file
        reference.path == relpath(file)
        !reference.relativeToPathVariable
    }

    def "creates a reference for a file under a root directory"() {
        TestFile file = rootDir.file("a/file.txt")

        expect:
        def reference = factory.fromFile(file)
        reference.file == file
        reference.path == "ROOT_DIR/a/file.txt"
        reference.relativeToPathVariable
    }

    def "creates a reference for a root directory"() {
        expect:
        def reference = factory.fromFile(rootDir)
        reference.file == rootDir
        reference.path == "ROOT_DIR"
        reference.relativeToPathVariable
    }

    def "creates null reference for a null file"() {
        expect:
        factory.fromFile(null) == null
    }

    def "creates a reference from a file path"() {
        TestFile file = tmpDir.file("file.txt")

        expect:
        def reference = factory.fromPath(relpath(file))
        reference.file == file
        reference.path == relpath(file)
        !reference.relativeToPathVariable
    }

    def "creates a reference from a jar url"() {
        TestFile file = tmpDir.file("file.txt")

        expect:
        def reference = factory.fromJarURI(jarUrL(file))
        reference.file == file
        reference.path == relpath(file)
        reference.jarURL == jarUrL(file);
        !reference.relativeToPathVariable
    }

    def "creates null reference for a null jar url"() {
        expect:
        factory.fromJarURI(null) == null
    }

    def "creates null reference for a null file path"() {
        expect:
        factory.fromPath(null) == null
    }

    def "creates a reference from a variable path"() {
        TestFile file = rootDir.file("a/file.txt")

        expect:
        def reference = factory.fromVariablePath("ROOT_DIR/a/file.txt")
        reference.file == file
        reference.path == "ROOT_DIR/a/file.txt"
        reference.relativeToPathVariable
    }

    def "creates null reference for a null file variable path"() {
        expect:
        factory.fromVariablePath(null) == null
    }

    def "file references are equal when they point to the same file"() {
        TestFile file = rootDir.file("a/file.txt")
        def reference = factory.fromFile(file)
        def sameFile = factory.fromFile(file)
        def differentFile = factory.fromFile(rootDir)

        expect:
        reference Matchers.strictlyEqual(sameFile)
        reference != differentFile
    }

    private String relpath(File file) {
        return file.absolutePath.replace(File.separator, '/')
    }

    private String jarUrL(File file) {
        return "jar:${file.toURI()}!/"
    }
}
