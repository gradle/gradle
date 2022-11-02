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

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**B
 * An enumeration of Java versions.
 * <a href="https://www.oracle.com/java/technologies/javase/versioning-naming.html">Version numbers before Java 9.</a>
 * <a href="https://openjdk.org/jeps/223">Version numbers after Java 9.</a>
 *
 * <p>
 * This interface is not intended for implementation by build script or plugin authors.
 * </p>
 */
public final class JavaVersion implements Comparable<JavaVersion>, Serializable {

    private static final long serialVersionUID = 1L;

    private static final int LOWER_CACHED_VERSION = 4;
    private static final int HIGHER_CACHED_VERSION = 19;
    private static final JavaVersion[] KNOWN_VERSIONS;

    static {
        KNOWN_VERSIONS = new JavaVersion[HIGHER_CACHED_VERSION - LOWER_CACHED_VERSION + 1];
        for (int version = LOWER_CACHED_VERSION; version <= HIGHER_CACHED_VERSION; version++) {
            KNOWN_VERSIONS[version - LOWER_CACHED_VERSION] = new JavaVersion(version);
        }
    }

    /**
     * Returns the Java version for the given version number.
     *
     * @since 8.0
     */
    @Incubating
    public static JavaVersion of(int version) {
        if (version <= 0) {
            throw new IllegalArgumentException("JavaVersion must be a positive integer, not " + version);
        }
        if (version >= LOWER_CACHED_VERSION && version <= HIGHER_CACHED_VERSION) {
            return KNOWN_VERSIONS[version - LOWER_CACHED_VERSION];
        } else {
            return new JavaVersion(version);
        }
    }

    /**
     * Returns the Java version for the given version number.
     *
     * @since 8.0
     */
    @Incubating
    public static JavaVersion of(String version) {
        return JavaVersion.of(Integer.parseInt(version));
    }

    /**
     * Java 1 major version.
     */
    public static final JavaVersion VERSION_1_1 = of(1);

    /**
     * Java 2 major version.
     */
    public static final JavaVersion VERSION_1_2 = of(2);

    /**
     * Java 3 major version.
     */
    public static final JavaVersion VERSION_1_3 = of(3);

    /**
     * Java 4 major version.
     */
    public static final JavaVersion VERSION_1_4 = of(4);

    /**
     * Java 5 major version.
     */
    public static final JavaVersion VERSION_1_5 = of(5);

    /**
     * Java 6 major version.
     */
    public static final JavaVersion VERSION_1_6 = of(6);

    /**
     * Java 7 major version.
     */
    public static final JavaVersion VERSION_1_7 = of(7);

    /**
     * Java 8 major version.
     */
    public static final JavaVersion VERSION_1_8 = of(8);

    /**
     * Java 9 major version.
     */
    public static final JavaVersion VERSION_1_9 = of(9);

    /**
     * Java 10 major version.
     */
    public static final JavaVersion VERSION_1_10 = of(10);

    /**
     * Java 11 major version.
     *
     * @since 4.7
     */
    public static final JavaVersion VERSION_11 = of(11);

    /**
     * Java 12 major version.
     *
     * @since 5.0
     */
    public static final JavaVersion VERSION_12 = of(12);

    /**
     * Java 13 major version.
     *
     * @since 6.0
     */
    public static final JavaVersion VERSION_13 = of(13);

    /**
     * Java 14 major version.
     *
     * @since 6.3
     */
    public static final JavaVersion VERSION_14 = of(14);

    /**
     * Java 15 major version.
     *
     * @since 6.3
     */
    public static final JavaVersion VERSION_15 = of(15);

    /**
     * Java 16 major version.
     *
     * @since 6.3
     */
    public static final JavaVersion VERSION_16 = of(16);

    /**
     * Java 17 major version.
     *
     * @since 6.3
     */
    public static final JavaVersion VERSION_17 = of(17);

    /**
     * Java 18 major version.
     *
     * @since 7.0
     */
    public static final JavaVersion VERSION_18 = of(18);

    /**
     * Java 19 major version.
     *
     * @since 7.0
     */
    public static final JavaVersion VERSION_19 = of(19);

    /**
     * Java 20 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.0
     */
    @Incubating
    public static final JavaVersion VERSION_20 = of(20);

    /**
     * Java 21 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.6
     */
    @Incubating
    public static final JavaVersion VERSION_21 = of(21);

    /**
     * Java 22 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.6
     */
    @Incubating
    public static final JavaVersion VERSION_22 = of(22);

    /**
     * Java 23 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.6
     */
    @Incubating
    public static final JavaVersion VERSION_23 = of(23);

