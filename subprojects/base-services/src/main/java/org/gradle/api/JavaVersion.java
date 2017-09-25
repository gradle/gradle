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
package org.gradle.api;

import com.google.common.annotations.VisibleForTesting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An enumeration of Java versions.
 */
public enum JavaVersion {
    VERSION_1_1(false), VERSION_1_2(false), VERSION_1_3(false), VERSION_1_4(false),
    // starting from here versions are 1_ but their official name is "Java 6", "Java 7", ...
    VERSION_1_5(true), VERSION_1_6(true), VERSION_1_7(true), VERSION_1_8(true), VERSION_1_9(true), VERSION_1_10(true);
    private static JavaVersion currentJavaVersion;
    private final boolean hasMajorVersion;
    private final String versionName;
    private final String majorVersion;

    JavaVersion(boolean hasMajorVersion) {
        this.hasMajorVersion = hasMajorVersion;
        this.versionName = name().substring("VERSION_".length()).replace('_', '.');
        this.majorVersion = name().substring(10);
    }

    /**
     * Converts the given object into a {@code JavaVersion}.
     *
     * @param value An object whose toString() value is to be converted. May be null.
     * @return The version, or null if the provided value is null.
     * @throws IllegalArgumentException when the provided value cannot be converted.
     */
    public static JavaVersion toVersion(Object value) throws IllegalArgumentException {
        if (value == null) {
            return null;
        }
        if (value instanceof JavaVersion) {
            return (JavaVersion) value;
        }

        String name = value.toString();
        Matcher matcher = Pattern.compile("(\\d{1,2})(\\D.+)?").matcher(name);
        if (matcher.matches()) {
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index > 0 && index < values().length && values()[index].hasMajorVersion) {
                return values()[index];
            }
        }

        matcher = Pattern.compile("1\\.(\\d{1,2})(\\D.+)?").matcher(name);
        if (matcher.matches()) {
            int versionIdx = Integer.parseInt(matcher.group(1)) - 1;
            if (versionIdx >= 0 && versionIdx < values().length) {
                return values()[versionIdx];
            }
        }
        throw new IllegalArgumentException(String.format("Could not determine java version from '%s'.", name));
    }

    /**
     * Returns the version of the current JVM.
     *
     * @return The version of the current JVM.
     */
    public static JavaVersion current() {
        if (currentJavaVersion == null) {
            currentJavaVersion = toVersion(System.getProperty("java.version"));
        }
        return currentJavaVersion;
    }

    @VisibleForTesting
    static void resetCurrent() {
        currentJavaVersion = null;
    }

    public static JavaVersion forClassVersion(int classVersion) {
        int index = classVersion - 45; //class file versions: 1.1 == 45, 1.2 == 46...
        if (index >= 0 && index < values().length) {
            return values()[index];
        }
        throw new IllegalArgumentException(String.format("Could not determine java version from '%d'.", classVersion));
    }

    public static JavaVersion forClass(byte[] classData) {
        if (classData.length < 8) {
            throw new IllegalArgumentException("Invalid class format. Should contain at least 8 bytes");
        }
        return forClassVersion(classData[7] & 0xFF);
    }

    public boolean isJava5() {
        return this == VERSION_1_5;
    }

    public boolean isJava6() {
        return this == VERSION_1_6;
    }

    public boolean isJava7() {
        return this == VERSION_1_7;
    }

    private boolean isJava8() {
        return this == VERSION_1_8;
    }

    private boolean isJava9() {
        return this == VERSION_1_9;
    }

    private boolean isJava10() {
        return this == VERSION_1_10;
    }

    public boolean isJava5Compatible() {
        return this.compareTo(VERSION_1_5) >= 0;
    }

    public boolean isJava6Compatible() {
        return this.compareTo(VERSION_1_6) >= 0;
    }

    public boolean isJava7Compatible() {
        return this.compareTo(VERSION_1_7) >= 0;
    }

    public boolean isJava8Compatible() {
        return this.compareTo(VERSION_1_8) >= 0;
    }

    public boolean isJava9Compatible() {
        return this.compareTo(VERSION_1_9) >= 0;
    }

    @Incubating
    public boolean isJava10Compatible() {
        return this.compareTo(VERSION_1_10) >= 0;
    }

    @Override
    public String toString() {
        return getName();
    }

    private String getName() {
        return versionName;
    }

    public String getMajorVersion() {
        return majorVersion;
    }
}
