/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous.jdk7

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import java.nio.file.Files
import java.nio.file.Paths

@Requires(TestPrecondition.NOT_WINDOWS)
class SymlinkContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def "can use symlink for input"() {
        given:
        def baseDir = file("src").createDir()
        def sourceFile = baseDir.file("A")
        sourceFile.text = "original"
        def symlink = baseDir.createDir("linkdir").file("link")
        buildFile << """
    task echo {
        def symlink = file("${symlink.toURI()}")
        inputs.files symlink
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
        succeeds()
        executedAndNotSkipped(":echo")
        output.contains("text: missing")
        when: "symlink is created"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(sourceFile.toURI()))
        then:
        succeeds()
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

        def symlink = baseDir.createDir("linkdir").file("link")
        buildFile << """
    task echo {
        def symlink = files("${symlink.toURI()}")
        inputs.files symlink
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
        // OSX uses a polling implementation, so changes to links are detectable
        if (OperatingSystem.current().isMacOsX()) {
            succeeds()
        } else {
            // Other OS's do not produce filesystem events for deleted symlinks
            noBuildTriggered()
        }
        when: "changes made to target of symlink"
        Files.createSymbolicLink(Paths.get(symlink.toURI()), Paths.get(targetDir.toURI()))
        targetDir.file("C").createFile()
        then:
        succeeds("echo")
        executedAndNotSkipped(":echo")
        output.contains("isEmpty: false")
    }
}
