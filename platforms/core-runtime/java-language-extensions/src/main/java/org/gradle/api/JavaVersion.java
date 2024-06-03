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

import org.gradle.api.internal.jvm.JavaVersionParser;

/**
 * An enumeration of Java versions.
 * Before 9: http://www.oracle.com/technetwork/java/javase/versioning-naming-139433.html
 * 9+: http://openjdk.java.net/jeps/223
 */
public enum JavaVersion {
    VERSION_1_1, VERSION_1_2, VERSION_1_3, VERSION_1_4,
    VERSION_1_5, VERSION_1_6, VERSION_1_7, VERSION_1_8,
    VERSION_1_9, VERSION_1_10,
    /**
     * Java 11 major version.
     *
     * @since 4.7
     */
    VERSION_11,

    /**
     * Java 12 major version.
     *
     * @since 5.0
     */
    VERSION_12,

    /**
     * Java 13 major version.
     *
     * @since 6.0
     */
    VERSION_13,

    /**
     * Java 14 major version.
     *
     * @since 6.3
     */
    VERSION_14,

    /**
     * Java 15 major version.
     *
     * @since 6.3
     */
    VERSION_15,

    /**
     * Java 16 major version.
     *
     * @since 6.3
     */
    VERSION_16,

    /**
     * Java 17 major version.
     *
     * @since 6.3
     */
    VERSION_17,

    /**
     * Java 18 major version.
     *
     * @since 7.0
     */
    VERSION_18,

    /**
     * Java 19 major version.
     *
     * @since 7.0
     */
    VERSION_19,

    /**
     * Java 20 major version.
     *
     * @since 7.0
     */
    VERSION_20,

    /**
     * Java 21 major version.
     *
     * @since 7.6
     */
    VERSION_21,

    /**
     * Java 22 major version.
     *
     * @since 7.6
     */
    VERSION_22,

    /**
     * Java 23 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.6
     */
    @Incubating
    VERSION_23,

    /**
     * Java 24 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.6
     */
    @Incubating
    VERSION_24,

    /**
     * Java 25 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 8.4
     */
    @Incubating
    VERSION_25,

    /**
     * Java 26 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 8.7
     */
    @Incubating
    VERSION_26,

    /**
     * Higher version of Java.
     * @since 4.7
     */
    VERSION_HIGHER;
    // Since Java 9, version should be X instead of 1.X
    private static final int FIRST_MAJOR_VERSION_ORDINAL = 9 - 1;
    // Class file versions: 1.1 == 45, 1.2 == 46...
    private static final int CLASS_MAJOR_VERSION_OFFSET = 44;
    private static JavaVersion currentJavaVersion;
    private final String versionName;

    JavaVersion() {
        this.versionName = ordinal() >= FIRST_MAJOR_VERSION_ORDINAL ? getMajorVersion() : "1." + getMajorVersion();
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
        if (value instanceof Integer) {
            return getVersionForMajor((Integer) value);
        }

        String name = value.toString();
        return getVersionForMajor(JavaVersionParser.parseMajorVersion(name));
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

    static void resetCurrent() {
        currentJavaVersion = null;
    }

    public static JavaVersion forClassVersion(int classVersion) {
        return getVersionForMajor(classVersion - CLASS_MAJOR_VERSION_OFFSET);
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

    public boolean isJava8() {
        return this == VERSION_1_8;
    }

    public boolean isJava9() {
        return this == VERSION_1_9;
    }

    public boolean isJava10() {
        return this == VERSION_1_10;
    }

    /**
     * Returns if the version is Java 11.
     *
     * @since 4.7
     */
    public boolean isJava11() {
        return this == VERSION_11;
    }

    /**
     * Returns if the version is Java 12.
     *
     * @since 5.0
     */
    public boolean isJava12() {
        return this == VERSION_12;
    }

    public boolean isJava5Compatible() {
        return isCompatibleWith(VERSION_1_5);
    }

    public boolean isJava6Compatible() {
        return isCompatibleWith(VERSION_1_6);
    }

    public boolean isJava7Compatible() {
        return isCompatibleWith(VERSION_1_7);
    }

    public boolean isJava8Compatible() {
        return isCompatibleWith(VERSION_1_8);
    }

    public boolean isJava9Compatible() {
        return isCompatibleWith(VERSION_1_9);
    }

    public boolean isJava10Compatible() {
        return isCompatibleWith(VERSION_1_10);
    }

    /**
     * Returns if the version is Java 11 compatible.
     *
     * @since 4.7
     */
    public boolean isJava11Compatible() {
        return isCompatibleWith(VERSION_11);
    }

    /**
     * Returns if the version is Java 12 compatible.
     *
     * @since 5.0
     */
    public boolean isJava12Compatible() {
        return isCompatibleWith(VERSION_12);
    }

    /**
     * Returns if this version is compatible with the given version
     *
     * @since 6.0
     */
    public boolean isCompatibleWith(JavaVersion otherVersion) {
        return this.compareTo(otherVersion) >= 0;
    }

    @Override
    public String toString() {
        return versionName;
    }

    /**
     * Returns the major version number as a {@link String}.
     *
     * @see #getMajorVersionNumber() for the integer value
     * @since 1.8
     */
    public String getMajorVersion() {
        return String.valueOf(getMajorVersionNumber());
    }

    /**
     * Returns the major version number.
     *
     * @see #getMajorVersion() for the string value
     * @since 8.9
     */
    @Incubating
    public int getMajorVersionNumber() {
        return ordinal() + 1;
    }

    private static JavaVersion getVersionForMajor(int major) {
        return major >= values().length ? JavaVersion.VERSION_HIGHER : values()[major - 1];
    }
}
