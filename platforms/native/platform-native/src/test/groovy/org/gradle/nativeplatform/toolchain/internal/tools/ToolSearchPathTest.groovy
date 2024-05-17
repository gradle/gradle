/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.tools

import org.gradle.api.GradleException
import org.gradle.internal.logging.text.DiagnosticsVisitor
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.toolchain.internal.ToolType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ToolSearchPathTest extends Specification {
    @Rule def TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def os = Stub(OperatingSystem)
    def registry = new ToolSearchPath(os)

    def "finds executable in system path"() {
        def file = tmpDir.createFile("cc.bin")

        given:
        os.getExecutableName("cc") >> "cc.bin"
        os.path >> [file.parentFile]

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        result.available
        result.tool == file
    }

    def "finds executable in provided path"() {
        def file = tmpDir.createFile("cc.bin")

        given:
        os.getExecutableName("cc") >> "cc.bin"
        registry.setPath([file.parentFile])

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        result.available
        result.tool == file
    }

    def "does not use path when executable name contains a file separator"() {
        def file = tmpDir.createFile("cc.bin")
        def base = tmpDir.createFile("cc")

        given:
        os.getExecutableName(base.absolutePath) >> file.absolutePath

        when:
        def result = registry.locate(ToolType.C_COMPILER, base.absolutePath)

        then:
        result.available
        result.tool == file
    }

    def "resolves cygwin symlinks on Windows"() {
        def file = tmpDir.createFile("cc.bin")
        def symlink = tmpDir.file("cc")

        given:
        symlink.setText("!<symlink>cc.bin\u0000", "utf-8")
        os.path >> [symlink.parentFile]
        os.windows >> true

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        result.available
        result.tool == file
    }

    def "ignores files that do not look like cygwin symlinks"() {
        def candidate1 = tmpDir.createFile("dir1/cc")
        def candidate2 = tmpDir.createFile("dir2/cc")
        def candidate3 = tmpDir.createFile("dir3/cc")
        def file = tmpDir.createFile("dir4/cc.bin")

        given:
        candidate1.setText("!<symlink>", "utf-8")
        candidate2.setText("!<symlink:abcd.bin", "utf-8")
        candidate3.setText("")
        os.getExecutableName("cc") >> "cc.bin"
        os.path >> [candidate1.parentFile, candidate2.parentFile, candidate3.parentFile, file.parentFile]
        os.windows >> true

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        result.available
        result.tool == file
    }

    def "returns first match found in path"() {
        def symlink = tmpDir.createFile("dir1/cc")
        def file = tmpDir.createFile("dir2/cc.bin")
        def ignored = tmpDir.createFile("dir3/cc.bin")

        given:
        symlink.setText("!<symlink>../dir2/cc.bin\u0000", "utf-8")
        os.getExecutableName("cc") >> "cc.bin"
        os.path >> [symlink.parentFile, ignored.parentFile]
        os.windows >> true

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        result.available
        result.tool == file
    }

    def "executable is unavailable when not found in path"() {
        def visitor = Mock(DiagnosticsVisitor)
        def dir1 = tmpDir.createDir("some-dir")
        def dir2 = tmpDir.createDir("some-dir-2")

        given:
        os.getExecutableName("cc") >> "cc.bin"
        registry.setPath([dir1, dir2])

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not find C compiler 'cc'. Searched in")
        1 * visitor.startChildren()
        1 * visitor.node(dir1.toString())
        1 * visitor.node(dir2.toString())
        1 * visitor.endChildren()
        0 * visitor._
    }

    def "executable is unavailable when not found in system path"() {
        def visitor = Mock(DiagnosticsVisitor)

        given:
        os.getExecutableName("cc") >> "cc.bin"
        os.path >> []

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        !result.available

        when:
        result.explain(visitor)

        then:
        1 * visitor.node("Could not find C compiler 'cc' in system path.")
        0 * visitor._
    }

    def "cannot use an unavailable tool"() {
        given:
        os.findInPath("cc") >> null

        when:
        def result = registry.locate(ToolType.C_COMPILER, "cc")

        then:
        !result.available

        when:
        result.getTool()

        then:
        GradleException e = thrown()
        e.message == "Could not find C compiler 'cc' in system path."
    }
}
