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

package org.gradle.performance.results

import groovy.transform.CompileStatic
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration

import java.math.RoundingMode

@CompileStatic
class PrettyCalculator {

    static String toBytes(Amount<DataAmount> bytes) {
        return bytes.toUnits(DataAmount.BYTES).value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toString() + " B"
    }

    static String toMillis(Amount<Duration> duration) {
        return duration.toUnits(Duration.MILLI_SECONDS).value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toString() + " ms"
    }

    static <Q> Number percentChange(Amount<Q> current, Amount<Q> previous) {
        if (previous == Amount.valueOf(0, previous.getUnits())) {
            return 100 as Integer
        }
        BigDecimal result = (current - previous) / previous * 100
        return result.setScale(2, RoundingMode.HALF_UP)
    }
}
