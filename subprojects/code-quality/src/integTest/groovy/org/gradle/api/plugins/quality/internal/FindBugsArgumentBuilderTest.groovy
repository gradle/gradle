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

import spock.lang.Specification
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.file.FileCollection
import org.gradle.api.reporting.SingleFileReport

class FindBugsArgumentBuilderTest extends Specification{

    FileCollection classes = Mock()
    FindBugsArgumentBuilder builder = new FindBugsArgumentBuilder(classes)

    def "fails with empty classes Collection"(){
        when:
        new FindBugsArgumentBuilder(null)    
        then:
        thrown(InvalidUserDataException)

        when:
        classes.empty >> true
        new FindBugsArgumentBuilder(classes)
        then:
        thrown(InvalidUserDataException)
    }
    
    
    def "with reports disabled"(){
        setup:
        NamedDomainObjectSet enabledReportSet = Mock()
        FindBugsReportsImpl report = Mock()

        report.enabled >> enabledReportSet;
        enabledReportSet.empty >> true
        when:
            builder.configureReports(report)
            def args = builder.build()
        then:
            !args.contains("-outputFile")
    }

    def "more than 1 enabled report throws exception"(){
        setup:
        NamedDomainObjectSet enabledReportSet = Mock()
        FindBugsReportsImpl report = Mock()
        report.enabled >> enabledReportSet;
        enabledReportSet.empty >> false
        enabledReportSet.size() >> 2

        when:
        builder.configureReports(report)
        builder.build()

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Findbugs tasks can only have one report enabled, however both the xml and html report are enabled. You need to disable one of them."
    }

    def "with report configured"(){
        setup:
        SingleFileReport singleReport = Mock()
        File destination = Mock()
        NamedDomainObjectSet enabledReportSet = Mock()
        FindBugsReportsImpl report = Mock()

        report.enabled >> enabledReportSet;
        report.firstEnabled >> singleReport
        singleReport.name >> reportType
        destination.absolutePath >> "/absolute/report/output"
        singleReport.destination >> destination
        enabledReportSet.empty >> false
        enabledReportSet.size() >> 1


        when:
        builder.configureReports(report)
        def args = builder.build()

        then:
        args.contains("-$reportType".toString())
        args.contains("-outputFile")
        args.contains(destination.absolutePath)
        
        where:
        reportType << ["xml", "html"];
    }
}
