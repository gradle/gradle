/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import spock.lang.Specification

class VersionParserTest extends Specification {
    def versionParser = new VersionParser()

    def "splits version on punctuation"() {
        expect:
        def version = versionParser.transform(versionStr)
        version.parts as List == parts

        where:
        versionStr  | parts
        'a.b.c'     | ['a', 'b', 'c']
        'a-b-c'     | ['a', 'b', 'c']
        'a_b_c'     | ['a', 'b', 'c']
        'a+b+c'     | ['a', 'b', 'c']
        'a.b-c+d_e' | ['a', 'b', 'c', 'd', 'e']
    }

    def "splits on adjacent digits and alpha"() {
        expect:
        def version = versionParser.transform(versionStr)
        version.parts as List == parts

        where:
        versionStr | parts
        'abc123'   | ['abc', '123']
        '1a2b'     | ['1', 'a', '2', 'b']
    }

    def "inconsistently handles empty parts and retains whitespace (existing behaviour not necessarily desirable behaviour)"() {
        expect:
        def version = versionParser.transform(versionStr)
        version.parts as List == parts

        where:
        versionStr  | parts
        ''          | ['']
        'a b c'     | ['a b c']
        '...'       | []
        '-a b c-  ' | ['', 'a b c', '  ']
    }
}
