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

package org.gradle.internal.vfs.impl

import spock.lang.Specification

class AbstractNodeTest extends Specification {

    def "can compare size of common prefix"() {
        expect:
        Integer.signum(AbstractNode.compareWithCommonPrefix(prefix, path, offset, '/' as char)) == result
        if (result) {
            assert Integer.signum(prefix <=> path.substring(offset)) == result
        }

        where:
        prefix              | path                    | offset | result
        "hello/world"       | "hello/other"           | 0      | 0
        "hello/world"       | "/var/hello/other"      | 5      | 0
        "hello/world"       | "/var/hello/world"      | 5      | 0
        "hello/world"       | "/var/hello/world/next" | 5      | 0
        "hello/world"       | "/var/hello1/other"     | 5      | -1
        "hello1/world"      | "/var/hello/other"      | 5      | 1
        "hello/world/some"  | "/var/hello/other"      | 5      | 0
        "bbc/some"          | "/var/abc/other"        | 5      | 1
        "/hello/world/some" | "/var/hello/other"      | 0      | -1
        "/hello/world/some" | "/var/hello/other"      | 4      | 0
    }
}
