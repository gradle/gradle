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
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

class SamplesFilesMiscIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("files/misc")
    def "can create a directory with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('ensureDirectory')

        then:
        dslDir.file('images').isDirectory()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/misc")
    def "can move a directory with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('moveReports')

        then:
        def toArchiveDir = dslDir.file("build/toArchive")
        toArchiveDir.file("reports").isDirectory()
        toArchiveDir.file("reports/my-report.pdf").isFile()
        toArchiveDir.file("reports/numbers.csv").isFile()
        toArchiveDir.file("reports/metrics/scatterPlot.pdf").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/misc")
    @Requires(IntegTestPreconditions.IsConfigCached)
    def "can move a directory with #dsl dsl with configuration cache"() {
        given:
        def dslDir = sample.dir.file(dsl)

        expect:
        2.times {
            def reportsDir = dslDir.file('build/reports')
            reportsDir.createDir().file('my-report.pdf').touch()
            reportsDir.file('numbers.csv').touch()

            executer.inDirectory(dslDir)
            succeeds("moveReports", "--rerun-tasks")

            dslDir.file("build/toArchive").deleteDir()
        }

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/misc")
    def "can delete a directory with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        dslDir.file("build").createDir().file("dummy.txt").touch()

        when:
        succeeds('myClean')

        then:
        dslDir.file('build').assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/misc")
    def "can delete files matching a pattern with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('cleanTempFiles')

        then:
        def srcDir = dslDir.file('src')
        srcDir.file("notes.txt.tmp").assertDoesNotExist()
        srcDir.file("README.md").isFile()
        srcDir.file("main/webapp/web.xml.tmp").assertDoesNotExist()
        srcDir.file("main/webapp/web.xml").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/misc")
    def "can use the rootDir property in a child project with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        expect:
        succeeds(':project2:checkConfigFile')

        where:
        dsl << ['groovy', 'kotlin']
    }
}
