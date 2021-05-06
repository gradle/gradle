/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal

import spock.lang.Specification
import spock.lang.Unroll

class RelativePathUtilTest extends Specification {

    @Unroll
    def "relative path from #fromPath to #toPath is #path"() {
        when:
        def from = new File(fromPath)
        def to = new File(toPath)

        then:
        RelativePathUtil.relativePath(from, to) == path

        where:
        fromPath | toPath  | path
        "a"      | "a/b"   | "b"
        "a"      | "a/b/a" | "b/a"
        "a"      | "b"     | "../b"
        "a/b"    | "b"     | "../../b"
        "a"      | "a"     | ""
    }
}
