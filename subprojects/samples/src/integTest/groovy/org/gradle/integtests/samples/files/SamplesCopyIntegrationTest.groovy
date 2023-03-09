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

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.test.precondition.TestPrecondition
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.IgnoreIf

class SamplesCopyIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("files/copy")
    def "can copy a single file with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReport')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can copy a single file using task properties for the paths with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReport2')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can copy a single file using the file method with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyReport3')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can specify multiple files in a from with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()

        when:
        succeeds('copyReportsForArchiving')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()
        dslDir.file('build/toArchive/manual.pdf').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can filter files to a specific type with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyPdfReportsForArchiving')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()
        dslDir.file('build/toArchive/metrics/scatterPlot.pdf').assertDoesNotExist()
        dslDir.file('build/toArchive/scatterPlot.pdf').assertDoesNotExist()
        dslDir.file('build/toArchive/numbers.csv').assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can filter files to a specific type including in subdirectories with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyAllPdfReportsForArchiving')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()
        dslDir.file('build/toArchive/metrics/scatterPlot.pdf').isFile()
        dslDir.file('build/toArchive/scatterPlot.pdf').assertDoesNotExist()
        dslDir.file('build/toArchive/numbers.csv').assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can copy a directory with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyReportsDirForArchiving')

        then:
        dslDir.file('build/toArchive/my-report.pdf').isFile()
        dslDir.file('build/toArchive/metrics/scatterPlot.pdf').isFile()
        dslDir.file('build/toArchive/numbers.csv').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can copy a directory, including itself with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("scatterPlot.pdf").touch()

        when:
        succeeds('copyReportsDirForArchiving2')

        then:
        dslDir.file('build/toArchive/reports/my-report.pdf').isFile()
        dslDir.file('build/toArchive/reports/metrics/scatterPlot.pdf').isFile()
        dslDir.file('build/toArchive/reports/numbers.csv').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can rename files as they are copied with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyFromStaging')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file('web.xml').isFile()
        outputDir.file('index.html').isFile()
        outputDir.file('products/gradle.html').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can truncate filenames as they are copied with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)
        def reportsDir = dslDir.file('build/reports')
        reportsDir.createDir().file('my-report.pdf').touch()
        reportsDir.file('numbers-long.csv').touch()

        and: "A PDF report in a subdirectory of build/reports"
        reportsDir.createDir("metrics").file("plot.pdf").touch()

        when:
        succeeds('copyWithTruncate')

        then:
        dslDir.file('build/toArchive/my-repor~13').isFile()
        dslDir.file('build/toArchive/metrics/plot.pdf').isFile()
        dslDir.file('build/toArchive/numbers-~16').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can use copy task with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyTask')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file('web.xml').isFile()
        outputDir.file('index-staging.html').isFile()
        outputDir.file('products/gradle-staging.html').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can use copy task with patterns with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyTaskWithPatterns')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file('web.xml').assertDoesNotExist()
        outputDir.file('index-staging.html').isFile()
        outputDir.file('products/gradle-staging.html').isFile()
        outputDir.file('draft.html').assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can use copy task with multiple from clauses with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('anotherCopyTask')

        then:
        def outputDir = dslDir.file("some-dir")
        outputDir.file('web.xml').isFile()
        outputDir.file('index-staging.html').isFile()
        outputDir.file('products/gradle-staging.html').isFile()
        outputDir.file('asset1.txt').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/22536")
    def "can use copy method in task with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyMethod')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file('web.xml').assertDoesNotExist()
        outputDir.file('index-staging.html').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    @ToBeFixedForConfigurationCache(because = "https://github.com/gradle/gradle/issues/22536")
    def "can use copy method in task with outputs with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyMethodWithExplicitDependencies')

        then:
        def outputDir = dslDir.file("some-dir")
        outputDir.file('web.xml').isFile()
        outputDir.file('index-staging.html').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    @IgnoreIf({ TestPrecondition.doSatisfies(UnitTestPreconditions.CaseInsensitiveFs) })
    def "can use copy task with rename with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('rename')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file('WEB.XML').isFile()
        outputDir.file('index.html').assertDoesNotExist()
        outputDir.file('index-staging.html').assertDoesNotExist()
        outputDir.file('INDEX-STAGING.HTML').assertDoesNotExist()
        outputDir.file('INDEX.HTML').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can use copy task with filter with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('filter')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file("index-staging.html").text.contains(String.format("[Copyright 2009]%n[Version 2.3.1]"))
        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/sampleJavaProject")
    def "can nest child specifications with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('nestedSpecs')

        then:
        def outputDir = dslDir.file("build/explodedWar")
        outputDir.file('index-staging.html').assertDoesNotExist()
        outputDir.file('index.html.tmp').assertDoesNotExist()
        outputDir.file('home.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('products/collaboration.jpg').isFile()
        outputDir.file('WEB-INF/classes/Hello.class').isFile()
        outputDir.file('WEB-INF/lib/commons-io-2.6.jar').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can use a standalone copyspec within a copy with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyAssets')

        then:
        def outputDir = dslDir.file("build/inPlaceApp")
        outputDir.file('web.xml').assertDoesNotExist()
        outputDir.file('index-staging.html').assertDoesNotExist()
        outputDir.file('index.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('products/gradle.html').isFile()
        outputDir.file('products/collaboration.jpg').isFile()
        outputDir.file('products/collaboration.jpg~').assertDoesNotExist()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can use a standalone copyspec within an archiving task with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('distApp')

        then:
        def tmpOutDir = dslDir.file("tmp")
        def zipFile = dslDir.file('build/dists/my-app-dist.zip')
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file('web.xml').assertDoesNotExist()
        tmpOutDir.file('index-staging.html').assertDoesNotExist()
        tmpOutDir.file('index.html').isFile()
        tmpOutDir.file('logo.png').isFile()
        tmpOutDir.file('products/gradle.html').isFile()
        tmpOutDir.file('products/collaboration.jpg').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can share a configuration closure with copy patterns no. 1 with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('copyAppAssets')

        then:
        def outputDir = dslDir.file("build/inPlaceApp")
        outputDir.file('web.xml').assertDoesNotExist()
        outputDir.file('index-staging.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('products/gradle.html').assertDoesNotExist()
        outputDir.file('products/collaboration.jpg').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @UsesSample("files/copy")
    def "can share a configuration closure with copy patterns no. 2 with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('archiveDistAssets')

        then:
        def tmpOutDir = dslDir.file("tmp")
        def zipFile = dslDir.file('build/dists/distribution-assets.zip')
        zipFile.isFile()
        zipFile.unzipTo(tmpOutDir)
        tmpOutDir.file('home.html').isFile()
        tmpOutDir.file('images/plot.eps').assertDoesNotExist()
        tmpOutDir.file('images/logo.png').isFile()
        tmpOutDir.file('images/photo.jpg').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    /* THIS TEST DOES NOT WORK
     *
     * The corresponding sample project works fine, it's just the test
     * that fails, both in IDEA and from the command line build. I suspect
     * the way the tests are run doesn't interact with ant.defaultexcludes()
     * well.
     *
    @UsesSample("files/copy")
    def "can change Ant default excludes with #dsl dsl"() {
        given:
        def dslDir = sample.dir.file(dsl)
        executer.inDirectory(dslDir)

        when:
        succeeds('forcedCopy')

        then:
        def outputDir = dslDir.file("build/inPlaceApp")
        outputDir.file('index-staging.html').isFile()
        outputDir.file('logo.png').isFile()
        outputDir.file('.git/stuff.txt').isFile()
        outputDir.file('products/collaboration.jpg').isFile()
        outputDir.file('products/collaboration.jpg~').isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }
    */
}
