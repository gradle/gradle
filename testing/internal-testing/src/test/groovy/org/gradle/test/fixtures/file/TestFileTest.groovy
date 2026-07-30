/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.test.fixtures.file

import org.junit.Rule
import spock.lang.Specification

class TestFileTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider(getClass())

    def "a missing file is truthy"() {
        expect:
        temp.testDirectory.file("missing")
    }

    def "fluent methods that leave nothing on disk are usable as conditions"() {
        given:
        TestFile missing = temp.testDirectory.file("missing")
        TestFile dir = temp.testDirectory.createDir("dir")
        dir.file("child.txt") << "content"

        expect:
        missing.assertDoesNotExist()
        dir.assertIsDir().deleteDir()
        dir.assertDoesNotExist()
    }
}
