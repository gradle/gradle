/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.watch.registry.impl

import com.google.common.collect.ImmutableSet
import org.gradle.internal.file.FileHierarchySet
import org.gradle.internal.snapshot.TestSnapshotFixture
import spock.lang.Specification

import static org.gradle.internal.watch.registry.impl.AbstractFileWatcherUpdater.resolveWatchedHierarchies

class WatchedHierarchiesTest extends Specification implements TestSnapshotFixture {
    def "does not watch when there's nothing to watch"() {
        def watchable = Mock(WatchableHierarchies)
        def probedRoots = ImmutableSet.<File> builder()
        when:
        def watched = resolveWatchedHierarchies(watchable, buildHierarchy([
        ]), probedRoots)
        then:
        probedRoots.build() == [] as Set
        rootsOf(watched) == []
        1 * watchable.recentlyUsedHierarchies >> []
    }

    def "watches empty directory"() {
        def watchable = Mock(WatchableHierarchies)
        def probedRoots = ImmutableSet.<File> builder()
        def dir = new File("empty").absoluteFile

        when:
        def watched = resolveWatchedHierarchies(watchable, buildHierarchy([
            directory(dir.absolutePath, [])
        ]), probedRoots)
        then:
        probedRoots.build() == [dir] as Set
        rootsOf(watched) == [dir.absolutePath]
        1 * watchable.recentlyUsedHierarchies >> [dir]
    }

    def "does not watch directory with only missing files inside"() {
        def watchable = Mock(WatchableHierarchies)
        def probedRoots = ImmutableSet.<File> builder()
        def dir = new File("empty").absoluteFile

        when:
        def watched = resolveWatchedHierarchies(watchable, buildHierarchy([
            missing(dir.absolutePath + "/missing.txt")
        ]), probedRoots)
        then:
        probedRoots.build() == [] as Set
        rootsOf(watched) == []
        1 * watchable.recentlyUsedHierarchies >> [dir]
    }

    def "watches only outermost hierarchy, but probes each nested hierarchy"() {
        def watchable = Mock(WatchableHierarchies)
        def probedRoots = ImmutableSet.<File> builder()
        def parent = new File("parent").absoluteFile
        def child = new File(parent, "child").absoluteFile
        def grandchild = new File(child, "grandchild").absoluteFile

        when:
        def watched = resolveWatchedHierarchies(watchable, buildHierarchy([
            regularFile(new File(grandchild, "missing.txt").absolutePath)
        ]), probedRoots)
        then:
        probedRoots.build() == [parent, child, grandchild] as Set
        rootsOf(watched) == [parent.absolutePath]
        1 * watchable.recentlyUsedHierarchies >> [parent, child, grandchild]
    }

    private static List<String> rootsOf(FileHierarchySet set) {
        def roots = []
        set.visitRoots((root -> roots.add(root)))
        return roots
    }
}
