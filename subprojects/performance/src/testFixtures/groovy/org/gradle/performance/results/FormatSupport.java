/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance.results;

import org.gradle.performance.measure.Amount;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.Duration;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class FormatSupport {
    private final DateFormat timeStampFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    public FormatSupport() {
        timeStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public String executionTimestamp() {
        return timeStampFormat.format(new Date());
    }

    public String timestamp(Date date) {
        return timeStampFormat.format(date);
    }

    public String date(Date date) {
        return dateFormat.format(date);
    }

    public String seconds(Amount<Duration> duration) {
        return duration.toUnits(Duration.SECONDS).getValue().toString();
    }

    public String megabytes(Amount<DataAmount> amount) {
        return amount.toUnits(DataAmount.MEGA_BYTES).getValue().toString();
    }
}
