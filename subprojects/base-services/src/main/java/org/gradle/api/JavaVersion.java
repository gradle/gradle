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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * An enumeration of Java versions.
 */
public final class JavaVersion implements Comparable<JavaVersion>, Serializable {
    public static final JavaVersion VERSION_1_1 = new JavaVersion(1);
    public static final JavaVersion VERSION_1_2 = new JavaVersion(2);
    public static final JavaVersion VERSION_1_3 = new JavaVersion(3);
    public static final JavaVersion VERSION_1_4 = new JavaVersion(4);
    public static final JavaVersion VERSION_1_5 = new JavaVersion(5);
    public static final JavaVersion VERSION_1_6 = new JavaVersion(6);
    public static final JavaVersion VERSION_1_7 = new JavaVersion(7);
    public static final JavaVersion VERSION_1_8 = new JavaVersion(8);
    public static final JavaVersion VERSION_1_9 = new JavaVersion(9);
    public static final JavaVersion VERSION_1_10 = new JavaVersion(10);

    private static Map<String, JavaVersion> init() {
        Map<String, JavaVersion> map = new HashMap<String, JavaVersion>();
        map.put("VERSION_1_1", VERSION_1_1);
        map.put("VERSION_1_2", VERSION_1_2);
        map.put("VERSION_1_3", VERSION_1_3);
        map.put("VERSION_1_4", VERSION_1_4);
        map.put("VERSION_1_5", VERSION_1_5);
        map.put("VERSION_1_6", VERSION_1_6);
        map.put("VERSION_1_7", VERSION_1_7);
        map.put("VERSION_1_8", VERSION_1_8);
        map.put("VERSION_1_9", VERSION_1_9);
        map.put("VERSION_1_10", VERSION_1_10);
        return Collections.unmodifiableMap(map);
    }

    private static final Map<String, JavaVersion> MAJOR_NAME_TO_VERSIONS = init();

    private static JavaVersion currentJavaVersion;
    private final List<Integer> versions;
    private final String legacyVersionName;

    private JavaVersion(int majorVersion) {
        this(singletonList(majorVersion));
    }

    private JavaVersion(List<Integer> versions) {
        this.versions = Collections.unmodifiableList(versions);
        this.legacyVersionName = "1." + versions.get(0);
    }

    public static JavaVersion valueOf(String value) {
        if (value == null) {
            throw new NullPointerException();
        }
        JavaVersion result = MAJOR_NAME_TO_VERSIONS.get(value);
        if (result == null) {
            throw new IllegalArgumentException();
        }
        return result;
    }

    public static JavaVersion[] values() {
        return new JavaVersion[]{
            VERSION_1_1, VERSION_1_2, VERSION_1_3, VERSION_1_4, VERSION_1_5,
            VERSION_1_6, VERSION_1_7, VERSION_1_8, VERSION_1_9, VERSION_1_10
        };
    }


    public String name() {
        return "VERSION_1_" + getMajorVersionNumber();
    }

    public int ordinal() {
        return getMajorVersionNumber() - 1;
    }

    public final Class<JavaVersion> getDeclaringClass() {
        return JavaVersion.class;
    }

    public static JavaVersion toVersion(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JavaVersion) {
            return (JavaVersion) value;
        }

        String str = value.toString();

        int firstNonVersionCharIndex = findFirstNonVersionCharIndex(str);
        assertTrue(str, firstNonVersionCharIndex != 0);

        String[] versionStrings = str.substring(0, firstNonVersionCharIndex).split("\\.");
        List<Integer> versions = convertToNumber(str, versionStrings);

        if (isLegacyVersion(versions)) {
            assertTrue(str, versions.get(1) > 0);
            return new JavaVersion(new ArrayList<Integer>(versions.subList(1, versions.size())));
        } else {
            return new JavaVersion(versions);
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
        for (int i = 0; i < s.length(); ++i) {
            if (!isDigitOrPeriod(s.charAt(i))) {
                return i;
            }
        }

        return s.length();
    }

