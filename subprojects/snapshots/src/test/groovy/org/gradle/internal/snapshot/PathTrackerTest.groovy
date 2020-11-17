/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.snapshot

import org.apache.commons.io.FilenameUtils
import spock.lang.Specification

class PathTrackerTest extends Specification {
    def tracker = new PathTracker()

    def "can handle empty"() {
        expect:
        tracker.root
        tracker.segments.empty
        tracker.toRelativePath() == ""
        tracker.toAbsolutePath() == null
    }

    def "can enter and leave root level"() {
        when:
        tracker.enter("root")
        then:
        tracker.root
        tracker.segments.empty
        tracker.toRelativePath() == ""
        tracker.toAbsolutePath() == "root"

        when:
        def name = tracker.leave()
        then:
        name == "root"
        tracker.root
        tracker.segments.empty
        tracker.toRelativePath() == ""
    }

    def "can enter and leave first level"() {
        given:
        tracker.enter("root")
        when:
        tracker.enter("first")
        then:
        !tracker.root
        tracker.segments as List == ["first"]
        tracker.toRelativePath() == "first"
        tracker.toAbsolutePath() == FilenameUtils.separatorsToSystem("root/first")

        when:
        def name = tracker.leave()
        then:
        name == "first"
        tracker.root
        tracker.segments.empty
        tracker.toRelativePath() == ""
        tracker.toAbsolutePath() == "root"
    }

    def "can enter and leave second level"() {
        given:
        tracker.enter("root")
        tracker.enter("first")
        when:
        tracker.enter("second")
        then:
        !tracker.root
        tracker.segments as List == ["first", "second"]
        tracker.toRelativePath() == "first/second"
        tracker.toAbsolutePath() == FilenameUtils.separatorsToSystem("root/first/second")

        when:
        def name = tracker.leave()
        then:
        name == "second"
        !tracker.root
        tracker.segments as List == ["first"]
        tracker.toRelativePath() == "first"
        tracker.toAbsolutePath() == FilenameUtils.separatorsToSystem("root/first")
    }
}
