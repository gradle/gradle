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
import spock.lang.Ignore

class ArchivesContinuousIntegrationTest extends AbstractContinuousModeExecutionIntegrationTest {

    def "creating zips in continuous mode"() {
        given:
        def sourceDir = file("src")
        def subDir = sourceDir.file("subdir")
        def outputFile = file("build/distributions/zip.zip")
        def unpackDir = file("build/unpack")

        when:
        subDir.mkdirs()
        unpackDir.mkdirs()
        sourceDir.file("README").text = "Read me"
        subDir.file("A").text = "A"
        buildFile << """
    apply plugin: 'base'
    task zip(type: Zip) {
        archiveName = "zip.zip"
        from("src")
    }
"""
        then:
        succeeds("zip")
        executedAndNotSkipped(":zip")
        outputFile.exists()
        outputFile.unzipTo(unpackDir)
        unpackDir.file("README").exists()
        unpackDir.file("subdir/A").exists()

        when:
        subDir.file("B").text = "B"
        then:
        succeeds()
        executedAndNotSkipped(":zip")
        outputFile.exists()
        outputFile.unzipTo(unpackDir)
        unpackDir.file("subdir/B").exists()

        // TODO: This triggers a build, but we still consider the zip task
        // up-to-date even though there's a new directory
//        when:
//        sourceDir.file("newdir").createDir()
//        then:
//        succeeds()
//        executedAndNotSkipped(":zip")
    }

    @Ignore("source files for compressed inputs are not considered")
    def "using compressed files as inputs"() {
        given:
        def packDir = file("pack").createDir()
        def outputDir = file("unpack")
        def sourceFile = file(source)
        buildFile << """
    task unpack(type: Sync) {
        from($type("${sourceFile.toURI()}"))
        into("unpack")
    }
"""
        when:
        packDir.file("A").text = "original"
        packDir.file("subdir").createDir().file("B").text = "B"
        packDir.file("subdir2").createDir()
        packDir."$packType"(sourceFile)
        then:
        succeeds("unpack")
        executedAndNotSkipped(":unpack")
        outputDir.file("A").text == "original"
        outputDir.file("subdir/B").exists()
        outputDir.file("subdir2").exists()

        when:
        packDir.file("A").text = "changed"
        packDir."$packType"(sourceFile)
        println outputDir.listFiles()
        then:
        succeeds()
        executedAndNotSkipped(":unpack")
        outputDir.file("A").text == "changed"

        cleanup:
        packDir.deleteDir()

        where:
        type      | packType | source
        "zipTree" | "zipTo"  | "source.zip"
        "tarTree" | "tarTo"  | "source.tar"
        "tarTree" | "tgzTo"  | "source.tgz"
    }

}
