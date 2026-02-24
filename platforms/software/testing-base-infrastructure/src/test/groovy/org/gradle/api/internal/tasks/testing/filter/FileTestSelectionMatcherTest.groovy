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
        exclude << [ "excluded", "*excluded", "*excluded*", "excluded*" ]
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
        include << [ "*i*", "included", "*included*", "included*", "*included", "includ*" ]
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

    def "matchesFile with subdirectory include pattern"() {
        def root = temp.createDir("root")
        def subFile = root.file("sub/foo.test").touch()
        def subNested = root.file("sub/bar.test").touch()
        def rootFile = root.file("foo.test").touch()
        def otherSubFile = root.file("other/foo.test").touch()

        expect:
        def matcher = createMatcher(["sub.*"], [], root)
        matcher.matchesFile(subFile)
        matcher.matchesFile(subNested)
        !matcher.matchesFile(rootFile)
        !matcher.matchesFile(otherSubFile)
    }

    def "matchesFile with wildcard in middle of path"() {
        def root = temp.createDir("root")
        def subFoo = root.file("sub/foo.test").touch()
        def subBar = root.file("sub/bar.test").touch()
        def otherFoo = root.file("other/foo.test").touch()

        expect:
        def matcherBroad = createMatcher(["*sub*"], [], root)
        matcherBroad.matchesFile(subFoo)
        matcherBroad.matchesFile(subBar)
        !matcherBroad.matchesFile(otherFoo)

        and:
        def matcherSpecific = createMatcher(["sub*foo*"], [], root)
        matcherSpecific.matchesFile(subFoo)
        !matcherSpecific.matchesFile(subBar)
        !matcherSpecific.matchesFile(otherFoo)
    }

    def "matchesFile with multiple roots"() {
        def root1 = temp.createDir("root1")
        def root2 = temp.createDir("root2")
        def file1 = root1.file("foo.test").touch()
        def file2 = root2.file("foo.test").touch()
        def outsideFile = temp.createDir("outside").file("foo.test").touch()

        expect:
        def matcher = createMatcher(["foo"], [], root1, root2)
        matcher.matchesFile(file1)
        matcher.matchesFile(file2)
        !matcher.matchesFile(outsideFile)
    }

    def "matchesFile with combined include and exclude"() {
        def root = temp.createDir("root")
        def fooTest = root.file("sub/foo.test").touch()
        def barTest = root.file("sub/bar.test").touch()
        def otherTest = root.file("other/foo.test").touch()

        expect:
        def matcher = createMatcher(["sub.*"], ["sub.bar"], root)
        matcher.matchesFile(fooTest)
        !matcher.matchesFile(barTest)
        !matcher.matchesFile(otherTest)
    }

    def "matchesFile with deeply nested paths"() {
        def root = temp.createDir("root")
        def deepFile = root.file("a/b/c/d/test.file").touch()
        def shallowFile = root.file("a/test.file").touch()

        expect:
        // a/b/c/d/test.file -> a.b.c.d.test (extension stripped)
        def matcherExact = createMatcher(["a.b.c.d.test"], [], root)
        matcherExact.matchesFile(deepFile)
        !matcherExact.matchesFile(shallowFile)

        and:
        // a/test.file -> a.test (extension stripped)
        def matcherWildcard = createMatcher(["a.*.test"], [], root)
        matcherWildcard.matchesFile(deepFile)
        !matcherWildcard.matchesFile(shallowFile)
    }

    def "matchesFile with dots in directory names"() {
        def root = temp.createDir("root")
        def dottedDir = root.file("my.module/foo.test").touch()

        expect:
        // my.module/foo.test -> my.module/foo -> my.module.foo (extension stripped, then / -> .)
        def matcherExact = createMatcher(["my.module.foo"], [], root)
        matcherExact.matchesFile(dottedDir)

        and:
        def matcherWildcard = createMatcher(["my.module.*"], [], root)
        matcherWildcard.matchesFile(dottedDir)
    }

    def "matchesFile with multiple dots in file names"() {
        def root = temp.createDir("root")
        // foo.bar.test -> foo.bar (extension stripped)
        def multiDotFile = root.file("foo.bar.test").touch()

        expect:
        def matcherExact = createMatcher(["foo.bar"], [], root)
        matcherExact.matchesFile(multiDotFile)

        and:
        def matcherWildSuffix = createMatcher(["*.bar"], [], root)
        matcherWildSuffix.matchesFile(multiDotFile)

        and:
        def matcherWildPrefix = createMatcher(["foo.*"], [], root)
        matcherWildPrefix.matchesFile(multiDotFile)
    }

    def "matchesFile with default packages"() {
        expect:
        // This captures current behavior, but not desired behavior.
        // We cannot select files in the default package without
        // including other files because *.included requires included
        // to be in a package
        def root = temp.createDir("root")
        def included = root.file("included.test").touch()
        def subIncluded = root.file("sub/included.test").touch()
        def excluded = root.file("sub/someincluded.test").touch()

        // included.test -> included, sub/included.test -> sub.included, sub/someincluded.test -> sub.someincluded
        def matcher = createMatcher(["*.included"], [], root)
        !matcher.matchesFile(included) // This isn't desired
        matcher.matchesFile(subIncluded)
        !matcher.matchesFile(excluded)
        and:
        // If you drop the dot from the pattern, we now incidentally include
        // tests that are in any package and end with the given pattern
        def matcherWithoutDot = createMatcher(["*included"], [], root)
        matcherWithoutDot.matchesFile(included)
        matcherWithoutDot.matchesFile(subIncluded)
        matcherWithoutDot.matchesFile(excluded) // This is a side effect of the include
    }

    private FileTestSelectionMatcher createMatcher(Collection<String> includes, Collection<String> excludes, TestFile... roots) {
        def classTestSelectionMatcher = new ClassTestSelectionMatcher(includes, excludes, [])
        def matcher = new FileTestSelectionMatcher(classTestSelectionMatcher, roots.collect { it.toPath().toRealPath() })
        matcher
    }
}
