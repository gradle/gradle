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

package org.gradle.api.plugins.quality.internal

import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.quality.internal.findbugs.FindBugsSpecBuilder
import org.gradle.api.reporting.SingleFileReport
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class FindBugsSpecBuilderTest extends Specification {
    @Rule TestNameTestDirectoryProvider tempFolder = new TestNameTestDirectoryProvider()

    FileCollection classes = Mock()
    FindBugsSpecBuilder builder = new FindBugsSpecBuilder(classes)

    def setup(){
        classes.getFiles() >> []
    }

    def "fails with empty classes Collection"() {
        when:
        new FindBugsSpecBuilder(null)

        then:
        thrown(InvalidUserDataException)

        when:
        new FindBugsSpecBuilder(classes)

        then:
        classes.empty >> true
        thrown(InvalidUserDataException)
    }

    def "with reports disabled"() {
        setup:
        NamedDomainObjectSet enabledReportSet = Mock()
        FindBugsReportsImpl report = Mock()

        report.enabled >> enabledReportSet
        enabledReportSet.empty >> true

        when:
        builder.configureReports(report)
        def spec = builder.build()

        then:
        !spec.arguments.contains("-outputFile")
    }

    def "with debugging"() {
        when:
        builder.withDebugging(debug)
        def spec = builder.build()

        then:
        spec.debugEnabled == debug

        where:
        debug << [false, true]
    }

    def "more than 1 enabled report throws exception"() {
        setup:
        NamedDomainObjectSet enabledReportSet = Mock()
        FindBugsReportsImpl report = Mock()
        report.enabled >> enabledReportSet
        enabledReportSet.empty >> false
        enabledReportSet.size() >> 2

        when:
        builder.configureReports(report)
        builder.build()

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "FindBugs tasks can only have one report enabled, however both the XML and HTML report are enabled. You need to disable one of them."
    }

    def "with report configured"() {
        setup:
        SingleFileReport singleReport = Mock()
        File destination = Mock()
        NamedDomainObjectSet enabledReportSet = Mock()
        FindBugsReportsImpl report = Mock()

        report.enabled >> enabledReportSet
        report.firstEnabled >> singleReport
        singleReport.name >> reportType
        destination.absolutePath >> "/absolute/report/output"
        singleReport.destination >> destination
        enabledReportSet.empty >> false
        enabledReportSet.size() >> 1


        when:
        builder.configureReports(report)
        def args = builder.build().arguments

        then:
        args.contains("-$reportType".toString())
        args.contains("-outputFile")
        args.contains(destination.absolutePath)

        where:
        reportType << ["xml", "html"]
    }

    def "configure effort"() {
        when:
        def args = builder.withEffort(effort).build().arguments

        then:
        args.contains("-effort:$effort" as String)

        where:
        effort << ["min", "default", "max"]
    }

    def "detects invalid effort value"() {
        when:
        builder.withEffort("unknown")

        then:
        thrown(InvalidUserDataException)
    }

    def "configure report level"() {
        when:
        def args = builder.withReportLevel(level).build().arguments

        then:
        args.contains("-$level" as String)

        where:
        level << ["experimental", "low", "medium", "high"]
    }

    def "detects invalid report level value"() {
        when:
        builder.withReportLevel("unknown")

        then:
        thrown(InvalidUserDataException)
    }

    def "configure visitors"() {
        when:
        def args = builder.withVisitors(["Foo", "Bar"]).build().arguments.join(" ")

        then:
        args.contains("-visitors Foo,Bar")
    }

    def "configure omitVisitors"() {
        when:
        def args = builder.withOmitVisitors(["Foo", "Bar"]).build().arguments.join(" ")

        then:
        args.contains("-omitVisitors Foo,Bar")
    }

    def "configure includeFilter"() {
        def file = tempFolder.createFile("include.txt")

        when:
        def args = builder.withIncludeFilter(file).build().arguments.join(" ")

        then:
        args.contains("-include $file")
    }

    def "configure excludeFilter"() {
        def file = tempFolder.createFile("exclude.txt")

        when:
        def args = builder.withExcludeFilter(file).build().arguments.join(" ")

        then:
        args.contains("-exclude $file")
    }
}
