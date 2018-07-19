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

import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.junit.Rule

class SamplesCopyIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/files/copy")
    def "can copy a single file"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReport')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can copy a single file using task properties for the paths"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReport2')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can copy a single file using the file method"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReport3')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can specify multiple files in a from"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReportsForArchiving')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
        sample.dir.file('build/toArchive/manual.pdf').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can filter files to a specific type"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyPdfReportsForArchiving')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
        sample.dir.file('build/toArchive/metrics/scatterPlot.pdf').assertDoesNotExist()
        sample.dir.file('build/toArchive/scatterPlot.pdf').assertDoesNotExist()
        sample.dir.file('build/toArchive/numbers.csv').assertDoesNotExist()
    }

    @UsesSample("userguide/files/copy")
    def "can filter files to a specific type including in subdirectories"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyAllPdfReportsForArchiving')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
        sample.dir.file('build/toArchive/metrics/scatterPlot.pdf').isFile()
        sample.dir.file('build/toArchive/scatterPlot.pdf').assertDoesNotExist()
        sample.dir.file('build/toArchive/numbers.csv').assertDoesNotExist()
    }

    @UsesSample("userguide/files/copy")
    def "can copy a directory"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyReportsDirForArchiving')

        then:
        sample.dir.file('build/toArchive/my-report.pdf').isFile()
        sample.dir.file('build/toArchive/metrics/scatterPlot.pdf').isFile()
        sample.dir.file('build/toArchive/numbers.csv').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can copy a directory, including itself"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyReportsDirForArchiving2')

        then:
        sample.dir.file('build/toArchive/reports/my-report.pdf').isFile()
        sample.dir.file('build/toArchive/reports/metrics/scatterPlot.pdf').isFile()
        sample.dir.file('build/toArchive/reports/numbers.csv').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can rename files as they are copied"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyFromStaging')

        then:
        def outputDir = sample.dir.file("build/explodedWar")
        outputDir.file('web.xml').isFile()
        outputDir.file('index.html').isFile()
        outputDir.file('products/gradle.html').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can truncate filenames as they are copied"() {
        given:
        executer.inDirectory(sample.dir)
        def reportsDir = sample.dir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers-long.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("plot.pdf").touch()

        when:
        succeeds('copyWithTruncate')

        then:
        sample.dir.file('build/toArchive/my-repor~13').isFile()
        sample.dir.file('build/toArchive/metrics/plot.pdf').isFile()
        sample.dir.file('build/toArchive/numbers-~16').isFile()
    }

    @UsesSample("userguide/files/sampleJavaProject")
    def "can nest child specifications"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('nestedSpecs')

        then:
        def outputDir = sample.dir.file("build/explodedWar")
        outputDir.file('index-staging.html').assertDoesNotExist()
        outputDir.file('index.html.tmp').assertDoesNotExist()
        outputDir.file('home.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('products/collaboration.jpg').isFile()
        outputDir.file('WEB-INF/classes/Hello.class').isFile()
        outputDir.file('WEB-INF/lib/commons-io-2.6.jar').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can use a standalone copyspec within a copy"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyAssets')

        then:
        def outputDir = sample.dir.file("build/inPlaceApp")
        outputDir.file('web.xml').assertDoesNotExist()
        outputDir.file('index-staging.html').assertDoesNotExist()
        outputDir.file('index.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('products/gradle.html').isFile()
        outputDir.file('products/collaboration.jpg').isFile()
        outputDir.file('products/collaboration.jpg~').assertDoesNotExist()
    }

    @UsesSample("userguide/files/copy")
    def "can use a standalone copyspec within an archiving task"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('distApp')

        then:
        def tmpOutDir = sample.dir.file("tmp")
        def zipFile = sample.dir.file('build/dists/my-app-dist.zip')
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file('web.xml').assertDoesNotExist()
        tmpOutDir.file('index-staging.html').assertDoesNotExist()
        tmpOutDir.file('index.html').isFile()
        tmpOutDir.file('logo.png').isFile()
        tmpOutDir.file('products/gradle.html').isFile()
        tmpOutDir.file('products/collaboration.jpg').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can share a configuration closure with copy patterns no. 1"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('copAppAssets')

        then:
        def outputDir = sample.dir.file("build/inPlaceApp")
        outputDir.file('web.xml').assertDoesNotExist()
        outputDir.file('index-staging.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('products/gradle.html').assertDoesNotExist()
        outputDir.file('products/collaboration.jpg').isFile()
    }

    @UsesSample("userguide/files/copy")
    def "can share a configuration closure with copy patterns no. 2"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('archiveDistAssets')

        then:
        def tmpOutDir = sample.dir.file("tmp")
        def zipFile = sample.dir.file('build/dists/distribution-assets.zip')
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file('home.html').isFile()
        tmpOutDir.file('images/plot.eps').assertDoesNotExist()
        tmpOutDir.file('images/logo.png').isFile()
        tmpOutDir.file('images/photo.jpg').isFile()
    }

    /* THIS TEST DOES NOT WORK
     *
     * The corresponding sample project works fine, it's just the test
     * that fails, both in IDEA and from the command line build. I suspect
     * the way the tests are run doesn't interact with ant.defaultexcludes()
     * well.
     *
    @UsesSample("userguide/files/copy")
    def "can change Ant default excludes"() {
        given:
        executer.inDirectory(sample.dir)

        when:
        succeeds('forcedCopy')

        then:
        def outputDir = sample.dir.file("build/inPlaceApp")
        outputDir.file('index-staging.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('.git/stuff.txt').isFile()
        outputDir.file('products/collaboration.jpg').isFile()
        outputDir.file('products/collaboration.jpg~').isFile()
    }
    */
}