    /**
     * Java 24 major version.
     * Not officially supported by Gradle. Use at your own risk.
     *
     * @since 7.6
     */
    @Incubating
    public static final JavaVersion VERSION_24 = of(24);

    /**
     * Higher version of Java.
     *
     * @since 4.7
     * @deprecated Due to the new way of handling Java versions, this constant is no longer needed and is equivalent to
     * {@link #of(int) of(25)}.
     */
    @Deprecated
    public static final JavaVersion VERSION_HIGHER = of(25);

    private static final Map<String, JavaVersion> VERSION_BY_NAME;

    static {
        VERSION_BY_NAME = new LinkedHashMap<String, JavaVersion>();
        VERSION_BY_NAME.put("VERSION_1_1", VERSION_1_1);
        VERSION_BY_NAME.put("VERSION_1_2", VERSION_1_2);
        VERSION_BY_NAME.put("VERSION_1_3", VERSION_1_3);
        VERSION_BY_NAME.put("VERSION_1_4", VERSION_1_4);
        VERSION_BY_NAME.put("VERSION_1_5", VERSION_1_5);
        VERSION_BY_NAME.put("VERSION_1_6", VERSION_1_6);
        VERSION_BY_NAME.put("VERSION_1_7", VERSION_1_7);
        VERSION_BY_NAME.put("VERSION_1_8", VERSION_1_8);
        VERSION_BY_NAME.put("VERSION_1_9", VERSION_1_9);
        VERSION_BY_NAME.put("VERSION_1_10", VERSION_1_10);
        VERSION_BY_NAME.put("VERSION_11", VERSION_11);
        VERSION_BY_NAME.put("VERSION_12", VERSION_12);
        VERSION_BY_NAME.put("VERSION_13", VERSION_13);
        VERSION_BY_NAME.put("VERSION_14", VERSION_14);
        VERSION_BY_NAME.put("VERSION_15", VERSION_15);
        VERSION_BY_NAME.put("VERSION_16", VERSION_16);
        VERSION_BY_NAME.put("VERSION_17", VERSION_17);
        VERSION_BY_NAME.put("VERSION_18", VERSION_18);
        VERSION_BY_NAME.put("VERSION_19", VERSION_19);
        VERSION_BY_NAME.put("VERSION_20", VERSION_20);
        VERSION_BY_NAME.put("VERSION_21", VERSION_21);
        VERSION_BY_NAME.put("VERSION_22", VERSION_22);
        VERSION_BY_NAME.put("VERSION_23", VERSION_23);
        VERSION_BY_NAME.put("VERSION_24", VERSION_24);
        VERSION_BY_NAME.put("VERSION_HIGHER", VERSION_HIGHER);
    }

    /**
     * Gets a JavaVersion instance by field name, e.g. {@code "VERSION_1_1"} gives {@link #VERSION_1_1}.
     *
     * @param name the field name
     * @return the JavaVersion instance
     * @deprecated Use {@link #of(int)} or {@link #of(String)} instead.
     */
    @Deprecated
    public static JavaVersion valueOf(String name) {
        JavaVersion version = VERSION_BY_NAME.get(name);
        if (version == null) {
            // Try to parse the version from the name
            if (!name.startsWith("VERSION_")) {
                throw new IllegalArgumentException("Invalid JavaVersion constant: " + name);
            }
            String versionString = name.substring("VERSION_".length());
            version = JavaVersion.of(Integer.parseInt(versionString));
        }
        return version;
    }

    /**
     * Gets the list of known JavaVersion instances.
     *
     * @return the list of known JavaVersion instances
     * @deprecated There is no longer a limitation on what values may be present, so this method is not accurate.
     */
    @Deprecated
    public static JavaVersion[] values() {
        return VERSION_BY_NAME.values().toArray(new JavaVersion[0]);
    }

    // Since Java 9, version should be X instead of 1.X
    private static final int FIRST_MAJOR_VERSION = 9;
    private static JavaVersion currentJavaVersion;
    private final int version;
    private final String versionName;

    JavaVersion(int version) {
        this.version = version;
        this.versionName = version >= FIRST_MAJOR_VERSION ? getMajorVersion() : "1." + getMajorVersion();
    }

