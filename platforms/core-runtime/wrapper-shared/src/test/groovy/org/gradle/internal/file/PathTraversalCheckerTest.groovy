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

package org.gradle.internal.file


import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Specification

import static org.gradle.internal.file.PathTraversalChecker.isUnsafePathName

class PathTraversalCheckerTest extends Specification {

    def "identifies potentially unsafe zip entry names"() {
        expect:
        isUnsafePathName(unsafePath)
        !isUnsafePathName(safePath)

        where:
        unsafePath     | safePath
        "/"            | "foo/"
        "\\"           | "foo\\"
        "/foo"         | "foo"
        "\\foo"        | "foo"
        "foo/.."       | "foo/bar"
        "foo\\.."      | "foo\\bar"
        "../foo"       | "..foo"
        "..\\foo"      | "..foo"
        "foo/../bar"   | "foo/..bar"
        "foo\\..\\bar" | "foo\\..bar"
        // Technically, this is not a path traversal, but we consider it unsafe due to
        // code that might merge '\' and '/' later, thus introducing a path traversal.
        "foo\\../bar"  | "foo\\..bar"
    }

    @Requires(
        value = UnitTestPreconditions.Windows,
        reason = "These path patterns are only unsafe on Windows systems"
    )
    def "identifies potentially unsafe zip entry names (windows only)"() {
        expect:
        isUnsafePathName(unsafePath)
        !isUnsafePathName(safePath)

        where:
        unsafePath     | safePath
        "C:/foo"       | "foo"
        "foo."         | "foo.txt"
        "foo.\\bar.txt"| "foo\\bar.txt"
        "foo./bar.txt" | "foo/bar.txt"
        "foo..\\bar"   | "..foo\\bar"
    }

    def "does not reject safe zip entry names with similar patterns"() {
        expect:
        !isUnsafePathName(safePath)

        where:
        safePath << [
            ".hidden",
            "foo/..bar",
            "foo\\..bar",
            "./..foo",
            ".\\..foo",
        ]
    }

    @Requires(
        value = UnitTestPreconditions.NotWindows,
        reason = "These path patterns are unsafe on Windows systems"
    )
    def "identifies potentially unsafe zip entry names (not windows)"() {
        expect:
        !isUnsafePathName(safePath)

        where:
        safePath << [
            "foo../bar",
            "foo..\\bar",
            ".../bar",
            "...\\bar",
            "foo...//",
            "foo...\\\\",
            "foo...//bar",
            "foo...\\\\bar"
        ]
    }
}
