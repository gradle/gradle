/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.bundling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue

class UndeletableFilesTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider)

    @Issue("https://github.com/gradle/gradle/issues/25752#issuecomment-1792821355")
    def "reproduce zip issue"() {
        buildFile << """
            plugins {
                id 'lifecycle-base'
            }

            System.out.println("Executing build.gradle")

            def helloArchive = zipTree(file("hello.zip"))
            tasks.create("makeArchive", Zip) {
                System.out.println("Files in the archive: " + helloArchive.files)

                archiveFileName = "archive.zip"
                destinationDirectory = layout.buildDirectory

                from helloArchive
            }
        """

        resources.maybeCopy('UndeletableTestFiles/zip')

        expect:
        succeeds "clean"
    }
}
