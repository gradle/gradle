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

package org.gradle.nativebinaries.toolchain.internal.tools

import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativebinaries.toolchain.internal.ToolType
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TreeVisitor
import org.junit.Rule
import spock.lang.Specification

class ToolSearchPathTest extends Specification {
    @Rule def TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def os = Stub(OperatingSystem)
    def registry = new ToolSearchPath(os)

    def "finds executable in system path"() {
        def file = tmpDir.createFile("cc.bin")

        given:
        os.findInPath("cc") >> file

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

    def "executable is unavailable when not found in path"() {
        def visitor = Mock(TreeVisitor)
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
        def visitor = Mock(TreeVisitor)

        given:
        os.findInPath("cc") >> null

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
