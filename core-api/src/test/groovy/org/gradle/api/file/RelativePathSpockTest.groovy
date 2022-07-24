/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.file

import spock.lang.Specification

class RelativePathSpockTest extends Specification {
    def "compareTo should compare paths with same number of segments"() {
        given:
        def path1 = new RelativePath(true, "a");
        def path2 = new RelativePath(true, "b");
        expect:
        path1.compareTo(path2) < 0
        path1.compareTo(path1) == 0
        path2.compareTo(path1) > 0
        path2.compareTo(path2) == 0
    }

    def "compareTo should compare paths with multiple number of segments"() {
        given:
        def path1 = new RelativePath(true, "a", "b", "d");
        def path2 = new RelativePath(true, "a", "b", "c");
        expect:
        path1.compareTo(path2) > 0
        path1.compareTo(path1) == 0
        path2.compareTo(path1) < 0
        path2.compareTo(path2) == 0
    }

    def "compareTo should compare paths with different number of segments"() {
        given:
        def path1 = new RelativePath(true, "b");
        def path2 = new RelativePath(true, "a", "b", "c");
        expect:
        path1.compareTo(path2) < 0
        path1.compareTo(path1) == 0
        path2.compareTo(path1) > 0
        path2.compareTo(path2) == 0
    }
}
