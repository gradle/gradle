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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class FileTestSelectionMatcherTest extends Specification {
    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "matchesFile with no patterns"() {
        def root = temp.createDir("root")
        def included = root.file("included.test").touch()
        def subIncluded = root.file("sub/included.test").touch()
        def subOther = root.file("sub/other.test").touch()
        def outsideRoot = temp.createDir("outside-root")
        def outsideExcluded = outsideRoot.file("excluded.test").touch()

        expect:
        def matcher = createMatcher([], [], root)
        matcher.matchesFile(included)
        matcher.matchesFile(subIncluded)
        matcher.matchesFile(subOther)
        // Not in the known roots
        !matcher.matchesFile(outsideExcluded)
    }

    def "matchesFile with exclude pattern (#exclude)"(String exclude) {
        def root = temp.createDir("root")
        def included = root.file("included.test").touch()
        def excluded = root.file("excluded.test").touch()

        expect:
        def matcher = createMatcher([], [exclude], root)
        matcher.matchesFile(included)
        !matcher.matchesFile(excluded)
        where:
        exclude << [ "excluded.test", "*excluded.test", "*excluded.test*", "excluded.*" ]
    }

    def "matchesFile with include pattern (#include)"(String include) {
        def root = temp.createDir("root")
        def included = root.file("included.test").touch()
        def excluded = root.file("excluded.test").touch()

        expect:
        def matcher = createMatcher([include], [], root)
        matcher.matchesFile(included)
        !matcher.matchesFile(excluded)
        where:
        include << [ "*i*.test", "included.test", "*included.test*", "included.test*", "*included.test", "included.*" ]
    }

    def "matchesFile with simple include pattern"() {
        def root = temp.createDir("root")
        def included = root.file("Included").touch()
        def otherIncluded = root.file("other.Included").touch()
        def subIncluded = root.file("sub/Included").touch()

        expect:
        def matcher = createMatcher(["Included"], [], root)
        matcher.matchesFile(included)
        matcher.matchesFile(subIncluded)
        !matcher.matchesFile(otherIncluded)
    }

    def "matchesFile with default packages"() {
        expect:
        // This captures current behavior, but not desired behavior.
        // We cannot select files in the default package without
        // including other files because *.included.test requires included.test
        // to be in a package
        def root = temp.createDir("root")
        def included = root.file("included.test").touch()
        def subIncluded = root.file("sub/included.test").touch()
        def excluded = root.file("sub/someincluded.test").touch()

        def matcher = createMatcher(["*.included.test"], [], root)
        !matcher.matchesFile(included) // This isn't desired
        matcher.matchesFile(subIncluded)
        !matcher.matchesFile(excluded)
        and:
        // If you drop the dot from the pattern, we now incidentally include
        // tests that are in any package and end with the given pattern
        def matcherWithoutDot = createMatcher(["*included.test"], [], root)
        matcherWithoutDot.matchesFile(included)
        matcherWithoutDot.matchesFile(subIncluded)
        matcherWithoutDot.matchesFile(excluded) // This is a side effect of the include
    }

    private FileTestSelectionMatcher createMatcher(Collection<String> includes, Collection<String> excludes, TestFile root) {
        def classTestSelectionMatcher = new ClassTestSelectionMatcher(includes, excludes, [])
        def matcher = new FileTestSelectionMatcher(classTestSelectionMatcher, [root.toPath().toRealPath()])
        matcher
    }
}
