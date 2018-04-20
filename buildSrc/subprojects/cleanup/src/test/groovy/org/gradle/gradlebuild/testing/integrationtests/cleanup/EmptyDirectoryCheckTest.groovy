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

package org.gradle.gradlebuild.testing.integrationtests.cleanup

import spock.lang.Unroll

class EmptyDirectoryCheckTest extends AbstractIntegrationTest {
    File targetDir
    File reportFile

    def setup() {
        targetDir = file("build/tmp/test files")
        reportFile = file("build/reports/remains.txt")
        buildFile << """
            plugins {
                id "gradlebuild.test-files-cleanup"
            }
        """
    }

    @Unroll
    def "empty directory creates no output. errors: #behavior"() {
        given:
        setBehaviorWhenNotEmpty(behavior)

        when:
        build("verifyTestFilesCleanup")

        then:
        !reportFile.exists()

        where:
        behavior << ["REPORT", "FAIL"]
    }

    def "reports existing files"() {
        given:
        def files = populateFiles()
        setBehaviorWhenNotEmpty("REPORT")

        when:
        build("verifyTestFilesCleanup")

        then:
        assertReportHasFiles(files)
    }

    def "reports existing files and errors"() {
        given:
        def files = populateFiles()

        when:
        buildAndFail("verifyTestFilesCleanup")

        then:
        assertReportHasFiles(files)
        result.output.contains("The directory ${targetDir.canonicalPath} was not empty. Report: file://")
    }

    private void assertReportHasFiles(List<File> files) {
        assert reportFile.exists()
        def reportedFiles = reportFile.readLines()
        assert reportedFiles.containsAll(files.collect { it.canonicalPath })
    }

    private void setBehaviorWhenNotEmpty(behavior) {
        buildFile << """
            import ${WhenNotEmpty.canonicalName}
            verifyTestFilesCleanup.policy = WhenNotEmpty.$behavior
        """
    }

    private List<File> populateFiles() {
        File subdir = new File(targetDir, "subdir")
        subdir.mkdirs()

        List<File> files = []
        files << new File(targetDir, "root.txt")
        files << new File(subdir, "file.txt")
        files << new File(subdir, "file2.txt")
        files.each { file ->
            file.text = file.absolutePath
        }
        return files
    }
}
