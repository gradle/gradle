/*
 * Copyright 2010 the original author or authors.
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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.StringEscapeUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * This class is only here to maintain binary compatibility with existing plugins.
 * <p>
 * Plugins should prefer external frameworks over this class.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public class TextUtil {

    private static void logDeprecation() {
        DeprecationLogger.deprecateType(TextUtil.class)
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "org_gradle_util_reports_deprecations")
            .nagUser();
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s*");
    private static final Pattern UPPER_CASE = Pattern.compile("(?=\\p{Upper})");
    private static final Joiner KEBAB_JOINER = Joiner.on("-");

    private static final Function<String, String> TO_LOWERCASE = new Function<String, String>() {
        @Override
        public String apply(String input) {
            return input.toLowerCase(Locale.ROOT);
        }
    };
    private static final Pattern NON_UNIX_LINE_SEPARATORS = Pattern.compile("\r\n|\r");

    public TextUtil() {
        logDeprecation();
    }

    /**
     * Returns the line separator for Windows.
     */
    public static String getWindowsLineSeparator() {
        logDeprecation();
        return "\r\n";
    }

    /**
     * Returns the line separator for Unix.
     */
    public static String getUnixLineSeparator() {
        logDeprecation();
        return "\n";
    }

    /**
     * Returns the line separator for this platform.
     */
    public static String getPlatformLineSeparator() {
        logDeprecation();
        return getPlatformLineSeparatorInternal();
    }

    private static String getPlatformLineSeparatorInternal() {
        return SystemProperties.getInstance().getLineSeparator();
    }

    /**
     * Converts all line separators in the specified string to the specified line separator.
     */
    @Nullable
    public static String convertLineSeparators(@Nullable String str, String sep) {
        logDeprecation();
        return str == null ? null : replaceLineSeparatorsOf(str, sep);
    }

    /**
     * Converts all line separators in the specified non-null string to the Unix line separator {@code \n}.
     */
    public static String convertLineSeparatorsToUnix(String str) {
        logDeprecation();
        return convertLineSeparatorsToUnixInternal(str);
    }

    private static String convertLineSeparatorsToUnixInternal(String str) {
        return replaceAll(NON_UNIX_LINE_SEPARATORS, str, "\n");
    }

    /**
     * Converts all line separators in the specified non-null {@link CharSequence} to the specified line separator.
     */
    public static String replaceLineSeparatorsOf(CharSequence string, String bySeparator) {
        logDeprecation();
        return replaceLineSeparatorsInternalOf(string, bySeparator);
    }

    private static String replaceLineSeparatorsInternalOf(CharSequence string, String bySeparator) {
        logDeprecation();
        return replaceAll("\r\n|\r|\n", string, bySeparator);
    }

    private static String replaceAll(String regex, CharSequence inString, String byString) {
        return replaceAll(Pattern.compile(regex), inString, byString);
    }

    private static String replaceAll(Pattern pattern, CharSequence inString, String byString) {
        return pattern.matcher(inString).replaceAll(byString);
    }

    /**
     * Converts all line separators in the specified string to the platform's line separator.
     */
    public static String toPlatformLineSeparators(String str) {
        logDeprecation();
        return str == null ? null : replaceLineSeparatorsInternalOf(str, getPlatformLineSeparatorInternal());
    }

    /**
     * Converts all line separators in the specified nullable string to a single new line character ({@code \n}).
     *
     * @return null if the given string is null
     */
    @Nullable
    public static String normaliseLineSeparators(@Nullable String str) {
        logDeprecation();
        return normaliseLineSeparatorsInternal(str);
    }

    private static String normaliseLineSeparatorsInternal(@Nullable String str) {
        return str == null ? null : convertLineSeparatorsToUnixInternal(str);
    }

    /**
     * Converts all native file separators in the specified string to '/'.
     */
    public static String normaliseFileSeparators(String path) {
        logDeprecation();
        return normaliseFileSeparatorsInternal(path);
    }

    private static String normaliseFileSeparatorsInternal(String path) {
        return path.replace(File.separatorChar, '/');
    }

    /**
     * Escapes the toString() representation of {@code obj} for use in a literal string.
     * This is useful for interpolating variables into script strings, as well as in other situations.
     */
    public static String escapeString(Object obj) {
        logDeprecation();
        return obj == null ? null : StringEscapeUtils.escapeJava(obj.toString());
    }

    /**
     * Tells whether the specified string contains any whitespace characters.
     */
    public static boolean containsWhitespace(String str) {
        logDeprecation();
        for (int i = 0; i < str.length(); i++) {
            if (Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indents every line of {@code text} by {@code indent}. Empty lines
     * and lines that only contain whitespace are not indented.
     */
    public static String indent(String text, String indent) {
        logDeprecation();
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!WHITESPACE.matcher(line).matches()) {
                builder.append(indent);
            }
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }

        return builder.toString();
    }

    public static String shorterOf(String s1, String s2) {
        logDeprecation();
        if (s2.length() >= s1.length()) {
            return s1;
        } else {
            return s2;
        }
    }

    /**
     * Same behavior as Groovy minus operator between Strings
     *
     * @param originalString original string
     * @param removeString string to remove
     * @return string with removeString removed or the original string if it did not contain removeString
     */
    public static String minus(String originalString, String removeString) {
        logDeprecation();
        String s = originalString.toString();
        int index = s.indexOf(removeString);
        if (index == -1) {
            return s;
        }
        int end = index + removeString.length();
        if (s.length() > end) {
            return s.substring(0, index) + s.substring(end);
        }
        return s.substring(0, index);
    }

    public static String normaliseFileAndLineSeparators(String in) {
        logDeprecation();
        return normaliseLineSeparatorsInternal(normaliseFileSeparatorsInternal(in));
    }

    public static String camelToKebabCase(String camelCase) {
        logDeprecation();
        return KEBAB_JOINER.join(Iterables.transform(Arrays.asList(UPPER_CASE.split(camelCase)), TO_LOWERCASE));
    }

    /**
     * This method should be used when making strings lowercase that
     * could be affected by locale differences. This method always uses an
     * English locale.
     *
     * @param s string to be made lowercase
     * @return a lowercase string that ignores locale
     * @see <a href="https://issues.gradle.org/browse/GRADLE-3470">GRADLE-3470</a>
     * @see <a href="https://haacked.com/archive/2012/07/05/turkish-i-problem-and-why-you-should-care.aspx/">Turkish i problem</a>
     */
    public static String toLowerCaseLocaleSafe(String s) {
        logDeprecation();
        return s.toLowerCase(Locale.ENGLISH);
    }
}
