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
import static org.gradle.util.Path.path

class PathTest extends Specification {
    def constructionFromString() {
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

    def equalsAndHashCode() {
        expect:
        strictlyEquals(Path.ROOT, Path.ROOT)
        strictlyEquals(path('path'), path('path'))
        strictlyEquals(path(':a:path'), path(':a:path'))
        !strictlyEquals(path(':a'), path(':b'))
        !strictlyEquals(path(':a'), path('a'))
    }

    def canGetParent() {
        expect:
        path(':a:b').parent == path(':a')
        path(':a').parent == path(':')
        path(':').parent == null
        path('a:b').parent == path('a')
        path('a').parent == null
    }

    def canGetName() {
        expect:
        path(':a:b').name == 'b'
        path(':a').name == 'a'
        path(':').name == null
        path('a:b').name == 'b'
        path('a').name == 'a'
    }

    def canCreateChild() {
        expect:
        path(':').child("a") == path(":a")
        path(':a').child("b") == path(":a:b")
        path('a:b').child("c") == path("a:b:c")
    }

    def convertsRelativePathToAbsolutePath() {
        when:
        def path = path(':')

        then:
        path.absolutePath('path') == ':path'

        when:
        path = Path.path(':sub')

        then:
        path.absolutePath('path') == ':sub:path'
    }

    def convertsAbsolutePathToAbsolutePath() {
        def path = path(':')

        expect:
        path.absolutePath(':') == ':'
        path.absolutePath(':path') == ':path'
    }

    def convertsAbsolutePathToRelativePath() {
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

    def convertsRelativePathToRelativePath() {
        def path = path(':')

        expect:
        path.relativePath('path') == 'path'
    }

    def appendsPath() {
        expect:
        path(':a:b').append(path(':c:d')) == path(':a:b:c:d')
        path(':a:b').append(path('c:d')) == path(':a:b:c:d')
        path('a:b').append(path(':c:d')) == path('a:b:c:d')
        path('a:b').append(path('c:d')) == path('a:b:c:d')
    }

    def appendsPathToAbsolutePath() {
        when:
        def path = path(':path')

        then:
        path.append(Path.ROOT).is(path)
        path.append(Path.path(':absolute')) == Path.path(':path:absolute')
        path.append(Path.path(':absolute:subpath')) == Path.path(':path:absolute:subpath')
        path.append(Path.path('relative')) == Path.path(':path:relative')
        path.append(Path.path('relative:subpath')) == Path.path(':path:relative:subpath')
    }

    def appendsPathToRelativePath() {
        when:
        def path = path('path')

        then:
        path.append(Path.ROOT).is(path)
        path.append(Path.path(':absolute')) == Path.path('path:absolute')
        path.append(Path.path(':absolute:subpath')) == Path.path('path:absolute:subpath')
        path.append(Path.path('relative')) == Path.path('path:relative')
        path.append(Path.path('relative:subpath')) == Path.path('path:relative:subpath')
    }

    def sortsPathsDepthFirstCaseInsensitive() {
        expect:
        paths(['a', 'b', 'A', 'abc']).sort() == paths(['A', 'a', 'abc', 'b'])
        paths([':a', ':b', ':b:a', ':B:a', ':', ':B', ':a:a']).sort() == paths([':', ':a', ':a:a', ':B', ':B:a', ':b', ':b:a'])
        paths(['b', 'b:a', 'a', 'a:a']).sort() == paths(['a', 'a:a', 'b', 'b:a'])
        paths([':', ':a', 'a']).sort() == paths(['a', ':', ':a'])
    }

    def paths(List<String> paths) {
        return paths.collect { path(it) }
    }
}
