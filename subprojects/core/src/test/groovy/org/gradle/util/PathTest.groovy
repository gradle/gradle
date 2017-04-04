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

class PathTest extends Specification {
    def constructionFromString() {
        expect:
        Path.path(':').getPath() == ':'
        Path.path(':').is(Path.ROOT)
        Path.ROOT.getPath() == ':'
        Path.path('a').getPath() == 'a'
        Path.path('a:b:c').getPath() == 'a:b:c'
        Path.path(':a').getPath() == ':a'
        Path.path(':a:b').getPath() == ':a:b'
        Path.path(':a:b:').getPath() == ':a:b'
    }

    def equalsAndHashCode() {
        expect:
        strictlyEquals(Path.ROOT, Path.ROOT)
        strictlyEquals(Path.path('path'), Path.path('path'))
        strictlyEquals(Path.path(':a:path'), Path.path(':a:path'))
        !strictlyEquals(Path.path(':a'), Path.path(':b'))
        !strictlyEquals(Path.path(':a'), Path.path('a'))
    }

    def canGetParent() {
        expect:
        Path.path(':a:b').parent == Path.path(':a')
        Path.path(':a').parent == Path.path(':')
        Path.path(':').parent == null
        Path.path('a:b').parent == Path.path('a')
        Path.path('a').parent == null
    }

    def canGetName() {
        expect:
        Path.path(':a:b').name == 'b'
        Path.path(':a').name == 'a'
        Path.path(':').name == null
        Path.path('a:b').name == 'b'
        Path.path('a').name == 'a'
    }

    def canCreateChild() {
        expect:
        Path.path(':').child("a") == Path.path(":a")
        Path.path(':a').child("b") == Path.path(":a:b")
        Path.path('a:b').child("c") == Path.path("a:b:c")
    }

    def convertsRelativePathToAbsolutePath() {
        when:
        def path = Path.path(':')

        then:
        path.absolutePath('path') == ':path'

        when:
        path = Path.path(':sub')

        then:
        path.absolutePath('path') == ':sub:path'
    }

    def convertsAbsolutePathToAbsolutePath() {
        def path = Path.path(':')

        expect:
        path.absolutePath(':') == ':'
        path.absolutePath(':path') == ':path'
    }

    def convertsAbsolutePathToRelativePath() {
        when:
        def path = Path.path(':')

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
        def path = Path.path(':')

        expect:
        path.relativePath('path') == 'path'
    }

    def sortsPathsDepthFirstCaseInsensitive() {
        expect:
        paths(['a', 'b', 'A', 'abc']).sort() == paths(['A', 'a', 'abc', 'b'])
        paths([':a', ':b', ':b:a', ':B:a', ':', ':B', ':a:a']).sort() == paths([':', ':a', ':a:a', ':B', ':B:a', ':b', ':b:a'])
        paths(['b', 'b:a', 'a', 'a:a']).sort() == paths(['a', 'a:a', 'b', 'b:a'])
        paths([':', ':a', 'a']).sort() == paths(['a', ':', ':a'])
    }

    def paths(List<String> paths) {
        return paths.collect { Path.path(it) }
    }
}
