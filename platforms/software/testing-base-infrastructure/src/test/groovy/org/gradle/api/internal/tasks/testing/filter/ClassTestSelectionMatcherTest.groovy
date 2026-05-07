/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.filter

import spock.lang.Specification

class ClassTestSelectionMatcherTest extends Specification {
    def "matchesTest with default packages"() {
        expect:
        // This captures current behavior, but not desired behavior.
        // We cannot select classes in the default package without
        // including other classes because *.SomeTest requires SomeTest
        // to be in a package
        def matcher = new ClassTestSelectionMatcher(["*.SomeTest*"], [], [])
        !matcher.matchesTest("SomeTest", null) // This isn't desired
        matcher.matchesTest("sub.SomeTest", null)
        !matcher.matchesTest("sub.OtherSomeTest", null)
        and:
        // If you drop the dot from the pattern, we now incidentally include
        // tests that are in any package and end with the given pattern
        def matcherWithoutDot = new ClassTestSelectionMatcher(["*SomeTest*"], [], [])
        matcherWithoutDot.matchesTest("SomeTest", null)
        matcherWithoutDot.matchesTest("sub.SomeTest", null)
        matcherWithoutDot.matchesTest("sub.OtherSomeTest", null) // This is a side effect of the include
    }
}
