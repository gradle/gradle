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

package org.gradle.launcher.continuous

import org.gradle.integtests.fixtures.AbstractContinuousIntegrationTest
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives
import spock.lang.Ignore

@TestReproducibleArchives
class ArchivesContinuousIntegrationTest extends AbstractContinuousIntegrationTest {
    def "creating zips"() {
        given:
        def sourceDir = file("src")
        def subDir = sourceDir.file("subdir")
        def outputFile = file("build/distributions/zip.zip")
        def unpackDir = file("build/unpack")

        when:
        subDir.mkdirs()
        unpackDir.mkdirs()
        sourceDir.file("README").text = "README"
        subDir.file("A").text = "A"
        buildFile << """
            apply plugin: 'base'
            task zip(type: Zip) {
                archiveFileName = "zip.zip"
                from("src")
            }
        """

        then:
        succeeds("zip")
        executedAndNotSkipped(":zip")
        outputFile.exists()
        outputFile.unzipTo(unpackDir)
        unpackDir.file("README").text == "README"
        unpackDir.file("subdir/A").text == "A"

        when:
        subDir.file("B").text = "B"

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":zip")
        outputFile.exists()
        outputFile.unzipTo(unpackDir)
        unpackDir.file("subdir/B").text == "B"

        when:
        sourceDir.file("newdir").createDir()

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":zip")
    }

    def "using compressed files as inputs - #type #packType #source - readonly #readonly"() {
        given:
        def packDir = file("pack").createDir()
        def outputDir = file("unpack")
        def sourceFile = file(source)

        def permissions = readonly
            ? """
                fileMode = 0644
                dirMode = 0755
              """
            : ""

        buildFile << """
            task unpack(type: Sync) {
                from($type("${sourceFile.toURI()}"))
                into("unpack")
                ${permissions}
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        """

        when:
        packDir.file("A").text = "original"
        packDir."$packType"(sourceFile, readonly)

        then:
        succeeds("unpack")
        executedAndNotSkipped(":unpack")
        outputDir.file("A").text == "original"

        when:
        // adding a new file to the archive instead of modifying 'A' because
        // zipTo won't update the zip file if Ant's zip task thinks the files
        // have not changed.
        packDir.file("B").text = "new-file"
        packDir."$packType"(sourceFile, readonly)

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":unpack")
        outputDir.file("A").text == "original"
        outputDir.file("B").text == "new-file"

        where:
        type      | packType | source        | readonly
        "zipTree" | "zipTo"  | "source.zip"  | true
        "zipTree" | "zipTo"  | "source.zip"  | false
        "tarTree" | "tarTo"  | "source.tar"  | true
        "tarTree" | "tarTo"  | "source.tar"  | false
        "tarTree" | "tgzTo"  | "source.tgz"  | true
        "tarTree" | "tgzTo"  | "source.tgz"  | false
        "tarTree" | "tbzTo"  | "source.tbz2" | true
        "tarTree" | "tbzTo"  | "source.tbz2" | false
    }

    @Ignore("inputs from resources are ignored")
    def "using compressed files as inputs from resources - #source"() {
        given:
        def packDir = file("pack").createDir()
        def outputDir = file("unpack")
        def sourceFile = file(source)

        buildFile << """
            task unpack(type: Sync) {
                from($type(resources.$resourceType("${sourceFile.toURI()}")))
                into("unpack")
            }
        """

        when:
        packDir.file("A").text = "original"
        packDir."$packType"(sourceFile)

        then:
        succeeds("unpack")
        executedAndNotSkipped(":unpack")
        outputDir.file("A").text == "original"

        when:
        packDir.file("A") << "-changed"
        packDir."$packType"(sourceFile)

        then:
        buildTriggeredAndSucceeded()
        executedAndNotSkipped(":unpack")
        outputDir.file("A").text == "original-changed"

        where:
        type      | packType | resourceType | source
        "tarTree" | "tgzTo"  | "gzip"       | "source.tgz"
        "tarTree" | "tbzTo"  | "bzip2"      | "source.tbz2"
    }
}
