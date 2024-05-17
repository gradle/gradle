/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.quality.checkstyle


import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.quality.integtest.fixtures.CheckstyleCoverage
import org.gradle.util.internal.VersionNumber
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@TargetCoverage({ CheckstyleCoverage.getSupportedVersionsByJdk() })
class CheckstylePluginHtmlReportIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        file("build.gradle") << """
plugins {
    id 'java-library'
    id 'checkstyle'
}

${mavenCentralRepository()}

checkstyle {
    toolVersion = '$version'
}
        """
        def nameOfCheck = "JavadocMethod"
        if (versionNumber >= VersionNumber.parse('8.21')) {
            nameOfCheck = "MissingJavadocMethod"
        }
        file("config/checkstyle/checkstyle.xml") << """
<!DOCTYPE module PUBLIC
          "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
          "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
    <module name="TreeWalker">
        <module name="$nameOfCheck"/>
    </module>
</module>
        """
    }

    private void goodCode() {
        file("src/main/java/com/example/Foo.java").java """
            package com.example;

            public class Foo {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
        file("src/main/java/com/example/Bar.java").java """
            package com.example;

            public class Bar {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
        file("src/main/java/com/example/Baz.java").java """
            package com.example;

            public class Baz {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
    }

    private void badCode() {
        file("src/main/java/com/example/Foo.java").java """
            package com.example;

            public class Foo {
                public String getBar() {
                    return "bar";
                }
                public String getBar2() {
                    return "bar";
                }
                public String getBar3() {
                    return "bar";
                }
            }
        """
        file("src/main/java/com/example/Bar.java").java """
            package com.example;

            public class Bar {
                public String getBar() {
                    return "bar";
                }
            }
        """
        file("src/main/java/com/example/Baz.java").java """
            package com.example;

            public class Baz {
                /**
                 * This returns a bar.
                 *
                 * @return return the bar
                 */
                public String getBar() {
                    return "bar";
                }
            }
        """
    }

    def "generates HTML report with good code"() {
        goodCode()
        when:
        succeeds("checkstyleMain")
        then:
        def html = parseReport()
        def head = html.selectFirst("head")
        // Title of report is Checkstyle Violations
        head.select("title").text() == "Checkstyle Violations"
        // Has some CSS styling
        head.select("style").size() == 1
        def body = html.selectFirst("body")
        def summaryTable = parseTable(body.selectFirst(".summary"))
        summaryTable[0] == ["Total files checked", "Total violations", "Files with violations"]
        summaryTable[1] == ["3", "0", "0"]

        // Good code produces no violations
        body.select(".filelist").size() == 0
        def violations = body.selectFirst(".violations")
        violations.selectFirst("p").text() == "No violations were found."
        violations.select(".file-violation").size() == 0
    }

    def "generates HTML report with bad code"() {
        badCode()
        when:
        fails("checkstyleMain")
        then:
        def html = parseReport()
        def body = html.selectFirst("body")
        def summaryTable = parseTable(body.selectFirst(".summary"))
        summaryTable[1] == ["3", "4", "2"]

        // Bad code produces violations in Foo.java and Bar.java, but not Baz.java
        def fileList = parseTable(body.selectFirst(".filelist"))
        fileList[0] == ["File", "Total violations"]
        fileList[1][0].endsWith("Foo.java")
        fileList[1][1] == "3"
        fileList[2][0].endsWith("Bar.java")
        fileList[2][1] == "1"

        def violations = body.selectFirst(".violations")
        def fileViolations = violations.select(".file-violation")
        fileViolations.size() == 2

        // Bar.java violations
        fileViolations[0].selectFirst("h3").text().endsWith("Bar.java")
        def barViolations = parseTable(fileViolations[0].selectFirst(".violationlist"))
        barViolations[0] == ["Severity", "Description", "Line Number"]
        barViolations[1] == ["error", "Missing a Javadoc comment.", "5"]

        // Foo.java violations
        fileViolations[1].selectFirst("h3").text().endsWith("Foo.java")

        def fooViolations = parseTable(fileViolations[1].selectFirst(".violationlist"))
        fooViolations[0] == ["Severity", "Description", "Line Number"]
        fooViolations[1] == ["error", "Missing a Javadoc comment.", "5"]
        fooViolations[2] == ["error", "Missing a Javadoc comment.", "8"]
        fooViolations[3] == ["error", "Missing a Javadoc comment.", "11"]

        // Sanity check that the anchor link is correct
        def fooAnchorLink = body.selectXpath('//table[@class="filelist"]//tr[2]/td/a').attr("href")
        def fooAnchorName = "#" + fileViolations[1].selectFirst("a").attr("name")
        fooAnchorName == fooAnchorLink
    }

    private Document parseReport() {
        def htmlReport = file("build/reports/checkstyle/main.html")
        htmlReport.assertExists()
        return Jsoup.parse(htmlReport)
    }

    /**
     * Parse a HTML table into a list of lists.
     * @param table
     * @return each element in the list represents a list of column values for a row.
     */
    private List<List<String>> parseTable(Element table) {
        def result = []
        def rows = table.select("tr")
        rows.each {row ->
            def rowResult = []
            def cols = row.select("td")
            if (cols.isEmpty()) {
                cols = row.select("th")
            }
            cols.each {col ->
                rowResult << col.text()
            }
            result << rowResult
        }
        return result
    }
}
