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
        ModelReportParser.parse(text)

        then:
        def ex = thrown(AssertionError)
        ex.message.startsWith message

        where:
        text | message
        ''   | 'Report text must not be blank'
        null | 'Report text must not be blank'
        's'  | 'Should have at least 7 lines'
    }

    def "fails when missing the success marker"() {
        when:
        ModelReportParser.parse("""1
2
3
4
5
6
+ model
BUILD SUCCESSFUsL
""")
        then:
        def ex = thrown(AssertionError)
        ex.message.startsWith "Expected to find an end of report marker '${ModelReportParser.END_OF_REPORT_MARKER}'"
    }

    def "fails when missing a root node"() {
        when:
        ModelReportParser.parse("""1
2
3
4
5
6
+ incorrect
BUILD SUCCESSFUL
""")
        then:
        def ex = thrown(AssertionError)
        ex.message.startsWith "Expected to find the root node '${ModelReportParser.ROOT_NODE_MARKER}'"
    }

    def "should parse a report with no children"() {
        def modelReport = ModelReportParser.parse(""":model


My Report


+ model
BUILD SUCCESSFUL
""")
        expect:
        modelReport.title == 'My Report'
        modelReport.reportNode.name() == 'model'
        modelReport.reportNode.children() == []
    }

    def "should parse a model report with child nodes and values"() {
        setup:
        def modelReport = ModelReportParser.parse(""":model


My Report


+ model
    + nullCredentials
          | Type: \t PasswordCredentials
        + password
              | Type: \t java.lang.String
        + username
              | Type: \t java.lang.String
    + numbers
          | Type: \t Numbers
        + value
              | Value: \t 5
              | Type: \t java.lang.Integer
    + primaryCredentials
          | Type: \t PasswordCredentials
          | Rules:
                ⤷ Rule1
                ⤷ Rule2
        + password
              | Value: \t hunter2
              | Type: \t java.lang.String
        + username
              | Value: \t uname
              | Type: \t java.lang.String

BUILD SUCCESSFUL
""")

        expect:
        modelReport.reportNode.'**'.primaryCredentials.username.@nodeValue[0] == 'uname'
        modelReport.reportNode.'**'.primaryCredentials.username.@type[0] == 'java.lang.String'
        modelReport.reportNode.'**'.primaryCredentials.@rules[0][0]== 'Rule1'
        modelReport.reportNode.'**'.primaryCredentials.@rules[0][1]== 'Rule2'
    }

    def "should find a node attributes"() {
        ReportNode reportNode = new ReportNode('test')
        when:
        ModelReportParser.setNodeProperties(line, reportNode)

        then:
        reportNode.attribute(expectedAttribute) == expectedValue
        where:
        line                       | expectedAttribute | expectedValue
        '| Value: \t some value' | 'nodeValue'       | 'some value'
        '| Type: \t some type'   | 'type'            | 'some type'
    }
}
