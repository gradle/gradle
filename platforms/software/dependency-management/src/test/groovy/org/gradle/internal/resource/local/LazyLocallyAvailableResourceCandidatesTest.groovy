/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.local

import org.gradle.internal.Factory
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class LazyLocallyAvailableResourceCandidatesTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    def "does not query factory until necessary"() {
        given:
        def factory = Mock(Factory)

        when:
        def candidates = new LazyLocallyAvailableResourceCandidates(factory, TestUtil.checksumService)

        then:
        0 * factory.create()

        when:
        def isNone = candidates.isNone()

        then:
        !isNone
        1 * factory.create() >> [file("abc"), file("def")]

        when:
        def candidate = candidates.findByHashValue(Hashing.sha1().hashString("def"))

        then:
        candidate.file.name == "def"
        0 * factory.create()
    }

    File file(path) {
        tmp.createFile(path) << path
    }
}
