/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.report

import spock.lang.Specification

class TestResultModelTest extends Specification {
    def formatsShortDurations() {
        expect:
        test(0).formattedDuration == '0s'
        test(7).formattedDuration == '0.007s'
        test(1200).formattedDuration == '1.200s'
    }

    def formatsLongDuration() {
        expect:
        test(60000).formattedDuration == '1m0.00s'
        test(72301).formattedDuration == '1m12.30s'
        test(72305).formattedDuration == '1m12.31s'
        test(60 * 60 * 1000).formattedDuration == '1h0m0.00s'
        test(24 * 60 * 60 * 1000).formattedDuration == '1d0h0m0.00s'
    }

    def test(long duration) {
        return new TestResult('test', duration, null)
    }
}
