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
import spock.lang.Subject

class GCEventParserTest extends Specification {

    @Subject parser = new GCEventParser((char) '.')

    def "parses event"() {
        when:
        def e = parser.parseLine "17.743: [Full GC (System) [PSYoungGen: 2048K->0K(114688K)] [PSOldGen: 24097K->26017K(262080K)] 26145K->26017K(376768K) [PSPermGen: 41509K->41509K(77696K)], 0.1944213 secs] [Times: user=0.20 sys=0.00, real=0.19 secs] "

        then:
        e.start == 26145
        e.committed == 376768
        e.end == 26017
    }

    def "reports unrecognized events"() {
        when:
        parser.parseLine "foo bar"

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains "foo bar"
    }
}
