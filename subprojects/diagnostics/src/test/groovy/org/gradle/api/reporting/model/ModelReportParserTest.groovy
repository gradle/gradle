/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.reporting.model

import spock.lang.Specification
import spock.lang.Unroll

class ModelReportParserTest extends Specification {

    @Unroll
    def "fails with invalid text"() {
        when:
        new ModelReportParser(text).toModelReport()
        then:
        def ex = thrown(java.lang.AssertionError)
        ex.message.startsWith message

        where:
        text | message
        ''   | 'Report text must not be blank'
        null | 'Report text must not be blank'
        's'  | 'Should have at least 7 lines'
    }

    def "fails when missing the success marker"() {
        when:
        new ModelReportParser("""1
2
3
4
5
6
+ model
BUILD SUCCESSFUsL
""").toModelReport()
        then:
        def ex = thrown(java.lang.AssertionError)
        ex.message.startsWith "Expected to find an end of report marker '${ModelReportParser.END_OF_REPORT_MARKER}'"
    }

    def "fails when missing a root node"() {
        when:
        new ModelReportParser("""1
2
3
4
5
6
+ incorrect
BUILD SUCCESSFUL
""").toModelReport()
        then:
        def ex = thrown(java.lang.AssertionError)
        ex.message.startsWith "Expected to find the root node '${ModelReportParser.ROOT_NODE_MARKER}'"
    }

    def "should parse a report with no children"() {
        def modelReport = new ModelReportParser(""":model


My Report


+ model
BUILD SUCCESSFUL
""").toModelReport()
        expect:
        modelReport.title == 'My Report'
        modelReport.reportNode.name() == 'model'
        modelReport.reportNode.children() == []
    }

    def "should parse a model report with child nodes and vules"() {
        setup:
        def modelReport = new ModelReportParser(""":model


My Report


+ model
    + nullCredentials
          | Type: \t PasswordCredentials |
        + password
              | Type: \t java.lang.String |
        + username
              | Type: \t java.lang.String |
    + numbers
          | Type: \t Numbers |
        + value
              | Value: \t 5 |
              | Type: \t java.lang.Integer |
    + primaryCredentials
          | Type: \t PasswordCredentials |
        + password
              | Value: \t hunter2 |
              | Type: \t java.lang.String |
        + username
              | Value: \t uname |
              | Type: \t java.lang.String |

BUILD SUCCESSFUL
""").toModelReport()

        expect:
        modelReport.reportNode.'**'.primaryCredentials.username.@nodeValue[0] == 'uname'
        modelReport.reportNode.'**'.primaryCredentials.username.@type[0] == 'java.lang.String'
    }

    def "should find a node attributes"() {
        ModelReportParser modelReportParser = new ModelReportParser()
        ReportNode reportNode = new ReportNode('test')
        when:
        modelReportParser.setNodeProperties(line, reportNode)

        then:
        reportNode.attribute(expectedAttribute) == expectedValue
        where:
        line                       | expectedAttribute | expectedValue
        '| Value: \t some value |' | 'nodeValue'       | 'some value'
        '| Type: \t some type |'   | 'type'            | 'some type'
    }
}
