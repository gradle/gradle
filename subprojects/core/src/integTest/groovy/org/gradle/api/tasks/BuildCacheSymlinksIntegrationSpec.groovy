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

package org.gradle.api.tasks

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files
import java.nio.file.Paths

@Requires(UnitTestPreconditions.Symlinks)
class BuildCacheSymlinksIntegrationSpec extends AbstractIntegrationSpec {

    TestFile inputDirectory
    TestFile outputDirectory
    TestFile originalDir
    TestFile originalFile
    TestFile fileLink
    TestFile dirLink

    def setup() {
        inputDirectory = createDir("input")
        outputDirectory = createDir("output")
        executer.withStacktraceEnabled()

        originalDir = inputDirectory.createDir("original")
        originalFile = originalDir.createFile("original.txt") << "some text"
        fileLink = originalDir.file("link").createLink(originalFile.getRelativePathFromBase())
        dirLink = inputDirectory.file("linkDir").createLink(originalDir.getRelativePathFromBase())
    }

    def "copying files again should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        checkUpToDate(preserveLinks, upToDate) {
            // do nothing
        }

        where:
        preserveLinks        | upToDate
        "LinksStrategy.ALL"  | true
        "LinksStrategy.NONE" | true
    }

    def "when target content is changed, the task should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        checkUpToDate(preserveLinks, upToDate) {
            originalFile << "new text"
        }

        where:
        preserveLinks            | upToDate
        "LinksStrategy.ALL"      | false
        "LinksStrategy.NONE"     | false
    }

    def "when target is changed, the task should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        def sameContentAsOriginal = originalDir.createFile("original2.txt") << originalFile.text

        checkUpToDate(preserveLinks, upToDate) {
            fileLink.delete()
            Files.createSymbolicLink(fileLink.toPath(), Paths.get(sameContentAsOriginal.getRelativePathFromBase()))
        }

        where:
        preserveLinks        | upToDate
        "LinksStrategy.ALL"  | false
        "LinksStrategy.NONE" | false // should be true
    }

    def "when dir link is replaced by its contents, the task should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        checkUpToDate(preserveLinks, upToDate) {
            dirLink.delete()
            FileUtils.copyDirectory(originalDir, dirLink)
        }

        where:
        preserveLinks        | upToDate
        "LinksStrategy.ALL"  | false
        "LinksStrategy.NONE" | false // should be true
    }

    def "when file link is replaced by its contents, the task should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        checkUpToDate(preserveLinks, upToDate) {
            fileLink.delete()
            FileUtils.copyFile(originalFile, fileLink)
        }

        where:
        preserveLinks        | upToDate
        "LinksStrategy.ALL"  | false
        "LinksStrategy.NONE" | false // should be true
    }

    def "when file is replaced by a link pointing to the original, the task should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        fileLink.delete()
        dirLink.delete()
        originalDir.createFile(fileLink.name) << originalFile.text

        checkUpToDate(preserveLinks, upToDate) {
            fileLink.delete()
            originalDir.file(fileLink.name).createLink(originalFile.getRelativePathFromBase())
        }

        where:
        preserveLinks        | upToDate
        "LinksStrategy.ALL"  | false
        "LinksStrategy.NONE" | false // should be true
    }

    def "when configuration is changed, the task should have up-to-date=#upToDate if preserveLinks was #preserveLinks and became #preserveLinksAfter"() {
        checkUpToDate(preserveLinks, upToDate) {
            buildKotlinFile.text = """
            tasks.register<Copy>("cp") {
                preserveLinks = $preserveLinksAfter
                from("${inputDirectory.name}")

                into("${outputDirectory.name}")
            }
            """
        }

        where:
        preserveLinks        | preserveLinksAfter   | upToDate
        "LinksStrategy.ALL"  | "LinksStrategy.NONE" | false
        "LinksStrategy.NONE" | "LinksStrategy.ALL"  | false
    }

    def "when a relative link is replaced to absolute, the task should have up-to-date=#upToDate if preserveLinks=#preserveLinks"() {
        checkUpToDate(preserveLinks, upToDate) {
            fileLink.delete()
            Files.createSymbolicLink(fileLink.toPath(), Paths.get(originalFile.getCanonicalPath()))
        }

        where:
        preserveLinks        | upToDate
        "LinksStrategy.ALL"  | false
        "LinksStrategy.NONE" | false // should be true
    }

    private def checkUpToDate(String preserveLinks, Boolean shouldBeUpToDate, Closure change) {
        buildKotlinFile << """
            tasks.register<Copy>("cp") {
                preserveLinks = $preserveLinks
                from("${inputDirectory.name}")

                into("${outputDirectory.name}")
            }
            """
        succeeds(":cp")
        executed(":cp")

        change()

        succeeds(":cp")
        if (shouldBeUpToDate) {
            skipped(":cp")
        } else {
            executedAndNotSkipped(":cp")
        }
    }
}