    /**
     * Converts the given object into a {@code JavaVersion}.
     *
     * @param value An object whose toString() value is to be converted. May be null.
     * @return The version, or null if the provided value is null.
     * @throws IllegalArgumentException when the provided value cannot be converted.
     */
    @Nullable
    public static JavaVersion toVersion(@Nullable Object value) throws IllegalArgumentException {
        if (value == null) {
            return null;
        }
        if (value instanceof JavaVersion) {
            return (JavaVersion) value;
        }
        if (value instanceof Integer) {
            return of((Integer) value);
        }

        String name = value.toString();

        int firstNonVersionCharIndex = findFirstNonVersionCharIndex(name);

        String[] versionStrings = name.substring(0, firstNonVersionCharIndex).split("\\.");
        List<Integer> versions = convertToNumber(name, versionStrings);

        if (isLegacyVersion(versions)) {
            assertTrue(name, versions.get(1) > 0);
            return of(versions.get(1));
        } else {
            return of(versions.get(0));
        }
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
        return of(classVersion - 44); //class file versions: 1.1 == 45, 1.2 == 46...
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

    /**
     * Returns if this version can compile another version.
     *
     * <p>
     * This differs from being "compatible", which is only concerned with runtime compatibility.
     * All JVMs can run older class files, but not all {@code javac} can compile to older targets, e.g. Java 17
     * cannot compile to Java 6.
     * </p>
     *
     * @since 8.0
     */
    @Incubating
    public boolean canCompile(JavaVersion otherVersion) {
        // Can always compile the same version
        if (equals(otherVersion)) {
            return true;
        }
        // Can never compile a newer version
        if (compareTo(otherVersion) < 0) {
            return false;
        }
        // Java 8 is currently supported on all known versions.
        // Due to faster release cadence, https://openjdk.org/jeps/182 is not followed anymore.
        // So it's not possible to guess/calculate when Java 8 will no longer be supported, etc.
        // Once Java 8 is dropped, there should be a more standard policy since all supported releases will match
        // the new time-based release cadence.
        if (otherVersion.isJava8Compatible()) {
            return true;
        }
        // Java 7 support dropped in JDK 20
        if (version >= 20) {
            return otherVersion.compareTo(VERSION_1_7) > 0;
        }
        // Java 6 support dropped in JDK 12
        if (version >= 12) {
            return otherVersion.compareTo(VERSION_1_6) > 0;
        }
        // Java 5 support dropped in JDK 9
        if (version >= 9) {
            return otherVersion.compareTo(VERSION_1_5) > 0;
        }
        // Everything else works.
        return true;
    }


    /**
     * Return this version as a String, "14" for Java 14.
     * <p>
     * This method will return {@code 1.<version>} when the version is lower than 9.
     * </p>
     *
     * @return the version number
     * @since 6.8
     */
    @Override
    public String toString() {
        return versionName;
    }

    // We have to keep this for a while: https://github.com/gradle/gradle/issues/4856
    private String getName() {
        return versionName;
    }

    /**
     * Returns the field name of this version.
     *
     * @return the field name of this version
     * @since 8.0
     * @deprecated This only exists for backwards-compatibility. Calculate this value separately if you need it.
     */
    @Incubating
    @Deprecated
    public String name() {
        if (version <= 10) {
            return "VERSION_1_" + version;
        }
        return "VERSION_" + version;
    }

    /**
     * Return this version as a number, e.g. 14 for Java 14.
     *
     * @return the version number
     * @see #toString()
     * @since 8.0
     */
    @Incubating
    public int asInt() {
        return version;
    }

    public String getMajorVersion() {
        return String.valueOf(version);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        JavaVersion rhs = (JavaVersion) obj;
        return version == rhs.version;
    }

    @Override
    public int hashCode() {
        return version;
    }

    @Override
    public int compareTo(JavaVersion o) {
        if (version > o.version) {
            return 1;
        } else if (version < o.version) {
            return -1;
        } else {
            return 0;
        }
    }

    private static void assertTrue(String value, boolean condition) {
        if (!condition) {
            throw new IllegalArgumentException("Could not determine java version from '" + value + "'.");
        }
    }

    private static boolean isLegacyVersion(List<Integer> versions) {
        return 1 == versions.get(0) && versions.size() > 1;
    }

    private static List<Integer> convertToNumber(String value, String[] versionStrs) {
        List<Integer> result = new ArrayList<Integer>();
        for (String s : versionStrs) {
            assertTrue(value, !isNumberStartingWithZero(s));
            try {
                result.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                assertTrue(value, false);
            }
        }
        assertTrue(value, !result.isEmpty() && result.get(0) > 0);
        return result;
    }

    private static boolean isNumberStartingWithZero(String number) {
        return number.length() > 1 && number.startsWith("0");
    }

    private static int findFirstNonVersionCharIndex(String s) {
        assertTrue(s, s.length() != 0);

        for (int i = 0; i < s.length(); ++i) {
            if (!isDigitOrPeriod(s.charAt(i))) {
                assertTrue(s, i != 0);
                return i;
            }
        }

        return s.length();
    }

    private static boolean isDigitOrPeriod(char c) {
        return (c >= '0' && c <= '9') || c == '.';
    }
}
