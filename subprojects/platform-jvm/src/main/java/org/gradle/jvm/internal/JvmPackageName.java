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

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class JvmPackageName {
    private static final char DELIMITER = '.';
    private static final Splitter PACKAGE_FRAGMENT_SPLITTER = Splitter.on(DELIMITER);
    private static final String UNNAMED_PACKAGE = "";

    private static final Set<String> INVALID_PACKAGE_KEYWORDS = ImmutableSet.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "_",
        "true", "false",
        "null",
        ""
    );

    private JvmPackageName() {
    }

    /**
     * Validate a given package name.
     *
     * <p>See JLS sections:
     * <ul>
     * <li><em><a href="https://docs.oracle.com/javase/specs/jls/se12/html/jls-3.html#jls-3.8"> 3.8. Identifiers</a></em></li>
     * <li><em><a href="https://docs.oracle.com/javase/specs/jls/se12/html/jls-6.html#jls-6.2"> 6.2. Names and Identifiers</a></em></li>
     * <li><em><a href="https://docs.oracle.com/javase/specs/jls/se12/html/jls-7.html#jls-7.4"> 7.4. Package Declarations</a></em></li>
     * </ul>
     * </p>
     *
     * @return whether value is null or does not conform to valid package naming per the JLS
     */
    public static boolean isValid(String value) {
        if (UNNAMED_PACKAGE.equals(value)) {
            return true;
        } else if (value == null) {
            return false;
        } else {
            return fragmentsAreValid(value);
        }
    }

    private static boolean fragmentsAreValid(String value) {
        for (String packageFragment : PACKAGE_FRAGMENT_SPLITTER.split(value)) {
            if (!isIdentifier(packageFragment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIdentifier(String token) {
        return !INVALID_PACKAGE_KEYWORDS.contains(token)
            && isValidJavaIdentifier(token);
    }

    private static boolean isValidJavaIdentifier(String token) {
        if (!Character.isJavaIdentifierStart(token.charAt(0))) {
            return false;
        } else {
            for (int i = 1; i < token.length(); i++) {
                if (!Character.isJavaIdentifierPart(token.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }
}
