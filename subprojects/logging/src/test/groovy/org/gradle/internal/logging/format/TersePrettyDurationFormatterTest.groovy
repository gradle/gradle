/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.logging.format

import spock.lang.Specification
import spock.lang.Unroll

class TersePrettyDurationFormatterTest extends Specification {
    def formatter = new TersePrettyDurationFormatter()

    @Unroll
    def "shows #output when elapsed time is greater or equals than #lowerBoundInclusive but lower than #upperBoundExlusive"() {
        when:
        def result = formatter.format(input);

        then:
        result == output

        where:
        lowerBoundInclusive | upperBoundExclusive | input            | output
        "None"              | "10 seconds"        | seconds(4.21345) | "4s"
        "10 seconds"        | "1 minute"          | seconds(42.1234) | "42s"
        "1 minute"          | "10 minutes"        | minutes(4.21234) | "4m 12s"
        "10 minutes"        | "1 hour"            | minutes(42.1234) | "42m 7s"
        "1 hour"            | "10 hours"          | hours(4.2123456) | "4h 12m 44s"
        "10 hours"          | "100 hours"         | hours(42.123456) | "42h 7m 24s"
        "100 hours"         | "None"              | hours(421.23456) | "421h 14m 4s"
    }

    private static long hours(double value) {
        return minutes(value * 60.0)
    }

    private static long minutes(double value) {
        return seconds(value * 60.0)
    }

    private static long seconds(double value) {
        return value * 1000.0;
    }
}
