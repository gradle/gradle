/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.files

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

class SamplesFilesMiscIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/files/misc")
    def "can create a directory"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('ensureDirectory')

        then:
        sample.dir.file('images').isDirectory()
    }

    @UsesSample("userguide/files/misc")
    def "can move a directory"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('moveReports')

        then:
        def toArchiveDir = sample.dir.file("build/toArchive")
        toArchiveDir.file("reports").isDirectory()
        toArchiveDir.file("reports/my-report.pdf").isFile()
        toArchiveDir.file("reports/numbers.csv").isFile()
        toArchiveDir.file("reports/metrics/scatterPlot.pdf").isFile()
    }

    @UsesSample("userguide/files/misc")
    def "can delete a directory"() {
        given:
        executer.inDirectory(sample.dir)
        sample.dir.file("build").createDir().file("dummy.txt").touch()

        when:
        succeeds('myClean')

        then:
        sample.dir.file('build').assertDoesNotExist()
    }

    @UsesSample("userguide/files/misc")
    def "can delete files matching a pattern"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('cleanTempFiles')

        then:
        def srcDir = sample.dir.file('src')
        srcDir.file("notes.txt.tmp").assertDoesNotExist()
        srcDir.file("README.md").isFile()
        srcDir.file("main/webapp/web.xml.tmp").assertDoesNotExist()
        srcDir.file("main/webapp/web.xml").isFile()
    }

    @UsesSample("userguide/files/misc")
    def "can use the rootDir property in a child project"() {
        given:
        executer.inDirectory(sample.dir)

        expect:
        succeeds(':project2:checkConfigFile')
    }
}
