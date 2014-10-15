/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import spock.lang.Specification

class MavenVersionSelectorSchemeTest extends Specification {
    def defaultMatcher = Mock(VersionSelectorScheme)
    def mapper = new MavenVersionSelectorScheme(defaultMatcher)
    def selector = Mock(VersionSelector)

    def "translates to maven syntax"() {
        when:
        1 * defaultMatcher.renderSelector(selector) >> input

        then:
        mapper.renderSelector(selector) == output

        where:
        input                | output
        "1.0"                | "1.0"
        "1+"                 | "[1,2)"
        "1.+"                | "[1,2)"
        "1.5.+"              | "[1.5,1.6)"
        "1.5+"               | "[1.5,1.6)"
        "1.100+"             | "[1.100,1.101)"
        "10.1+"              | "[10.1,10.2)"
        "+"                  | "LATEST"
        "latest.integration" | "LATEST"
        "latest.release"     | "RELEASE"
    }

    def "translates from maven syntax"() {
        when:
        1 * defaultMatcher.parseSelector(output) >> selector

        then:
        mapper.parseSelector(input) == selector

        where:
        output               | input
        "1.0"                | "1.0"
        "[1,2)"              | "[1,2)"
        "[1.5,1.6)"          | "[1.5,1.6)"
        "1.5+"               | "1.5+"
        "+"                  | "+"
        "latest.integration" | "LATEST"
        "latest.release"     | "RELEASE"
    }
}
