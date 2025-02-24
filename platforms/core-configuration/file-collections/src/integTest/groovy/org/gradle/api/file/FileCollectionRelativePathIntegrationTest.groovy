/*
 * Copyright 2025 the original author or authors.
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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

class FileCollectionRelativePathIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/30052")
    def "provider-backed relative files are resolved relative to their owner"() {
        given:
        settingsFile """
            include("sub")
            include("other")
        """
        createDirs("other")

        buildFile "sub/build.gradle", """
            abstract class CustomTask extends DefaultTask {
                @InputFiles abstract ConfigurableFileCollection getIncoming()
                @TaskAction void run() { println("Effective files: \${incoming.files.toSorted()}") }
            }

            tasks.register("foo", CustomTask) {
                incoming.from(project.files(provider { "subFile.txt" }))
                incoming.from(project(":other").isolated.projectDirectory.files(provider { "otherFile.txt" }))
                incoming.from(layout.settingsDirectory.files(provider { "settingsFile.txt" }))
            }
        """

        when:
        run ":sub:foo"

        then:
        def files = ["sub/subFile.txt", "other/otherFile.txt", "settingsFile.txt"]
        outputContains("Effective files: ${files.collect { testDirectory.file(it) }.toSorted()}")
    }

    // Tests a case forbidden by IP
    @Requires(IntegTestPreconditions.NotIsolatedProjects)
    @Issue("https://github.com/gradle/gradle/issues/30052")
    def "ConfigurableFileCollection supports adding relative files at execution time"() {
        given:
        settingsFile """
            include("abc") // a sibling non-root project that is configured earlier
            include("sub")
        """

        buildFile "abc/build.gradle", """
            def fileCollection = project.objects.fileCollection()
            fileCollection.from("file1.txt")
            project.ext.myFiles = fileCollection
        """

        buildFile "sub/build.gradle", """
            def otherProjectFiles = project(":abc").ext.myFiles
            tasks.register("foo") {
                doLast {
                    otherProjectFiles.from("file2.txt")
                    println("files: \${otherProjectFiles.files.toSorted()}")
                }
            }
        """

        when:
        run ":sub:foo"

        then:
        def files = ["abc/file1.txt", "abc/file2.txt"]
        outputContains("files: ${files.collect { testDirectory.file(it) }.toSorted()}")
    }

}
