/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.encoding;

import org.gradle.internal.os.OperatingSystem;

import java.util.Arrays;
import java.util.List;

public class Identifier {
    private static final String PUNCTUATION_CHARS = "-'!@#$%^&*()_+=,.?{}[]<>";
    private static final String NON_ASCII_CHARS = "-√æず∫ʙぴ₦ガき∆ç√∫";
    private static final String NON_PRECOMPOSED_NON_ASCII = "-√æ∫ʙ₦∆√∫";
    private static final String FILESYSTEM_RESERVED_CHARS = "-./\\?%*:|\"<>";
    private static final String XML_MARKUP_CHARS = "-<with>some<xml-markup/></with>";

    private final String suffix;
    private final String displayName;

    private Identifier(String suffix, String displayName) {
        this.displayName = displayName;
        this.suffix = suffix == null ? "" : suffix;
    }

    public Identifier safeForBranch() {
        return without(getUnsupportedFileNameCharacters().replace("/", ""));
    }

    public Identifier safeForFileName() {
        return without(getUnsupportedFileNameCharacters());
    }

    public Identifier without(String toRemove) {
        String newSuffix = suffix;
        for (char c : toRemove.toCharArray()) {
            newSuffix = newSuffix.replace(c, '-');
        }
        return new Identifier(newSuffix, displayName);
    }

    private static String getUnsupportedFileNameCharacters() {
        if (OperatingSystem.current().isWindows()) {
            return "<>:\"/\\|?*";
        }
        return "/\\";
    }

    public String decorate(String prefix) {
        return prefix + suffix;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static List<Identifier> getAll() {
        return Arrays.asList(getPunctuation(), getNonAscii(), getFileSystemReserved(), getXmlMarkup(), getWhiteSpace());
    }

    public static Identifier getPunctuation() {
        return new Identifier(PUNCTUATION_CHARS, "punctuation");
    }

    public static Identifier getNonAscii() {
        if (OperatingSystem.current().isMacOsX()) {
            // The hfs+ file system stores file names in decomposed form. Don't use precomposed characters on OS X, as way too few things normalise text correctly
            return new Identifier(NON_PRECOMPOSED_NON_ASCII, "non-ascii");
        }
        return new Identifier(NON_ASCII_CHARS, "non-ascii");
    }

    public static Identifier getFileSystemReserved() {
        return new Identifier(FILESYSTEM_RESERVED_CHARS, "filesystem");
    }

    public static Identifier getXmlMarkup() {
        return new Identifier(XML_MARKUP_CHARS, "xml markup");
    }

    public static Identifier getWhiteSpace() {
        return new Identifier(" with white space", "whitespace");
    }
}
