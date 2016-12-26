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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.SYMLINKS)
class IncrementalBuildSymlinkHandlingIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
task work {
    inputs.file('in.txt')
    inputs.dir('in-dir')
    outputs.file('out.txt')
    outputs.dir('out-dir')
    doLast {
        file('out.txt').text = 'content'
        def f2 = file('out-dir/file1.txt')
        f2.parentFile.mkdirs()
        f2 << 'content'
    }
}
"""
    }

    def "uses the target of symlink for input file content"() {
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

    def "symlink may reference missing input file"() {
        def inFile = file("other")
        def link = file("in.txt")
        link.createLink("other")
        assert !link.exists()

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

    def "can replace input file with symlink to file with same content"() {
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

        then:
        result.assertTasksSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")

        when:
        copy.text = "new content"
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }

    def "can replace input directory with symlink to directory with same content"() {
        def inDir = file("in-dir").createDir()
        inDir.file("file").createFile()
        def copy = file("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        inDir.renameTo(copy)
        inDir.deleteDir()
        inDir.createLink(copy)
        run("work")

        then:
        // TODO - should be skipped
        result.assertTasksNotSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")

        when:
        copy.file("file").text = "new content"
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }

    def "can replace output file with symlink to file with same content"() {
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

        then:
        result.assertTasksSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")

        when:
        copy.text = "new content"
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }

    def "can replace output directory with symlink to directory with same content"() {
        def outDir = file("out-dir")
        def copy = file("other")

        given:
        run("work")
        run("work")
        result.assertTasksSkipped(":work")

        when:
        outDir.renameTo(copy)
        outDir.deleteDir()
        outDir.createLink(copy)
        run("work")

        then:
        // TODO - should be skipped
        result.assertTasksNotSkipped(":work")

        when:
        run("work")

        then:
        result.assertTasksSkipped(":work")

        when:
        copy.listFiles().each { it.text = 'new content' }
        run("work")

        then:
        result.assertTasksNotSkipped(":work")
    }
}
