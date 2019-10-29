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
import spock.lang.Unroll

@Unroll
class AbstractFileSystemNodeTest extends Specification {

    def "file name of '#path' is '#name'"() {
        expect:
        AbstractFileSystemNode.getFileName(path) == name

        where:
        path                                                      | name
        "/a/b/c"                                                  | "c"
        "/"                                                       | ""
        "C:${File.separator}some-name"                            | "some-name"
        ""                                                        | ""
        "C:${File.separator}Windows/system${File.separator}win32" | "win32"
    }

    def "common prefix of #prefix and #path at #offset is #result"() {
        expect:
        AbstractFileSystemNode.sizeOfCommonPrefix(prefix, path, offset) == result

        where:
        prefix       | path          | offset | result
        '/root'      | '/'           | 0      | 0
        '/root'      | '/root'       | 0      | 5
        '/root/some' | '/root/other' | 0      | 5
    }

}
