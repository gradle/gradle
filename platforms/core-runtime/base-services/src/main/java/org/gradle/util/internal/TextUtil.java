/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.SystemProperties;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

// TODO Do not rely on default encoding
@SuppressWarnings("StringCaseLocaleUsage")
public class TextUtil {
    private static final Pattern WHITESPACE = Pattern.compile("\\s*");
    private static final Pattern UPPER_CASE = Pattern.compile("(?=\\p{Upper})");
    private static final Joiner KEBAB_JOINER = Joiner.on("-");
    private static final Function<String, String> TO_LOWERCASE = new Function<String, String>() {
        @Override
        public String apply(String input) {
            return input.toLowerCase();
        }
    };
    private static final Pattern NON_UNIX_LINE_SEPARATORS = Pattern.compile("\r\n|\r");

    /**
     * Returns the line separator for Windows.
     */
    public static String getWindowsLineSeparator() {
        return "\r\n";
    }

    /**
     * Returns the line separator for Unix.
     */
    public static String getUnixLineSeparator() {
        return "\n";
    }

    /**
     * Returns the line separator for this platform.
     */
    public static String getPlatformLineSeparator() {
        return SystemProperties.getInstance().getLineSeparator();
    }

    /**
     * Converts all line separators in the specified string to the specified line separator.
     */
    @Nullable
    public static String convertLineSeparators(@Nullable String str, String sep) {
        return str == null ? null : replaceLineSeparatorsOf(str, sep);
    }

    /**
     * Converts all line separators in the specified non-null string to the Unix line separator {@code \n}.
     */
    public static String convertLineSeparatorsToUnix(String str) {
        return replaceAll(NON_UNIX_LINE_SEPARATORS, str, "\n");
    }

    /**
     * Converts all line separators in the specified non-null {@link CharSequence} to the specified line separator.
     */
    public static String replaceLineSeparatorsOf(CharSequence string, String bySeparator) {
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
        return str == null ? null : replaceLineSeparatorsOf(str, getPlatformLineSeparator());
    }

    /**
     * Converts all line separators in the specified nullable string to a single new line character ({@code \n}).
     *
     * @return null if the given string is null
     */
    @Nullable
    public static String normaliseLineSeparators(@Nullable String str) {
        return str == null ? null : convertLineSeparatorsToUnix(str);
    }

    /**
     * Converts all native file separators in the specified string to '/'.
     */
    public static String normaliseFileSeparators(String path) {
        return path.replace(File.separatorChar, '/');
    }

    /**
     * Checks if a String is whitespace, empty ("") or null.
     * <p>
     * This function was copied from Apache Commons-Lang to restore TAPI compatibility
     * with Java 6 on old Gradle versions because StringUtils require SQLException which
     * is absent from Java 6.
     */
    public static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Capitalizes a String changing the first letter to title case as
     * per {@link Character#toTitleCase(char)}. No other letters are changed.
     * <p>
     * This function was copied from Apache Commons-Lang to restore TAPI compatibility
     * with Java 6 on old Gradle versions because StringUtils require SQLException which
     * is absent from Java 6.
     */
    public static String capitalize(String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        return Character.toTitleCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Escapes the toString() representation of {@code obj} for use in a literal string.
     * This is useful for interpolating variables into script strings, as well as in other situations.
     */
    public static String escapeString(Object obj) {
        return obj == null ? null : escapeJavaStyleString(obj.toString(), false, false);
    }

    /**
     * This function was copied from Apache Commons-Lang to restore TAPI compatibility
     * with Java 6 on old Gradle versions because StringUtils require SQLException which
     * is absent from Java 6.
     */
    private static String escapeJavaStyleString(String str, boolean escapeSingleQuotes, boolean escapeForwardSlash) {
        if (str == null) {
            return null;
        }
        try {
            StringWriter writer = new StringWriter(str.length() * 2);
            escapeJavaStyleString(writer, str, escapeSingleQuotes, escapeForwardSlash);
            return writer.toString();
        } catch (IOException ioe) {
            // this should never ever happen while writing to a StringWriter
            throw new UncheckedIOException(ioe);
        }
    }


    private static void escapeJavaStyleString(
        Writer out, String str, boolean escapeSingleQuote,
        boolean escapeForwardSlash
    ) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("The Writer must not be null");
        }
        if (str == null) {
            return;
        }
        int sz;
        sz = str.length();
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);

            // handle unicode
            if (ch > 0xfff) {
                out.write("\\u" + hex(ch));
            } else if (ch > 0xff) {
                out.write("\\u0" + hex(ch));
            } else if (ch > 0x7f) {
                out.write("\\u00" + hex(ch));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b':
                        out.write('\\');
                        out.write('b');
                        break;
                    case '\n':
                        out.write('\\');
                        out.write('n');
                        break;
                    case '\t':
                        out.write('\\');
                        out.write('t');
                        break;
                    case '\f':
                        out.write('\\');
                        out.write('f');
                        break;
                    case '\r':
                        out.write('\\');
                        out.write('r');
                        break;
                    default:
                        if (ch > 0xf) {
                            out.write("\\u00" + hex(ch));
                        } else {
                            out.write("\\u000" + hex(ch));
                        }
                        break;
                }
            } else {
                switch (ch) {
                    case '\'':
                        if (escapeSingleQuote) {
                            out.write('\\');
                        }
                        out.write('\'');
                        break;
                    case '"':
                        out.write('\\');
                        out.write('"');
                        break;
                    case '\\':
                        out.write('\\');
                        out.write('\\');
                        break;
                    case '/':
                        if (escapeForwardSlash) {
                            out.write('\\');
                        }
                        out.write('/');
                        break;
                    default:
                        out.write(ch);
                        break;
                }
            }
        }
    }

    private static String hex(char ch) {
        return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
    }

    /**
     * Tells whether the specified string contains any whitespace characters.
     */
    public static boolean containsWhitespace(String str) {
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
        return normaliseLineSeparators(normaliseFileSeparators(in));
    }

    public static String camelToKebabCase(String camelCase) {
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
        return s.toLowerCase(Locale.ENGLISH);
    }

    /**
     * This method returns the plural ending for an english word for trivial cases depending on the number of elements a list has.
     *
     * @param collection which size is used to determine the plural ending
     * @return "s" if the collection has more than one element, an empty string otherwise
     */
    public static String getPluralEnding(Collection<?> collection) {
        return collection.size() > 1 ? "s" : "";
    }

    public static String endLineWithDot(String txt) {
        if (txt.endsWith(".") || txt.endsWith("\n")) {
            return txt;
        }
        return txt + ".";
    }

    public static String screamingSnakeToKebabCase(String text) {
        return StringUtils.replace(toLowerCaseLocaleSafe(text), "_", "-");
    }
}
