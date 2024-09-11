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
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public class VersionNumber implements Comparable<VersionNumber> {

    private static void logDeprecation(int majorVersion) {
        DeprecationLogger.deprecateType(VersionNumber.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(majorVersion, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    private static final DefaultScheme DEFAULT_SCHEME = new DefaultScheme();
    private static final SchemeWithPatchVersion PATCH_SCHEME = new SchemeWithPatchVersion();
    public static final VersionNumber UNKNOWN = version(0);

    private final int major;
    private final int minor;
    private final int micro;
    private final int patch;
    private final String qualifier;
    private final AbstractScheme scheme;
    private boolean deprecationLogged;

    public VersionNumber(int major, int minor, int micro, @Nullable String qualifier) {
        this(major, minor, micro, 0, qualifier, DEFAULT_SCHEME, true);
    }

    public VersionNumber(int major, int minor, int micro, int patch, @Nullable String qualifier) {
        this(major, minor, micro, patch, qualifier, PATCH_SCHEME, true);
    }

    private VersionNumber(int major, int minor, int micro, int patch, @Nullable String qualifier, AbstractScheme scheme, boolean logDeprecation) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
        this.patch = patch;
        this.qualifier = qualifier;
        this.scheme = scheme;
        if (logDeprecation) {
            logDeprecation(7);
            deprecationLogged = true;
        }
    }

    private void maybeLogDeprecation() {
        if(!deprecationLogged) {
            logDeprecation(7);
        }
    }

    public int getMajor() {
        maybeLogDeprecation();
        return major;
    }

    public int getMinor() {
        maybeLogDeprecation();
        return minor;
    }

    public int getMicro() {
        maybeLogDeprecation();
        return micro;
    }

    public int getPatch() {
        maybeLogDeprecation();
        return patch;
    }

    @Nullable
    public String getQualifier() {
        maybeLogDeprecation();
        return qualifier;
    }

    public VersionNumber getBaseVersion() {
        maybeLogDeprecation();
        return new VersionNumber(major, minor, micro, patch, null, scheme, false);
    }

    @Override
    public int compareTo(VersionNumber other) {
        logDeprecation(8);
        if (major != other.major) {
            return major - other.major;
        }
        if (minor != other.minor) {
            return minor - other.minor;
        }
        if (micro != other.micro) {
            return micro - other.micro;
        }
        if (patch != other.patch) {
            return patch - other.patch;
        }
        return Ordering.natural().nullsLast().compare(toLowerCase(qualifier), toLowerCase(other.qualifier));
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof VersionNumber && compareTo((VersionNumber) other) == 0;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + micro;
        result = 31 * result + patch;
        result = 31 * result + Objects.hashCode(qualifier);
        return result;
    }

    @Override
    public String toString() {
        maybeLogDeprecation();
        return scheme.format(this);
    }

    public static VersionNumber version(int major) {
        return version(major, 0);
    }

    public static VersionNumber version(int major, int minor) {
        return new VersionNumber(major, minor, 0, 0, null, DEFAULT_SCHEME, false);
    }

    /**
     * Returns the default MAJOR.MINOR.MICRO-QUALIFIER scheme.
     */
    public static Scheme scheme() {
        logDeprecation(7);
        return DEFAULT_SCHEME;
    }

    /**
     * Returns the MAJOR.MINOR.MICRO.PATCH-QUALIFIER scheme.
     */
    public static Scheme withPatchNumber() {
        logDeprecation(7);
        return PATCH_SCHEME;
    }

    public static VersionNumber parse(String versionString) {
        logDeprecation(8);
        return DEFAULT_SCHEME.parse(versionString);
    }

    @Nullable
    private String toLowerCase(@Nullable String string) {
        return string == null ? null : string.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the version number scheme.
     */
    @Deprecated
    public interface Scheme {
        VersionNumber parse(String value);

        String format(VersionNumber versionNumber);
    }

    private abstract static class AbstractScheme implements Scheme {
        final int depth;

        protected AbstractScheme(int depth) {
            this.depth = depth;
        }

        @Override
        public VersionNumber parse(@Nullable String versionString) {
            if (versionString == null || versionString.length() == 0) {
                return UNKNOWN;
            }
            Scanner scanner = new Scanner(versionString);


            if (!scanner.hasDigit()) {
                return UNKNOWN;
            }
            int minor = 0;
            int micro = 0;
            int patch = 0;
            int major = scanner.scanDigit();
            if (scanner.isSeparatorAndDigit('.')) {
                scanner.skipSeparator();
                minor = scanner.scanDigit();
                if (scanner.isSeparatorAndDigit('.')) {
                    scanner.skipSeparator();
                    micro = scanner.scanDigit();
                    if (depth > 3 && scanner.isSeparatorAndDigit('.', '_')) {
                        scanner.skipSeparator();
                        patch = scanner.scanDigit();
                    }
                }
            }

            if (scanner.isEnd()) {
                return new VersionNumber(major, minor, micro, patch, null, this, false);
            }

            if (scanner.isQualifier()) {
                scanner.skipSeparator();
                return new VersionNumber(major, minor, micro, patch, scanner.remainder(), this, false);
            }

            return UNKNOWN;
        }

        private static class Scanner {
            int pos;
            final String str;

            private Scanner(String string) {
                this.str = string;
            }

            boolean hasDigit() {
                return pos < str.length() && Character.isDigit(str.charAt(pos));
            }

            boolean isSeparatorAndDigit(char... separators) {
                return pos < str.length() - 1 && oneOf(separators) && Character.isDigit(str.charAt(pos + 1));
            }

            private boolean oneOf(char... separators) {
                char current = str.charAt(pos);
                for (int i = 0; i < separators.length; i++) {
                    char separator = separators[i];
                    if (current == separator) {
                        return true;
                    }
                }
                return false;
            }

            boolean isQualifier() {
                return pos < str.length() - 1 && oneOf('.', '-');
            }

            int scanDigit() {
                int start = pos;
                while (hasDigit()) {
                    pos++;
                }
                return Integer.parseInt(str.substring(start, pos));
            }

            public boolean isEnd() {
                return pos == str.length();
            }

            public void skipSeparator() {
                pos++;
            }

            @Nullable
            public String remainder() {
                return pos == str.length() ? null : str.substring(pos);
            }
        }
    }

    private static class DefaultScheme extends AbstractScheme {
        private static final String VERSION_TEMPLATE = "%d.%d.%d%s";

        public DefaultScheme() {
            super(3);
        }

        @Override
        public String format(VersionNumber versionNumber) {
            return String.format(VERSION_TEMPLATE, versionNumber.major, versionNumber.minor, versionNumber.micro, versionNumber.qualifier == null ? "" : "-" + versionNumber.qualifier);
        }
    }

    private static class SchemeWithPatchVersion extends AbstractScheme {
        private static final String VERSION_TEMPLATE = "%d.%d.%d.%d%s";

        private SchemeWithPatchVersion() {
            super(4);
        }

        @Override
        public String format(VersionNumber versionNumber) {
            return String.format(VERSION_TEMPLATE, versionNumber.major, versionNumber.minor, versionNumber.micro, versionNumber.patch, versionNumber.qualifier == null ? "" : "-" + versionNumber.qualifier);
        }
    }

}

