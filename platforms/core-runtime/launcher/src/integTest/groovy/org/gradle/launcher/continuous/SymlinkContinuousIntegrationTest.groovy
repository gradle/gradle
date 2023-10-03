/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files
import java.nio.file.Paths

@Requires(UnitTestPreconditions.NotWindows)
class SymlinkContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def "can use symlink for input"() {
        given:
        def baseDir = file("src").createDir()
        def sourceFile = baseDir.file("A")
        sourceFile.text = "original"

        def linkdir = baseDir.createDir("linkdir")
        def symlink = linkdir.file("link")
        // Since we remove symlinks at the end of the build from the VFS, we
        // need an existing sibling of the symlink to ensure the parent directory of
        // the symlink is watched between builds.
        linkdir.file("existing").createFile()
        buildFile << """
    task echo {
        def symlink = file("${symlink.toURI()}")
        inputs.files symlink
        inputs.files "src/linkdir/existing"
        outputs.file "build/outputs"
        doLast {
            println "text: " + (symlink.exists() ? symlink.text:"missing")
        }
    }
"""
        when: "symlink is used as input and exists"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(sourceFile.toURI()))
        then:
        succeeds("echo")
        executedAndNotSkipped(":echo")
        output.contains("text: original")
        when: "symlink is deleted"
        symlink.delete()
        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":echo")
        output.contains("text: missing")
        when: "symlink is created"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(sourceFile.toURI()))
        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":echo")
        output.contains("text: original")
        when: "changes made to target of symlink"
        sourceFile.text = "changed"
        then:
        noBuildTriggered()

        cleanup:
        assert symlink.delete()
    }

    def "can use symlinked directory for input"() {
        given:
        def baseDir = file("src").createDir()
        def targetDir = baseDir.file("target").createDir()
        targetDir.files("A", "B")*.createFile()

        def linkdir = baseDir.createDir("linkdir")
        def symlink = linkdir.file("link")
        // Since we remove symlinks at the end of the build from the VFS, we
        // need an existing non-symlink sibling of the symlink to ensure the parent directory of
        // the symlink is watched between builds.
        linkdir.file("existing").createFile()

        buildFile << """
    task echo {
        def symlink = files("${symlink.toURI()}")
        inputs.files symlink
        inputs.files "src/linkdir/existing"
        outputs.files "build/output"
        doLast {
            println "isEmpty: " + symlink.isEmpty()
        }
    }
"""
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(targetDir.toURI()))
        expect:
        succeeds("echo")
        executedAndNotSkipped(":echo")
        output.contains("isEmpty: false")
        when: "symlink is deleted"
        symlink.delete()
        then:
        buildTriggeredAndSucceeded()
        when: "changes made to target of symlink"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(targetDir.toURI()))
        targetDir.file("C").createFile()
        then:
        succeeds("echo")
        executedAndNotSkipped(":echo")
        output.contains("isEmpty: false")
    }
}
