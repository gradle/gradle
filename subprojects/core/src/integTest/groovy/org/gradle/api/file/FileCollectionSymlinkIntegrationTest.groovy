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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Issue
import spock.lang.Unroll

import static org.gradle.util.TextUtil.escapeString

@Unroll
@Requires(TestPrecondition.SYMLINKS)
class FileCollectionSymlinkIntegrationTest extends AbstractIntegrationSpec {

    def "#desc can handle symlinks"() {
        def buildScript = file("build.gradle")
        def baseDir = file('build')
        baseDir.file('file').text = 'some contents'
        def symlinked = baseDir.file('symlinked')
        symlinked.text = 'target of symlink'
        baseDir.file('symlink').createLink(symlinked)


        buildScript << """
            def baseDir = new File("${escapeString(baseDir)}")
            def file = new File(baseDir, "file")
            def symlink = new File(baseDir, "symlink")
            def symlinked = new File(baseDir, "symlinked")
            def fileCollection = $code

            assert fileCollection.contains(file)
            assert fileCollection.contains(symlink)
            assert fileCollection.contains(symlinked)
            assert fileCollection.files == [file, symlink, symlinked] as Set
            assert (fileCollection - project.layout.files(symlink)).files == [file, symlinked] as Set
        """

        when:
        maybeDeprecated(code)
        run()

        then:
        noExceptionThrown()

        where:
        desc                                 | code
        "project.files()"                    | "project.files(file, symlink, symlinked)"
        "project.fileTree()"                 | "project.fileTree(baseDir)"
        "project.layout.files()"             | "project.layout.files(file, symlink, symlinked)"
        "project.layout.configurableFiles()" | "project.layout.configurableFiles(file, symlink, symlinked)"
        "project.objects.fileCollection()"   | "project.objects.fileCollection().from(file, symlink, symlinked)"
    }

    @Issue('https://github.com/gradle/gradle/issues/1365')
    def "broken symlink not produced by task is ignored"() {
        given:
        def input = file("input.txt").createFile()
        def outputDirectory = file("output")

        def brokenLink = file('link').createLink("broken")
        assert !brokenLink.exists()

        buildFile << """
            task copy(type: Copy) {
                from '${input.name}'
                into '${outputDirectory.name}'
            }
        """

        when:
        succeeds 'copy'
        then:
        outputDirectory.list().contains input.name
    }

    void maybeDeprecated(String expression) {
        if (expression.contains("configurableFiles")) {
            executer.expectDeprecationWarning()
        }
    }
}
