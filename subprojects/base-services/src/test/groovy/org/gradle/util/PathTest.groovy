/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.util

import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals
import static org.gradle.util.Path.ROOT
import static org.gradle.util.Path.path

class PathTest extends Specification {
    def "construction from string"() {
        expect:
        path(':').getPath() == ':'
        path(':').is(Path.ROOT)
        Path.ROOT.getPath() == ':'
        path('a').getPath() == 'a'
        path('a:b:c').getPath() == 'a:b:c'
        path(':a').getPath() == ':a'
        path(':a:b').getPath() == ':a:b'
        path(':a:b:').getPath() == ':a:b'
    }

    def "equals and hashCode"() {
        expect:
        strictlyEquals(Path.ROOT, Path.ROOT)
        strictlyEquals(path('path'), path('path'))
        strictlyEquals(path(':a:path'), path(':a:path'))
        !strictlyEquals(path(':a'), path(':b'))
        !strictlyEquals(path(':a'), path('a'))
    }

    def "can get parent"() {
        expect:
        path(':a:b').parent == path(':a')
        path(':a').parent == path(':')
        path(':').parent == null
        path('a:b').parent == path('a')
        path('a').parent == null
    }

    def "can get name"() {
        expect:
        path(':a:b').name == 'b'
        path(':a').name == 'a'
        path(':').name == null
        path('a:b').name == 'b'
        path('a').name == 'a'
    }

    def "can create child"() {
        expect:
        path(':').child("a") == path(":a")
        path(':a').child("b") == path(":a:b")
        path('a:b').child("c") == path("a:b:c")
    }

    def "converts relative path to absolute path"() {
        when:
        def path = path(':')

        then:
        path.absolutePath('path') == ':path'

        when:
        path = Path.path(':sub')

        then:
        path.absolutePath('path') == ':sub:path'
    }

    def "converts absolute path to absolute path"() {
        def path = path(':')

        expect:
        path.absolutePath(':') == ':'
        path.absolutePath(':path') == ':path'
    }

    def "converts absolute path to relative path"() {
        when:
        def path = path(':')

        then:
        path.relativePath(':') == ':'
        path.relativePath(':path') == 'path'

        when:
        path = Path.path(':sub')

        then:
        path.relativePath(':') == ':'
        path.relativePath(':sub') == ':sub'
        path.relativePath(':sub:path') == 'path'
        path.relativePath(':sub2:path') == ':sub2:path'
        path.relativePath(':other:path') == ':other:path'
    }

    def 'converts relative path to relative path'() {
        def path = path(':')

        expect:
        path.relativePath('path') == 'path'
    }

    def "appends path"() {
        expect:
        path(':a:b').append(path(':c:d')) == path(':a:b:c:d')
        path(':a:b').append(path('c:d')) == path(':a:b:c:d')
        path('a:b').append(path(':c:d')) == path('a:b:c:d')
        path('a:b').append(path('c:d')) == path('a:b:c:d')
    }

    def "appends path to absolute path"() {
        when:
        def path = path(':path')

        then:
        path.append(Path.ROOT).is(path)
        path.append(Path.path(':absolute')) == Path.path(':path:absolute')
        path.append(Path.path(':absolute:subpath')) == Path.path(':path:absolute:subpath')
        path.append(Path.path('relative')) == Path.path(':path:relative')
        path.append(Path.path('relative:subpath')) == Path.path(':path:relative:subpath')
    }

    def "appends path to relative path"() {
        when:
        def path = path('path')

        then:
        path.append(Path.ROOT).is(path)
        path.append(Path.path(':absolute')) == Path.path('path:absolute')
        path.append(Path.path(':absolute:subpath')) == Path.path('path:absolute:subpath')
        path.append(Path.path('relative')) == Path.path('path:relative')
        path.append(Path.path('relative:subpath')) == Path.path('path:relative:subpath')
    }

    def "sorts paths depth-first case-insensitive"() {
        expect:
        paths(['a', 'b', 'A', 'abc']).sort() == paths(['A', 'a', 'abc', 'b'])
        paths([':a', ':b', ':b:a', ':B:a', ':', ':B', ':a:a']).sort() == paths([':', ':a', ':a:a', ':B', ':B:a', ':b', ':b:a'])
        paths(['b', 'b:a', 'a', 'a:a']).sort() == paths(['a', 'a:a', 'b', 'b:a'])
        paths([':', ':a', 'a']).sort() == paths(['a', ':', ':a'])
    }

    def "counts segments"() {
        expect:
        ROOT.segmentCount() == 0
        path(":").segmentCount() == 0
        path("a").segmentCount() == 1
        path(":a").segmentCount() == 1
        path(":a:b").segmentCount() == 2
        path("a:b").segmentCount() == 2
        path(":a:b:c").segmentCount() == 3
        path("a:b:c").segmentCount() == 3
    }

    def "can remove all segments from absolute path"() {
        expect:
        Path.ROOT === Path.path(path).with { it.removeFirstSegments(it.segmentCount()) }

        where:
        path << [':', ':a', ':a:b']
    }

    def "removes invalid segments"() {
        when:
        path(path).removeFirstSegments(segmentCount)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot remove $segmentCount segments from path $path"

        where:
        path   | segmentCount
        'a:b'  | -1
        'a:b'  | 2
        ':a:b' | -1
        ':a:b' | 3
    }

    def "removes zero segments"() {
        expect:
        ROOT.removeFirstSegments(0) == ROOT
        path(":").removeFirstSegments(0) == path(":")
        path("a").removeFirstSegments(0) == path("a")
        path(":a").removeFirstSegments(0) == path(":a")
        path(":a:b").removeFirstSegments(0) == path(":a:b")
        path("a:b").removeFirstSegments(0) == path("a:b")
        path(":a:b:c").removeFirstSegments(0) == path(":a:b:c")
        path("a:b:c").removeFirstSegments(0) == path("a:b:c")
    }

    def "removes one segment"() {
        expect:
        path(":a:b").removeFirstSegments(1) == path(":b")
        path("a:b").removeFirstSegments(1) == path("b")
        path(":a:b:c").removeFirstSegments(1) == path(":b:c")
        path("a:b:c").removeFirstSegments(1) == path("b:c")
    }

    def "removes two segments"() {
        expect:
        path(":a:b:c").removeFirstSegments(2) == path(":c")
        path("a:b:c").removeFirstSegments(2) == path("c")
    }

    def "retrieves segment"() {
        expect:
        path(":a:b:c").segment(0) == "a"
        path(":a:b:c").segment(1) == "b"
        path(":a:b:c").segment(2) == "c"
        path("a:b:c").segment(0) == "a"
        path("a:b:c").segment(1) == "b"
        path("a:b:c").segment(2) == "c"

        when:
        path(":a:b:c").segment(3)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Segment index 3 is invalid for path :a:b:c"

        when:
        path(":a:b:c").segment(-1)

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Segment index -1 is invalid for path :a:b:c"
    }

    def "test if path is absolute"() {
        expect:
        path(':').absolute
        path(':a').absolute
        !path('a').absolute
        path(':a:b').absolute
        !path('a:b').absolute
    }

    def paths(List<String> paths) {
        return paths.collect { path(it) }
    }
}
