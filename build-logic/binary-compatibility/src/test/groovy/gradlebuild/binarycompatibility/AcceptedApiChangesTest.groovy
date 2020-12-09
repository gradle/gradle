/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility

import spock.lang.Specification

class AcceptedApiChangesTest extends Specification {

    def "parses accepted change"() {
        when:
        def changes = AcceptedApiChanges.parse("""
            {
                "acceptedApiChanges": [
                    {
                        "type": "org.gradle.api.initialization.ConfigurableIncludedBuild",
                        "member": "Implemented interface org.gradle.api.initialization.IncludedBuild",
                        "changes": ["Interface has been removed"],
                        "acceptation": "@Incubating interface has been removed"
                    }
                ]
            }
        """)

        then:
        changes.acceptedChanges.size() == 1
        def acceptedChange = changes.acceptedChanges.entrySet().iterator().next()
        def change = acceptedChange.key
        change.type == "org.gradle.api.initialization.ConfigurableIncludedBuild"
        change.member == "Implemented interface org.gradle.api.initialization.IncludedBuild"
        change.changes == ["Interface has been removed"]
        acceptedChange.value == "@Incubating interface has been removed"
    }

    def "parses more than one change"() {
        when:
        def changes = AcceptedApiChanges.parse("""
            {
                "acceptedApiChanges": [
                    {
                        "type": "org.gradle.api.initialization.ConfigurableIncludedBuild",
                        "member": "Implemented interface org.gradle.api.initialization.IncludedBuild",
                        "changes": ["Interface has been removed"],
                        "acceptation": "@Incubating interface has been removed"
                    },
                    {
                        "type": "other.Type",
                        "member": "Method other.Type.someMethod",
                        "changes": ["Method has been removed"],
                        "acceptation": "I really want to do this"
                    }
                ]
            }
        """)

        then:
        changes.acceptedChanges.size() == 2
    }

}
