/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testing.jacoco.plugins.rules

final class JacocoViolationRulesLimit {

    private JacocoViolationRulesLimit() {}

    static class Sufficient {
        static final String LINE_METRIC_COVERED_RATIO = JacocoViolationRulesLimit.create('LINE', 'COVEREDRATIO', 0.0, 1.0)
        static final String CLASS_METRIC_MISSED_COUNT = JacocoViolationRulesLimit.create('CLASS', 'MISSEDCOUNT', null, 0)
    }

    static class Insufficient {
        static final String LINE_METRIC_COVERED_RATIO = JacocoViolationRulesLimit.create('LINE', 'COVEREDRATIO', 0.0, 0.5)
        static final String CLASS_METRIC_MISSED_COUNT = JacocoViolationRulesLimit.create('CLASS', 'MISSEDCOUNT', 0.5, null)
        static final String CLASS_METRIC_MISSED_COUNT_MINIMUM_GT_MAXIMUM = JacocoViolationRulesLimit.create('CLASS', 'MISSEDCOUNT', 0.5, 0.1)
    }

    private static String create(String counter, String value, BigDecimal minimum, BigDecimal maximum) {
        StringBuilder limit = new StringBuilder()
        limit <<= 'limit {\n'

        if (counter) {
            limit <<= "    counter = '${counter}'\n"
        }
        if (value) {
            limit <<= "    value = '${value}'\n"
        }
        if (minimum) {
            limit <<= "    minimum = $minimum\n"
        }
        if (maximum) {
            limit <<= "    maximum = $maximum\n"
        }

        limit <<= '}'
        limit.toString()
    }
}
