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

package org.gradle.performance.fixture

import spock.lang.Specification

class WaitingReaderTest extends Specification {

    def "can read lines"() {
        def source = new BufferedReader(new StringReader("1\n2"))
        def reader = new WaitingReader(source, 1, 1)
        expect:
        reader.readLine() == "1"
        reader.retriedCount == 0

        reader.readLine() == "2"
        reader.retriedCount == 0

        reader.readLine() == null
        reader.retriedCount > 0
    }
}
