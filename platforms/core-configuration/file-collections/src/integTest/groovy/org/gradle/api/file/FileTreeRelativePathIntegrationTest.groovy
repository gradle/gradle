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

class FileTreeRelativePathIntegrationTest extends AbstractIntegrationSpec {

    // Tests a use case that will be forbidden by IP
    @Requires(IntegTestPreconditions.NotIsolatedProjects)
    @Issue("https://github.com/gradle/gradle/issues/30052")
    def "ConfigurableFileTree supports setting directory to relative path at execution time"() {
        given:
        settingsFile """
            include("abc") // a sibling non-root project that is configured earlier
            include("sub")
        """

        createDir("abc/subDir")

        buildFile "abc/build.gradle", """
            def customFiles = project.objects.fileTree()
            customFiles.from("willBeChanged")
            project.ext.customFiles = customFiles
        """

        buildFile "sub/build.gradle", """
            def customFiles = project(":abc").ext.customFiles
            tasks.register("foo") {
                doLast {
                    customFiles.from("subDir")
                    println("dir: \${customFiles.dir}")
                }
            }
        """

        when:
        run ":sub:foo"

        then:
        outputContains("dir: ${testDirectory.file("abc/subDir")}")
    }
}
