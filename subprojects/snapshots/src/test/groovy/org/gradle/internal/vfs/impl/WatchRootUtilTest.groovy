/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl

import org.gradle.internal.vfs.watch.WatchRootUtil
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class WatchRootUtilTest extends Specification {
    @Requires(TestPrecondition.UNIX_DERIVATIVE)
    def "resolves recursive UNIX roots #directories to #resolvedRoots"() {
        expect:
        resolveRecursiveRoots(directories) == resolvedRoots

        where:
        directories        | resolvedRoots
        []                 | []
        ["/a"]             | ["/a"]
        ["/a", "/b"]       | ["/a", "/b"]
        ["/a", "/a/b"]     | ["/a"]
        ["/a/b", "/a"]     | ["/a"]
        ["/a", "/a/b/c/d"] | ["/a"]
        ["/a/b/c/d", "/a"] | ["/a"]
        ["/a", "/b/a"]     | ["/a", "/b/a"]
        ["/b/a", "/a"]     | ["/a", "/b/a"]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "resolves recursive Windows roots #directories to #resolvedRoots"() {
        expect:
        resolveRecursiveRoots(directories) == resolvedRoots

        where:
        directories                 | resolvedRoots
        []                          | []
        ["C:\\a"]                   | ["C:\\a"]
        ["C:\\a", "C:\\b"]          | ["C:\\a", "C:\\b"]
        ["C:\\a", "C:\\a\\b"]       | ["C:\\a"]
        ["C:\\a\\b", "C:\\a"]       | ["C:\\a"]
        ["C:\\a", "C:\\a\\b\\c\\d"] | ["C:\\a"]
        ["C:\\a\\b\\c\\d", "C:\\a"] | ["C:\\a"]
        ["C:\\a", "C:\\b\\a"]       | ["C:\\a", "C:\\b\\a"]
        ["C:\\b\\a", "C:\\a"]       | ["C:\\a", "C:\\b\\a"]
    }

    private static List<String> resolveRecursiveRoots(List<String> directories) {
        WatchRootUtil.resolveRootsToWatch(directories as Set)
            .collect { it.toString() }
            .sort()
    }
}
