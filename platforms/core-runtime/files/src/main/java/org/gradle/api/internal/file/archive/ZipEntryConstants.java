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

import java.time.Instant;
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
     * The value is 1980 February 1st in the JVM's default (local) time zone, because zip stores entry times in the
     * local time zone (as MS-DOS date/time). Use {@link #MINIMUM_TIME_FOR_ZIP_ENTRIES_UTC} when comparing against a raw (UTC) timestamp instead.
     */
    public static final long CONSTANT_TIME_FOR_ZIP_ENTRIES = new GregorianCalendar(1980, Calendar.FEBRUARY, 1, 0, 0, 0).getTimeInMillis();

    /**
     * The minimum timestamp that can be stored in a zip entry, as a UTC instant.
     * <p>
     * MS-DOS date/time cannot represent dates before 1980, so smaller values are raised to this minimum.
     * This is the UTC-instant form of {@link #CONSTANT_TIME_FOR_ZIP_ENTRIES}: once adjusted to the default
     * time zone for storage it produces the same 1980-02-01 entry date, independent of the build time zone.
     */
    public static final long MINIMUM_TIME_FOR_ZIP_ENTRIES_UTC = Instant.parse("1980-02-01T00:00:00Z").toEpochMilli();

    /**
     * The maximum timestamp that can be stored in a zip entry, as a UTC instant.
     * <p>
     * Commons-compress can store timestamps only up to 128 * 365 days from the Unix epoch (2097-11-30T00:00:00Z)
     * as MS-DOS date/time, and silently adds an NTFS extra field for anything larger. The NTFS field stores an
     * absolute instant, so the archive bytes would then depend on the build time zone; larger values fail instead.
     * The margin to that limit ensures the timestamp stays storable as MS-DOS date/time once adjusted to the
     * default time zone for storage.
     */
    public static final long MAXIMUM_TIME_FOR_ZIP_ENTRIES_UTC = Instant.parse("2097-11-01T00:00:00Z").toEpochMilli();

    private ZipEntryConstants() {}

}
