/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.text

import org.gradle.internal.logging.text.StyledTextOutput
import spock.lang.Specification

class StyledTableTest extends Specification {
    def "should render a table with a single row"() {
        given:
        def table = new StyledTable(
            "",
            ["Key", "Value"],
            [
                new StyledTable.Row(["foo", "bar"], StyledTextOutput.Style.Normal)
            ]
        )

        expect:
        StyledTableUtil.toString(table) == """
            | Key | Value |
            |-----|-------|
            | foo | bar   |
        """.stripIndent(true).trim()
    }

    def "should render a table with a single row and indent"() {
        given:
        def table = new StyledTable(
            "[indent]",
            ["Key", "Value"],
            [
                new StyledTable.Row(["foo", "bar"], StyledTextOutput.Style.Normal)
            ]
        )

        expect:
        StyledTableUtil.toString(table) == """
            [indent]| Key | Value |
            [indent]|-----|-------|
            [indent]| foo | bar   |
        """.stripIndent(true).trim()
    }

    def "header is accounted for in spacing"() {
        given:
        def table = new StyledTable(
            "",
            ["Very Long"],
            [
                new StyledTable.Row(["foo"], StyledTextOutput.Style.Normal)
            ]
        )

        expect:
        StyledTableUtil.toString(table) == """
            | Very Long |
            |-----------|
            | foo       |
        """.stripIndent(true).trim()
    }

    def "longest string determines spacing"() {
        given:
        def table = new StyledTable(
            "",
            ["a", "b"],
            [
                new StyledTable.Row(["foo", "bar"], StyledTextOutput.Style.Normal),
                new StyledTable.Row(["very long", "short"], StyledTextOutput.Style.Normal),
                new StyledTable.Row(["tiny", "even longer"], StyledTextOutput.Style.Normal)
            ]
        )

        expect:
        StyledTableUtil.toString(table) == """
            | a         | b           |
            |-----------|-------------|
            | foo       | bar         |
            | very long | short       |
            | tiny      | even longer |
        """.stripIndent(true).trim()
    }

    def "fails when header and row sizes differ"() {
        given:
        def header = ["a"]
        def rows = [
            new StyledTable.Row(["b"], StyledTextOutput.Style.Normal),
            new StyledTable.Row(["c"], StyledTextOutput.Style.Normal),
            new StyledTable.Row(["d", "oops a mistake"], StyledTextOutput.Style.Normal)
        ]

        when:
        new StyledTable(
            "",
            header,
            rows
        )

        then:
        def ex = thrown(IllegalArgumentException)
        ex.getMessage() == "Header and row 2 must have the same number of columns"
    }
}
