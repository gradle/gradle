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

package org.gradle.internal.watch.vfs.impl

import org.gradle.internal.file.FileHierarchySet
import spock.lang.Specification

class FileWatchingFilterTest extends Specification {
    def globalCache = new File("global-cache").absoluteFile
    def filter = new FileWatchingFilter(FileHierarchySet.empty().plus(globalCache))

    def "a current-build location is immutable alongside the global immutable locations"() {
        def cacheDir = new File("project/.gradle").absoluteFile

        expect:
        !filter.isImmutableLocation(cacheDir.absolutePath)

        when:
        filter.addCurrentBuildImmutableLocation(cacheDir)

        then:
        filter.isImmutableLocation(cacheDir.absolutePath)
        filter.isImmutableLocation(globalCache.absolutePath)
    }

    def "current-build immutable locations are forgotten on build finish, global ones are kept"() {
        def cacheDir = new File("project/.gradle").absoluteFile
        filter.addCurrentBuildImmutableLocation(cacheDir)

        when:
        filter.buildFinished()

        then:
        !filter.isImmutableLocation(cacheDir.absolutePath)
        filter.isImmutableLocation(globalCache.absolutePath)
    }
}
