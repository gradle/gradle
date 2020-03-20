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

import org.gradle.internal.hash.Hashing
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.RegularFileSnapshot
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

    def "resolves directories to watch from snapshot hierarchy"() {
        def prefix = '/some/absolute'

        when:
        def directoriesToWatch = resolveDirectoriesToWatch(hierarchy, prefix)
        then:
        normalizeLineSeparators(directoriesToWatch) == (expectedDirectoriesToWatch.collect { "${prefix}${it}".toString() } as Set)

        where:
        hierarchy  | expectedDirectoriesToWatch
        [root: []] | ["", "/root"]
        [root: [
            [
                dir: ['file'],
                dir2: ['anotherFile', [subDir: []]],
            ],
            'rootFile'
        ]]         | ["", "/root", "/root/dir", "/root/dir2", "/root/dir2/subDir"]
        [
            root1: [
                [
                    dir: [],
                ],
                'rootFile'
            ],
            root2: [
                [
                    dir: ['file'],
                ]
            ]
        ]          | ["", "/root1", "/root1/dir", "/root2", "/root2/dir"]
    }

    private static Set<String> resolveDirectoriesToWatch(Map roots, String prefix) {
        def dirs = directoriesFromMap(roots, prefix)
        return dirs.inject([] as Set<String>) { acc, snapshot ->
            return acc  + WatchRootUtil.resolveDirectoriesToWatch(snapshot, { true }, [])
        }
    }

    private static List<CompleteDirectorySnapshot> directoriesFromMap(Map entries, String prefix) {
        entries.collect { name, value ->
            String absolutePath = "${prefix}/${name}"
            def children = value.collectMany { child ->
                switch (child) {
                    case String:
                        return [fileSnapshot("${absolutePath}/${child}")]
                    case Map:
                        return directoriesFromMap(child, absolutePath)
                    default:
                        throw new AssertionError("Unexpected child: ${child}")
                }
            }
            new CompleteDirectorySnapshot(absolutePath, name, children, Hashing.md5().hashString(absolutePath))
        }
    }

    private static RegularFileSnapshot fileSnapshot(String absolutePath) {
        new RegularFileSnapshot(absolutePath, absolutePath.substring(absolutePath.lastIndexOf('/') + 1), Hashing.md5().hashString(absolutePath), new FileMetadata(1, 1))
    }

    private static List<String> resolveRecursiveRoots(List<String> directories) {
        WatchRootUtil.resolveRootsToWatch(directories as Set)
            .collect { it.toString() }
            .sort()
    }

    private static Set<String> normalizeLineSeparators(Set<String> paths) {
        return paths*.replace(File.separatorChar, '/' as char) as Set
    }
}
