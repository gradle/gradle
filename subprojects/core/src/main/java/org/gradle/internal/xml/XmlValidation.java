/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.xml;

public class XmlValidation {
    public static boolean isValidXmlName(CharSequence name) {
        // element names can only contain 0 or 1 colon
        // See http://www.w3.org/TR/2004/REC-xml-names11-20040204/#Conformance
        // If the name has a prefix, evaluate both prefix and name
        int pos = 0;
        int nsPos = 0;
        int nsCount = 0;
        for (; pos < name.length(); pos++) {
            char ch = name.charAt(pos);
            if (ch == ':') {
                nsCount++;
                if (nsCount > 1) {
                    return false;
                }
                if (pos > 0) {
                    // non leading ':'
                    nsPos = pos + 1;
                }
                // else leading ':', this is ok
            } else if (pos == nsPos) {
                if (!isValidNameStartChar(ch)) {
                    return false;
                }
            } else {
                if (!isValidNameChar(ch)) {
                    return false;
                }
            }
        }
        return pos != nsPos;
    }

    private static boolean isValidNameChar(char ch) {
        if (isValidNameStartChar(ch)) {
            return true;
        }
        if (ch >= '0' && ch <= '9') {
            return true;
        }
        if (ch == '-' || ch == '.' || ch == '\u00b7') {
            return true;
        }
        if (ch >= '\u0300' && ch <= '\u036f') {
            return true;
        }
        if (ch >= '\u203f' && ch <= '\u2040') {
            return true;
        }
        return false;
    }

    private static boolean isValidNameStartChar(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return true;
        }
        if (ch >= 'a' && ch <= 'z') {
            return true;
        }
        if (ch == ':' || ch == '_') {
            return true;
        }
        if (ch >= '\u00c0' && ch <= '\u00d6') {
            return true;
        }
        if (ch >= '\u00d8' && ch <= '\u00f6') {
            return true;
        }
        if (ch >= '\u00f8' && ch <= '\u02ff') {
            return true;
        }
        if (ch >= '\u0370' && ch <= '\u037d') {
            return true;
        }
        if (ch >= '\u037f' && ch <= '\u1fff') {
            return true;
        }
        if (ch >= '\u200c' && ch <= '\u200d') {
            return true;
        }
        if (ch >= '\u2070' && ch <= '\u218f') {
            return true;
        }
        if (ch >= '\u2c00' && ch <= '\u2fef') {
            return true;
        }
        if (ch >= '\u3001' && ch <= '\ud7ff') {
            return true;
        }
        if (ch >= '\uf900' && ch <= '\ufdcf') {
            return true;
        }
        if (ch >= '\ufdf0' && ch <= '\ufffd') {
            return true;
        }
        return false;
    }

    /**
     * Verifies if a codepoint is an allowed XML char.
     *
     * @see <a href="https://www.w3.org/TR/REC-xml/#charsets">XML Charsets</a>
     * @param c codepoint
     * @return true when the codepoint is a valid XML char
     */
    public static boolean isLegalCharacter(final int c) {
        if (c == 0x9 || c == 0xA || c == 0xD) {
            return true;
        } else if (c < 0x20) {
            return false;
        } else if (c <= 0xD7FF) {
            return true;
        } else if (c < 0xE000) {
            return false;
        } else if (c <= 0xFFFD) {
            return true;
        } else if (c < 0x10000) {
            return false;
        } else if (c <= 0x10FFFF) {
            return true;
        }
        return false;
    }

    public static boolean isRestrictedCharacter(char c) {
        if (c == 0x9 || c == 0xA || c == 0xD || c == 0x85) {
            return false;
        } else if (c <= 0x1F) {
            return true;
        } else if (c < 0x7F) {
            return false;
        } else if (c <= 0x9F) {
            return true;
        }
        return false;
    }
}
