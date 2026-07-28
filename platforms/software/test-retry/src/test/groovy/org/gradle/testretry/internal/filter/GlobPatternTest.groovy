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
package org.gradle.testretry.internal.filter

import spock.lang.Specification

class GlobPatternTest extends Specification {

    def "glob pattern matching"() {
        expect:
        with(GlobPattern.from("*")) {
            matches "a"
            matches "b"
            matches "*"
            matches " "
        }
        with(GlobPattern.from("**")) {
            matches "a"
            matches "b"
            matches "*"
            matches " "
        }
        with(GlobPattern.from(".")) {
            matches "."
            !matches("b")
        }
        with(GlobPattern.from("a*")) {
            matches "a"
            matches "ab"
            !matches("ba")
            !matches("b")
        }
        with(GlobPattern.from("a*a")) {
            matches "aba"
            matches "aa"
            !matches("ba")
            !matches("abc")
        }
        with(GlobPattern.from("**")) {
            matches "aba"
            matches "aa"
            matches ""
            matches "a"
        }
    }
}