    private static boolean isDigitOrPeriod(char c) {
        return (c >= '0' && c <= '9') || c == '.';
    }

    public static JavaVersion current() {
        if (currentJavaVersion == null) {
            currentJavaVersion = toVersion(System.getProperty("java.version"));
        }
        return currentJavaVersion;
    }

    public static JavaVersion forClassVersion(int classVersion) {
        if (classVersion < 45) {
            throw new IllegalArgumentException(String.format("Could not determine java version from '%d'.", classVersion));

        }
        //class file versions: 1.1 == 45, 1.2 == 46...
        return new JavaVersion(classVersion - 44);
    }

    public static JavaVersion forClass(byte[] classData) {
        if (classData.length < 8) {
            throw new IllegalArgumentException("Invalid class format. Should contain at least 8 bytes");
        }
        return forClassVersion(classData[7] & 0xFF);
    }

    public boolean isJava5() {
        return getMajorVersionNumber() == 5;
    }

    public boolean isJava6() {
        return getMajorVersionNumber() == 6;
    }

    public boolean isJava7() {
        return getMajorVersionNumber() == 7;
    }

    public boolean isJava8() {
        return getMajorVersionNumber() == 8;
    }

    public boolean isJava9() {
        return getMajorVersionNumber() == 9;
    }

    public boolean isJava10() {
        return getMajorVersionNumber() == 10;
    }

    public boolean isJava5Compatible() {
        return getMajorVersionNumber() >= 5;
    }

    public boolean isJava6Compatible() {
        return getMajorVersionNumber() >= 6;
    }

    public boolean isJava7Compatible() {
        return getMajorVersionNumber() >= 7;
    }

    public boolean isJava8Compatible() {
        return getMajorVersionNumber() >= 8;
    }

    public boolean isJava9Compatible() {
        return getMajorVersionNumber() >= 9;
    }

    @Incubating
    public boolean isJava10Compatible() {
        return getMajorVersionNumber() >= 10;
    }

    @Override
    public String toString() {
        return getName();
    }

    private String getName() {
        return legacyVersionName;
    }

    public String getMajorVersion() {
        return String.valueOf(getMajorVersionNumber());
    }

    /**
     * Returns the major version number, e.g. 9.
     *
     * @since 4.7
     */
    @Incubating
    public int getMajorVersionNumber() {
        return versions.get(0);
    }

    /**
     * Returns the version list, e.g. version '9.0.0.1' will return [9, 0, 0, 1].
     *
     * @since 4.7
     */
    @Incubating
    public List<Integer> getVersions() {
        return versions;
    }

    /**
     * Returns the JavaVersion object with only major version.
     *
     * @since 4.7
     */
    @Incubating
    public JavaVersion major() {
        return JavaVersion.toVersion(versions.get(0));
    }

    @Override
    public int compareTo(JavaVersion that) {
        int shorterLength = versions.size() > that.versions.size() ? that.versions.size() : versions.size();
        for (int i = 0; i < shorterLength; ++i) {
            int thisNumber = versions.get(i);
            int thatNumber = that.versions.get(i);
            if (thisNumber < thatNumber) {
                return -1;
            } else if (thisNumber > thatNumber) {
                return 1;
            }
        }

        if (versions.size() > that.versions.size()) {
            // this is 9.0.0.1, that is 9.0
            return allRestElementsAreZeros(versions, shorterLength) ? 0 : 1;
        } else {
            // this is 9.0, that is 9.0.0.1
            return allRestElementsAreZeros(that.versions, shorterLength) ? 0 : -1;
        }
    }

    private boolean allRestElementsAreZeros(List<Integer> versions, int startIndex) {
        for (int i = startIndex; i < versions.size(); ++i) {
            if (0 != versions.get(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        JavaVersion that = (JavaVersion) o;

        return versions.equals(that.versions);
    }

    @Override
    public int hashCode() {
        return versions.hashCode();
    }
}
