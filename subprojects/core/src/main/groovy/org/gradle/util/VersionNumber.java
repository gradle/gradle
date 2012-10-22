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

package org.gradle.util;

import com.google.common.base.Objects;
import com.google.common.collect.Ordering;

import org.gradle.api.Nullable;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents, parses, and compares version numbers following a major.minor.micro-qualifier format.
 */
public class VersionNumber implements Comparable<VersionNumber> {
    public static final VersionNumber UNKNOWN = new VersionNumber(0, 0, 0, null);

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?+(?:\\.(\\d+))?+(?:[-\\.](.+))?");
    private static final String VERSION_TEMPLATE = "%d.%d.%d%s";

    private final int major;
    private final int minor;
    private final int micro;
    private final String qualifier;

    public VersionNumber(int major, int minor, int micro, @Nullable String qualifier) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.qualifier = qualifier;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getMicro() {
        return micro;
    }

    public String getQualifier() {
        return qualifier;
    }

    public int compareTo(VersionNumber other) {
        if (major != other.major) { return major - other.major; }
        if (minor != other.minor) { return minor - other.minor; }
        if (micro != other.micro) { return micro - other.micro; }
        return Ordering.natural().nullsFirst().compare(qualifier, other.qualifier);
    }

    public boolean equals(Object other) {
        return other instanceof VersionNumber && compareTo((VersionNumber)other) == 0;
    }

    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + micro;
        result = 31 * result + Objects.hashCode(qualifier);
        return result;
    }

    public String toString() {
        return String.format(VERSION_TEMPLATE, major, minor, micro, qualifier == null ? "" : "-" + qualifier);
    }

    public static VersionNumber parse(String versionString) {
        if (versionString == null) { return UNKNOWN; }
        Matcher m = VERSION_PATTERN.matcher(versionString);
        if (!m.matches()) { return UNKNOWN; }

        int major = Integer.valueOf(m.group(1));
        String minorString = m.group(2);
        int minor = minorString == null ? 0 : Integer.valueOf(minorString);
        String microString = m.group(3);
        int micro = microString == null ? 0 : Integer.valueOf(microString);
        String qualifier = m.group(4);

        return new VersionNumber(major, minor, micro, qualifier);
    }
}

