/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.internal;

import java.util.Arrays;
import java.util.List;

/**
 * An immutable representation of a valid Java package name.
 *
 * <p>While {@code JvmPackageName} values are guaranteed to be valid per JLS package
 * naming rules, no validation is performed to ensure that the named package actually
 * exists.</p>
 *
 * <p>Note that due to vagaries inherent in Java naming, a valid package name is also
 * always a valid type name.</p>
 *
 * @see #of(String)
 * @since 2.10
 */
public class JvmPackageName {

    private static final List<String> JAVA_KEYWORDS = Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while"
    );

    private static final List<String> BOOLEAN_AND_NULL_LITERALS = Arrays.asList("true", "false", "null");

    private static final String UNNAMED_PACKAGE = "";

    private final String value;

    private JvmPackageName(String value) {
        this.value = value;
    }

    /**
     * The underlying package name value, e.g. "com.example.p1".
     */
    public String getValue() {
        return value;
    }

    /**
     * Validate, create and return a new {@code PackageName} instance from the given
     * package name.
     *
     * <p>See JLS sections:
     * <ul>
     *   <li><em><a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.8">
     *     3.8. Identifiers</a></em></li>
     *   <li><em><a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-6.html#jls-6.2">
     *     6.2. Names and Identifiers</a></em></li>
     *   <li><em><a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-7.html#jls-7.4">
     *     7.4. Package Declarations</a></em></li>
     * </ul>
     * </p>
     *
     * @param value the candidate package name, e.g.: {@code com.example.p1} or an empty
     * string indicating an <em>unnamed package</em>
     * @return the validated package name instance, never null
     * @throws IllegalArgumentException if value is null or does not conform to valid
     * package naming per the JLS
     */
    public static JvmPackageName of(String value) {
        if (!isValidPackageName(value)) {
            throw new IllegalArgumentException(String.format("'%s' is not a valid package name", value));
        }
        return new JvmPackageName(value);
    }

    private static boolean isValidPackageName(String value) {
        if (UNNAMED_PACKAGE.equals(value)) {
            return true;
        }
        if (value == null
                || value.startsWith(".")
                || value.endsWith(".")) {
            return false;
        }
        for (String token : value.split("\\.")) {
            if (!isIdentifier(token)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifier(String token) {
        if (token.isEmpty()
                || JAVA_KEYWORDS.contains(token)
                || BOOLEAN_AND_NULL_LITERALS.contains(token)
                || !Character.isJavaIdentifierStart(token.charAt(0))) {
            return false;
        }
        if (token.length() > 1) {
            for (char c : token.substring(1).toCharArray()) {
                if (!Character.isJavaIdentifierPart(c)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JvmPackageName that = (JvmPackageName) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
