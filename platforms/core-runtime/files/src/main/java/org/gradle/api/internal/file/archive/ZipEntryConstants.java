/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class ZipEntryConstants {

    /**
     * Note that setting the January 1st 1980 (or even worse, "0", as time) won't work due
     * to Java 8 doing some interesting time processing: It checks if this date is before January 1st 1980
     * and if it is it starts setting some extra fields in the zip. Java 7 does not do that - but in the
     * zip not the milliseconds are saved but values for each of the date fields - but no time zone. And
     * 1980 is the first year which can be saved.
     * If you use January 1st 1980 then it is treated as a special flag in Java 8.
     * Moreover, only even seconds can be stored in the zip file. Java 8 uses the upper half of
     * some other long to store the remaining millis while Java 7 doesn't do that. So make sure
     * that your seconds are even.
     * Moreover, parsing happens via `new Date(millis)` in {@code java.util.zip.ZipUtils#javaToDosTime()} so we
     * must use default timezone and locale.
     *
     * The date is 1980 February 1st CET.
     */
    public static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

    private ZipEntryConstants() {}

}
