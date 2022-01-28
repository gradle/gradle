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

package org.gradle.internal.snapshot

import spock.lang.Specification

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE

class VfsRelativePathTest extends Specification {

    def "convert absolute path '#absolutePath' to relative path '#relativePath'"() {
        expect:
        VfsRelativePath.of(absolutePath).asString == relativePath

        where:
        absolutePath           | relativePath
        'C:\\'                 | 'C:'
        'C:\\Users\\user'      | 'C:\\Users\\user'
        '/'                    | ''
        '/var/log/messages'    | 'var/log/messages'
        '//uncMount/some/path' | 'uncMount/some/path'
        '/a'                   | 'a'
        '/a/b/c'               | 'a/b/c'
        '/a/b/c/'              | 'a/b/c'
        ''                     | ''
    }

    def "'#relativePath' fromChild '#child' is '#result'"() {
        expect:
        VfsRelativePath.of(relativePath).pathFromChild(child).asString == result
        VfsRelativePath.of(child).pathToChild(relativePath) == result

        where:
        relativePath | child | result
        "a/b"        | "a"   | "b"
        "a/b"        | ""    | "a/b"
        ""           | ""    | ""
    }

    def "'#relativePath / #offset' #verb a prefix of '#childPath'"() {
        expect:
        VfsRelativePath.of(relativePath, offset).isPrefixOf(childPath, CASE_SENSITIVE) == result

        where:
        relativePath | offset         | childPath | result
        "a/b"        | "a/".length()  | "a"       | false
        "a/b"        | "a/b".length() | "c"       | true
        "a/b"        | "a/".length()  | "b"       | true
        "b"          | 0              | "b/c/d"   | true
        verb = result ? "is" : "is not"
    }

    def "'#relativePath / #offset' #verb '#prefix' as a prefix"() {
        expect:
        VfsRelativePath.of(relativePath, offset).hasPrefix(prefix, CASE_SENSITIVE) == result

        where:
        relativePath | offset        | prefix | result
        "a/b"        | "a/".length() | "a"    | false
        "a/b"        | "a/".length() | ""     | true
        "a/b"        | "a/".length() | "b"    | true
        "a/b/c/d"    | "a/".length() | "b"    | true
        verb = result ? "has" : "has not"
    }
}
