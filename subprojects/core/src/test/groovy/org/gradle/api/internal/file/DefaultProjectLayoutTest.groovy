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
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.provider.ProviderTestUtil.withNoValue
import static org.gradle.api.internal.provider.ProviderTestUtil.withValues
import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectLayoutTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    TestFile projectDir
    DefaultProjectLayout layout

    def setup() {
        projectDir = tmpDir.createDir("project")
        layout = new DefaultProjectLayout(projectDir, TestFiles.resolver(projectDir), Stub(TaskDependencyFactory), Stub(Factory), Stub(PropertyHost), TestFiles.fileCollectionFactory(projectDir), TestFiles.filePropertyFactory(projectDir), TestFiles.fileFactory())
    }

    def "can query the project directory"() {
        expect:
        layout.projectDirectory.getAsFile() == projectDir
    }

    def "can resolve directory relative to project directory"() {
        def pathProvider = withValues("a", "b")

        expect:
        def dir = layout.projectDirectory.dir("sub-dir")
        dir.getAsFile() == projectDir.file("sub-dir")

        def provider = layout.projectDirectory.dir(pathProvider)
        provider.present

        def dir1 = provider.get()
        dir1.getAsFile() == projectDir.file("a")

        def dir2 = provider.get()
        dir2.getAsFile() == projectDir.file("b")

        dir1.getAsFile() == projectDir.file("a")
        dir2.getAsFile() == projectDir.file("b")
    }

    def "can resolve regular file relative to project directory"() {
        def pathProvider = withValues("a", "b")

        expect:
        def file = layout.projectDirectory.file("child")
        file.getAsFile() == projectDir.file("child")

        def provider = layout.projectDirectory.file(pathProvider)
        provider.present

        def file1 = provider.get()
        file1.getAsFile() == projectDir.file("a")

        def file2 = provider.get()
        file2.getAsFile() == projectDir.file("b")

        file1.getAsFile() == projectDir.file("a")
        file2.getAsFile() == projectDir.file("b")
    }

    def "directory is not present when path provider is not present"() {
        def pathProvider = withNoValue()

        expect:
        def provider = layout.projectDirectory.dir(pathProvider)
        !provider.present
        provider.getOrNull() == null
    }

    def "regular file is not present when path provider is not present"() {
        def pathProvider = withNoValue()

        expect:
        def provider = layout.projectDirectory.file(pathProvider)
        !provider.present
        provider.getOrNull() == null
    }

    def "can view directory as a file tree"() {
        def dir1 = projectDir.createDir("dir1")
        def file1 = dir1.createFile("sub-dir/file1")
        def file2 = dir1.createFile("file2")
        def dir2 = projectDir.createDir("dir2")
        def file3 = dir2.createFile("other/file3")

        expect:
        def tree1 = layout.projectDirectory.dir("dir1").asFileTree
        tree1.files == [file1, file2] as Set

        def tree2 = layout.projectDirectory.dir("dir2").asFileTree
        tree2.files == [file3] as Set
    }

    def "can query and mutate the build directory using resolvable type"() {
        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.present
        fileProvider.present
        fileProvider.get() == projectDir.file("build")

        def dir1 = buildDirectory.get()
        dir1.getAsFile() == projectDir.file("build")

        layout.setBuildDirectory("other")
        buildDirectory.present
        fileProvider.present
        fileProvider.get() == projectDir.file("other")

        def dir2 = buildDirectory.get()
        dir2.getAsFile() == projectDir.file("other")

        layout.setBuildDirectory("../target")
        buildDirectory.present
        fileProvider.present
        fileProvider.get() == tmpDir.file("target")

        def dir3 = buildDirectory.get()
        dir3.getAsFile() == tmpDir.file("target")

        dir1.getAsFile() == projectDir.file("build")
        dir2.getAsFile() == projectDir.file("other")
        dir3.getAsFile() == tmpDir.file("target")
    }

    def "can set the build directory location using an absolute File"() {
        def dir = tmpDir.createDir("dir")

        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.set(dir)
        buildDirectory.present
        fileProvider.present
        buildDirectory.get().getAsFile() == dir
        fileProvider.get() == dir
    }

    def "can set the build directory location using a relative File"() {
        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.set(new File("other"))
        buildDirectory.present
        fileProvider.present
        buildDirectory.get().getAsFile() == projectDir.file("other")
        fileProvider.get() == projectDir.file("other")
    }

    def "can resolve directory relative to build directory"() {
        def pathProvider = withValues("a", "b", "c")

        expect:
        def dir = layout.buildDirectory.dir("sub-dir")
        dir.present
        dir.get().getAsFile() == projectDir.file("build/sub-dir")

        def provider = layout.buildDirectory.dir(pathProvider)
        provider.present

        def dir1 = provider.get()
        dir1.getAsFile() == projectDir.file("build/b")

        def dir2 = provider.get()
        dir2.getAsFile() == projectDir.file("build/c")

        dir1.getAsFile() == projectDir.file("build/b")
        dir2.getAsFile() == projectDir.file("build/c")
    }

    def "can resolve regular file relative to build directory"() {
        def pathProvider = withValues("a", "b", "c")

        expect:
        def file = layout.buildDirectory.file("child")
        file.present
        file.get().getAsFile() == projectDir.file("build/child")

        def provider = layout.buildDirectory.file(pathProvider)
        provider.present

        def file1 = provider.get()
        file1.getAsFile() == projectDir.file("build/b")

        def file2 = provider.get()
        file2.getAsFile() == projectDir.file("build/c")

        file1.getAsFile() == projectDir.file("build/b")
        file2.getAsFile() == projectDir.file("build/c")
    }

    def "directories are equal when their paths are equal"() {
        expect:
        def dir = layout.projectDirectory.dir("child")
        strictlyEquals(dir, layout.projectDirectory.dir("child"))

        dir != layout.projectDirectory.dir("other")
        dir != layout.projectDirectory.dir("child/child2")
        dir != layout.projectDirectory.file("child")
    }

    def "regular files are equal when their paths are equal"() {
        expect:
        def file = layout.projectDirectory.file("child")
        strictlyEquals(file, layout.projectDirectory.file("child"))

        file != layout.projectDirectory.file("other")
        file != layout.projectDirectory.file("child/child2")
        file != layout.projectDirectory.dir("child")
    }

    def "can map File provider to regular file provider"() {
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")
        def fileProvider = withValues(file1, file2)

        expect:
        def provider = layout.file(fileProvider)
        provider.present

        provider.get().getAsFile() == file1
        provider.get().getAsFile() == file2
    }

    def "can map File provider to directory provider"() {
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")
        def fileProvider = withValues(file1, file2)

        expect:
        def provider = layout.dir(fileProvider)
        provider.present

        provider.get().getAsFile() == file1
        provider.get().getAsFile() == file2
    }

    def "resolves relative file given by File provider"() {
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")

        def fileProvider = withValues(new File("file1"), new File("file2"))

        expect:
        def provider = layout.file(fileProvider)
        provider.present

        provider.get().getAsFile() == file1
        provider.get().getAsFile() == file2
    }

    def "resolves relative dir given by File provider"() {
        def dir1 = projectDir.file("dir1")
        def dir2 = projectDir.file("dir2")

        def dirProvider = withValues(new File("dir1"), new File("dir2"))

        expect:
        def provider = layout.dir(dirProvider)
        provider.present

        provider.get().getAsFile() == dir1
        provider.get().getAsFile() == dir2
    }
}
