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
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

import static org.gradle.performance.measure.DataSeries.confidenceInDifference;

public class FormatSupport {
    public static String executionTimestamp() {
        return timestamp(Instant.now());
    }

    public static String timestamp(Instant time) {
        return DateTimeFormatter.ISO_INSTANT.format(time);
    }

    public static String date(Date date) {
        return format(date, "yyyy-MM-dd");
    }

    private static String format(Date date, String format) {
        DateFormat timeStampFormat = new SimpleDateFormat(format);
        timeStampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return timeStampFormat.format(date);
    }

    public static Number getTotalTimeSeconds(MeasuredOperationList baseline, MeasuredOperationList current) {
        return baseline.getTotalTime().getMedian().toUnits(Duration.SECONDS).getValue();
    }

    public static Number getConfidencePercentage(MeasuredOperationList baseline, MeasuredOperationList current) {
        if (baseline.isEmpty() || current.isEmpty()) {
            // This is a workaround for https://github.com/gradle/gradle-private/issues/1690
            return new BigDecimal(0);
        }

        double sign = Math.signum(getDifferencePercentage(baseline, current).doubleValue());
        return new BigDecimal(sign * 100.0 * confidenceInDifference(baseline.getTotalTime(), current.getTotalTime())).setScale(2, RoundingMode.HALF_UP);
    }

    public static Number getDifferencePercentage(MeasuredOperationList baseline, MeasuredOperationList current) {
        if (baseline.isEmpty() || current.isEmpty()) {
            // This is a workaround for https://github.com/gradle/gradle-private/issues/1690
            return new BigDecimal(0);
        }
        return new BigDecimal(100.0 * getDifferenceRatio(baseline.getTotalTime(), current.getTotalTime()).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    public static Number getDifferenceRatio(DataSeries<Duration> baselineVersion, DataSeries<Duration> currentVersion) {
        double base = baselineVersion.getMedian().getValue().doubleValue();
        double current = currentVersion.getMedian().getValue().doubleValue();
        return (current - base) / base;
    }

    public static String getFormattedDifference(DataSeries<Duration> baselineVersion, DataSeries<Duration> currentVersion) {
        Amount<Duration> base = baselineVersion.getMedian();
        Amount<Duration> current = currentVersion.getMedian();
        Amount<Duration> diff = current.minus(base);

        String sign = diff.getValue().doubleValue() > 0 ? "+" : "";

        return String.format("%s%s (%.2f%%)", sign, diff.format(), 100.0 * FormatSupport.getDifferenceRatio(baselineVersion, currentVersion).doubleValue());
    }

    public static String getFormattedConfidence(DataSeries<Duration> baselineVersion, DataSeries<Duration> currentVersion) {
        return String.format("%.1f%%", 100.0 * confidenceInDifference(baselineVersion, currentVersion));
    }
}
