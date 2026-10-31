/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.reporting.internal

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.reporting.DirectoryReport
import org.gradle.api.reporting.Report
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Rule
import spock.lang.Specification

class BuildDashboardGeneratorSpec extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    File outputFile
    BuildDashboardGenerator generator = new BuildDashboardGenerator()

    void setup() {
        outputFile = tmpDir.file('output.html')
    }

    Document getOutputHtml() {
        Jsoup.parse(outputFile, null)
    }

    Report mockReport(String name, File destination) {
        def directory = Mock(Directory) {
            getAsFile() >> destination
        }
        def destinationProperty = Mock(DirectoryProperty) {
            get() >> directory
        }
        Stub(Report) {
            getDisplayName() >> name
            getOutputLocation() >> destinationProperty
        }
    }

    Report mockDirectoryReport(String name, File destinationDirectory) {
        def directory = Mock(Directory) {
            getAsFile() >> destinationDirectory
        }
        def destinationProperty = Mock(DirectoryProperty) {
            get() >> directory
        }
        Stub(DirectoryReport) {
            getDisplayName() >> name
            getOutputLocation() >> destinationProperty
            getEntryPoint() >> new File(destinationDirectory, "index.html")
        }
    }

    void 'appropriate message is displayed when there are no reports available'() {
        when:
        generator.render([], outputFile)

        then:
        outputHtml.select('h1').text() == 'There are no build reports available.'
    }

    void 'links to reports are added to the generated markup'() {
        given:
        def htmlFolder = tmpDir.createDir('htmlContent');
        htmlFolder.createFile("index.html")

        when:
        generator.render([
                        mockReport('a', tmpDir.createFile('report.html')),
                        mockReport('b', tmpDir.createDir('inner').createFile('otherReport.html')),
                        mockReport('c', tmpDir.file('idonotexist.html')),
                        mockDirectoryReport('d', htmlFolder),
                        mockReport('e', tmpDir.createDir('simpleDirectory')),
                ], outputFile)

        then:
        outputHtml.select('h1').text() == 'Build reports'
        with outputHtml.select('ul li'), {
            size() == 5
            select('a[href=report.html]').text() == 'a'
            select('a[href=inner/otherReport.html]').text() == 'b'
            select('span[class=unavailable]').text() == 'c'
            select('a[href=htmlContent/index.html]').text() == 'd'
            select('a[href=simpleDirectory]').text() == 'e'
        }
    }

    void 'encodes output using utf-8'() {
        given:
        def htmlFolder = tmpDir.createDir('htmlContent');
        htmlFolder.createFile("index.html")

        when:
        generator.render([mockReport('\u03b1\u03b2', tmpDir.createFile('report.html'))], outputFile)

        then:
        outputHtml.select('h1').text() == 'Build reports'
        with outputHtml.select('ul li'), {
            size() == 1
            select('a[href=report.html]').text() == '\u03b1\u03b2'
        }
    }

    void 'report css is set up'() {
        when:
        generator.render([], outputFile)

        then:
        outputHtml.select('head link[type=text/css]').attr('href') == 'css/base-style.css'
        tmpDir.file('css/base-style.css').text == getClass().getResource('/org/gradle/reporting/base-style.css').text
    }
}
