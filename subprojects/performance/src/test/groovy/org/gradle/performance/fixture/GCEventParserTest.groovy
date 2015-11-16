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

import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import spock.lang.Specification
import spock.lang.Subject

class GCEventParserTest extends Specification {

    @Subject parser = new GCEventParser((char) '.')

    def "parses event"() {
        when:
        def e = parser.parseLine "2015-01-22T16:04:50.319+0000: [Full GC (System) [PSYoungGen: 2048K->0K(114688K)] [PSOldGen: 24097K->26017K(262080K)] 26145K->26017K(376768K) [PSPermGen: 41509K->41509K(77696K)], 0.1944213 secs] [Times: user=0.20 sys=0.00, real=0.19 secs] "

        then:
        e.timestamp == new DateTime(2015, 1, 22, 16, 4, 50, 319, DateTimeZone.default) // timezone information is discarded
        e.start == 26145
        e.committed == 376768
        e.end == 26017

        when:
        e = parser.parseLine "2015-02-12T07:14:50.459-1000: [GC [DefNew: 2560K->319K(2880K), 0.0034420 secs] 2560K->588K(9408K), 0.0034820 secs] [Times: user=0.00 sys=0.00, real=0.01 secs] "

        then:
        e.timestamp == new DateTime(2015, 2, 12, 7, 14, 50, 459, DateTimeZone.default)
        e.start == 2560
        e.committed == 9408
        e.end == 588
    }

    def "reports unrecognized events"() {
        when:
        parser.parseLine "foo bar"

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains "foo bar"
    }

    def "ignores events that seem to happen on windows"() {
        when:
        def e = parser.parseLine " [Times: user=0.20 sys=0.00, real=0.19 secs] "

        then:
        e == GCEventParser.GCEvent.IGNORED
    }

    def "parses young generation allocation failures"() {
        when:
        def e = parser.parseLine '2015-11-16T15:20:08.939+0100: [GC (Allocation Failure) --[PSYoungGen: 510285K->510285K(521728K)] 1791411K->1908557K(1920000K), 0,1576176 secs] [Times: user=0,45 sys=0,03, real=0,15 secs]'

        then:
        e.timestamp == new DateTime(2015, 11, 16, 15, 20, 8, 939, DateTimeZone.default)
        e.start == 1791411
        e.committed == 1920000
        e.end == 1908557
    }
}
