/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems.internal

import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import spock.lang.Specification

class ProblemIdAndProblemGroupTest extends Specification {

    def 'name and displayName cannot be null or empty or only whitespace'() {
        when:
        def rootGroup = ProblemGroup.create(rootGroupName, rootGroupDisplayName)
        def subGroup = ProblemGroup.create(subGroupName, subGroupDisplayName, rootGroup)
        def id = ProblemId.create(idName, idDisplayName, subGroup)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == expectedMessage

        where:
        rootGroupName | subGroupName | idName  | rootGroupDisplayName | subGroupDisplayName | idDisplayName || expectedMessage
        ""            | "valid"      | "valid" | "valid"              | "valid"             | "valid"       || 'Problem group name must not be blank'
        "  "          | "valid"      | "valid" | "valid"              | "valid"             | "valid"       || 'Problem group name must not be blank'
        "valid"       | ""           | "valid" | "valid"              | "valid"             | "valid"       || 'Problem group name must not be blank'
        "valid"       | "  "         | "valid" | "valid"              | "valid"             | "valid"       || 'Problem group name must not be blank'
        "valid"       | "valid"      | ""      | "valid"              | "valid"             | "valid"       || 'Problem id name must not be blank'
        "valid"       | "valid"      | "  "    | "valid"              | "valid"             | "valid"       || 'Problem id name must not be blank'
        "valid"       | "valid"      | "valid" | ""                   | "valid"             | "valid"       || 'Problem group displayName must not be blank'
        "valid"       | "valid"      | "valid" | "  "                 | "valid"             | "valid"       || 'Problem group displayName must not be blank'
        "valid"       | "valid"      | "valid" | "valid"              | ""                  | "valid"       || 'Problem group displayName must not be blank'
        "valid"       | "valid"      | "valid" | "valid"              | "  "                | "valid"       || 'Problem group displayName must not be blank'
        "valid"       | "valid"      | "valid" | "valid"              | "valid"             | ""            || 'Problem id displayName must not be blank'
        "valid"       | "valid"      | "valid" | "valid"              | "valid"             | "  "          || 'Problem id displayName must not be blank'
    }

    def 'multiline names are flattened to a single line'() {
        when:
        def rootGroup = ProblemGroup.create("ro\no\r\nt\r\n\r\nGroupName", "ro\no\r\nt\r\n\r\nGroupDisplayName")
        def subGroup = ProblemGroup.create("su\nb\r\nG\r\n\r\nroupName", "su\nb\r\nG\r\n\r\nroupDisplayName", rootGroup)
        def id = ProblemId.create("id\nN\r\na\r\n\r\nme", "i\nd\r\nD\r\n\r\nisplayName", subGroup)

        then:
        id.name == 'idName'
        id.displayName == 'idDisplayName'
        id.group.name == 'subGroupName'
        id.group.displayName == 'subGroupDisplayName'
        id.group.parent.name == 'rootGroupName'
        id.group.parent.displayName == 'rootGroupDisplayName'
    }
}
