/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.profile;

import java.text.DecimalFormat;

public class ElapsedTimeFormatter {
    private static final DecimalFormat SECONDS_FORMAT = new DecimalFormat("0.000");
    private static final DecimalFormat SECONDS_FORMAT2 = new DecimalFormat("00.000");
    private static final DecimalFormat HMFORMAT = new DecimalFormat("0");
    private static final DecimalFormat HMFORMAT2 = new DecimalFormat("00");

    public String format(long elapsed) {
        long hours = elapsed / (1000L * 60 * 60);
        long minutes = (elapsed / (1000L * 60)) % 60;
        float seconds = (float) ((elapsed % 60000L) / 1000.0);
        StringBuffer result = new StringBuffer();

        if (hours > 0) {
            result.append(HMFORMAT.format(hours));
            result.append(":");
        }

        if (minutes > 0) {
            if (hours > 0) {
                result.append(HMFORMAT2.format(minutes));
            } else {
                result.append(HMFORMAT.format(minutes));
            }
            result.append(":");
        }

        if (hours > 0 || minutes > 0) {
            result.append(SECONDS_FORMAT2.format(seconds));
        } else {
            result.append(SECONDS_FORMAT.format(seconds));
        }

        return result.toString();
    }
}
