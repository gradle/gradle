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

import jodd.jerry.Jerry
import org.gradle.api.reporting.Report
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import static jodd.jerry.Jerry.jerry

class BuildDashboardGeneratorSpec extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    File outputFile
    BuildDashboardGenerator generator

    void setup() {
        outputFile = tmpDir.file('output.html')
    }

    private void generatorFor(reports) {
        generator = new BuildDashboardGenerator(reports as Set, outputFile)
    }

    private Jerry getOutputHtml() {
        jerry(outputFile.text)
    }

    Report mockReport(String name, File destination) {
        Stub(Report) {
            getDisplayName() >> name
            getDestination() >> destination
        }
    }

    void 'appropriate message is displayed when there are no reports available'() {
        given:
        generatorFor([])

        when:
        generator.generate()

        then:
        outputHtml.find('h1').text().trim() == 'There are no build reports available.'
    }

    void 'links to reports are added to the generated markup'() {
        given:
        generatorFor([
                mockReport('a', tmpDir.createFile('report.html')),
                mockReport('b', tmpDir.createDir('inner').createFile('otherReport.html')),
                mockReport('c', tmpDir.file('idonotexist.html')),
                mockReport('d', null)
        ])

        when:
        generator.generate()

        then:
        outputHtml.find('h1').text().trim() == 'Available build reports:'
        with outputHtml.find('ul li a'), {
            size() == 2
            filter('[href="report.html"]').text().trim() == 'a'
            filter('[href="inner/otherReport.html"]').text().trim() == 'b'
        }
    }

    void 'report css is set up'() {
        given:
        generatorFor([])

        when:
        generator.generate()

        then:
        outputHtml.find('head link[type="text/css"]').attr('href') == 'base-style.css'
        tmpDir.file('base-style.css').text == getClass().getResource('/org/gradle/reporting/base-style.css').text
    }
}
