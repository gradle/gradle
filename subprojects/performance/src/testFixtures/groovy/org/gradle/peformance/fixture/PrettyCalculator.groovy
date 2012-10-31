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

package org.gradle.peformance.fixture

import java.math.RoundingMode

/**
 * by Szczepan Faber, created at: 10/30/12
 */
class PrettyCalculator {

    //stolen from the web, TODO SF, replace with commons or something
    static String prettyBytes(long bytes) {
        int unit = 1024;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = "kMGTPE".charAt(exp-1)
        return String.format("%.3f %sB", bytes / Math.pow(unit, exp), pre);
    }

    static String prettyBytes(double bytes) {
        prettyBytes(bytes.round())
    }

    static Number percentChange(double current, double previous) {
        if (previous == 0) {
            return 100
        }
        BigDecimal result = (-1) * (100 * (previous-current) / previous)
        return result.setScale(2, RoundingMode.HALF_UP)
    }
}
