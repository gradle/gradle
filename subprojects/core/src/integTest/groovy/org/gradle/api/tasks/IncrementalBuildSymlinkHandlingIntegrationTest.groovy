/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Requires(UnitTestPreconditions.Symlinks)
class IncrementalBuildSymlinkHandlingIntegrationTest extends AbstractIntegrationSpec implements ValidationMessageChecker {
    def setup() {
        expectReindentedValidationMessage()
        buildFile << """
// This is a workaround to bust the JVM's file canonicalization cache
def f = file("delete-me")
f.createNewFile()
f.delete() // invalidates cache

task work {
    inputs.file('in.txt')
    inputs.dir('in-dir')
    def outTxt = file('out.txt')
    def outDir = file('out-dir')
    outputs.file(outTxt)
    outputs.dir(outDir)
    doLast {
        outTxt.text = 'content'
        def f2 = new File(outDir, 'file1.txt')
        f2.parentFile.mkdirs()
        f2 << 'content'
    }
}
"""
    }

    def "uses the target of symlink for input file content"() {
        file("in-dir").createDir()
        def inFile = file("other").createFile()
        def link = file("in.txt")
        link.createLink("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        inFile.text = 'new content'
        run("work")

        then:
        result.assertTasksNotSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")
    }

    def "uses the target of symlink for input directory content"() {
        file('in.txt').touch()
        def inDir = file("other").createDir()
        def inFile = inDir.file("file").createFile()
        file("in-dir").createLink("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        inFile.text = 'new content'
        run("work")

        then:
        result.assertTasksNotSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")
    }

    def "follows symlinks in input directories"() {
        file('in.txt').touch()
        def inFile = file("other").createFile()
        def inDir = file("in-dir").createDir()
        inDir.file("file").createLink("../other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        inFile.text = 'new content'
        run("work")

        then:
        result.assertTasksNotSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")
    }

    def "symlink may not reference missing input file"() {
        file("in-dir").createDir()
        def link = file("in.txt")
        link.createLink("other")
        assert !link.exists()

        expect:
        fails("work")
        failure.assertHasDescription("A problem was found with the configuration of task ':work' (type 'DefaultTask').")
        failureDescriptionContains(inputDoesNotExist {
            property('$1')
                .file(link)
                .includeLink()
        })
    }

    def "can replace input file with symlink to file with same content"() {
        file("in-dir").createDir()
        def inFile = file("in.txt").createFile()
        def copy = file("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        inFile.copyTo(copy)
        inFile.delete()
        inFile.createLink(copy)
        run("work")

        /*
         * This documents the current behavior, which is optimizing
         * for performance at the expense of not detecting some corner
         * cases. If there actually is a task that needs to distinguish
         * between links and real files, we should probably provide an
         * opt-in to canonical snapshotting, as it's quite expensive.
         */
        then:
        result.assertTaskSkipped(":work")

        when:
        copy.text = "new content"
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }

    def "can replace input directory with symlink to directory with same content"() {
        file('in.txt').touch()
        def inDir = file("in-dir").createDir()
        inDir.file("file").createFile()
        def copy = file("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        Files.move(inDir.toPath(), copy.toPath(), StandardCopyOption.ATOMIC_MOVE)
        inDir.deleteDir()
        inDir.createLink(copy)

        run("work")

        /*
         * This documents the current behavior, which is optimizing
         * for performance at the expense of not detecting some corner
         * cases. If there actually is a task that needs to distinguish
         * between links and real files, we should probably provide an
         * opt-in to canonical snapshotting, as it's quite expensive.
         */
        then:
        result.assertTaskSkipped(":work")

        when:
        copy.file("file").text = "new content"
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }

    def "can replace output file with symlink to file with same content"() {
        file('in.txt').touch()
        file("in-dir").createDir()
        def outFile = file("out.txt")
        def copy = file("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        outFile.copyTo(copy)
        outFile.delete()
        outFile.createLink(copy)
        run("work")


        /*
         * This documents the current behavior, which is optimizing
         * for performance at the expense of not detecting some corner
         * cases. If there actually is a task that needs to distinguish
         * between links and real files, we should probably provide an
         * opt-in to canonical snapshotting, as it's quite expensive.
         */
        then:
        result.assertTaskSkipped(":work")

        when:
        copy.text = "new content"
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }

    def "can replace output directory with symlink to directory with same content"() {
        file('in.txt').touch()
        file("in-dir").createDir()
        def outDir = file("out-dir")
        def copy = file("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        Files.move(outDir.toPath(), copy.toPath(), StandardCopyOption.ATOMIC_MOVE)
        outDir.deleteDir()
        outDir.createLink(copy)

        run("work")

        /*
         * This documents the current behavior, which is optimizing
         * for performance at the expense of not detecting some corner
         * cases. If there actually is a task that needs to distinguish
         * between links and real files, we should probably provide an
         * opt-in to canonical snapshotting, as it's quite expensive.
         */
        then:
        result.assertTaskSkipped(":work")

        when:
        copy.listFiles().each { it.text = 'new content' }
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }
}
