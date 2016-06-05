/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import spock.lang.Specification


class OverlappingDirectoriesDetectorTest extends Specification {

    def "should detect overlapping file paths in unix"() {
        given:
        OverlappingDirectoriesDetector detector = new OverlappingDirectoriesDetector('/' as char)
        when:
        detector.addPaths(['/a/b/c', '/a/b/c2', '/a/b/c3', '/a/b/c/d'])
        then:
        detector.resolveOverlappingPaths() == ['/a/b/c', '/a/b/c/d'] as Set
    }

    def "should detect overlapping file paths in windows"() {
        given:
        OverlappingDirectoriesDetector detector = new OverlappingDirectoriesDetector('\\' as char)
        when:
        detector.addPaths(['C:\\a\\b\\c', 'C:\\a\\b\\c2', 'C:\\a\\b\\c3', 'C:\\a\\b\\c\\d'])
        then:
        detector.resolveOverlappingPaths() == ['C:\\a\\b\\c', 'C:\\a\\b\\c\\d'] as Set
    }

    def "should detect when same path is used twice"() {
        given:
        OverlappingDirectoriesDetector detector = new OverlappingDirectoriesDetector('/' as char)
        when:
        detector.addPaths(['/a/b/c', '/a/b/c2', '/a/b/c3', '/a/b/c'])
        then:
        detector.resolveOverlappingPaths() == ['/a/b/c'] as Set
    }

}
