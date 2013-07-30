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

public class Identifier {
    private static final String PUNCTUATION_CHARS = "-'!@#$%^&*()_+=,.?{}[]<>";
    private static final String NON_ASCII_CHARS = "-√æず∫ʙぴ₦ガき∆ç√∫";
    private static final String FILESYSTEM_RESERVED_CHARS = "-./\\?%*:|\"<>";
    private static final String XML_MARKUP_CHARS = "-<with>some<xml-markup/></with>";

    private final String suffix;

    public Identifier(String suffix) {
        this.suffix = suffix == null ? "" : suffix;
    }

    public Identifier withPunctuation() {
        return new Identifier(suffix + PUNCTUATION_CHARS);
    }

    public Identifier withNonAscii() {
        return new Identifier(suffix + NON_ASCII_CHARS);
    }

    public Identifier withReservedFileSystemChars() {
        return new Identifier(suffix + FILESYSTEM_RESERVED_CHARS);
    }

    public Identifier withMarkup() {
        return new Identifier(suffix + XML_MARKUP_CHARS);
    }

    public Identifier withWhiteSpace() {
        return new Identifier(suffix + " with white space");
    }

    public Identifier safeForFileName() {
        return without(getUnsupportedFileNameCharacters());
    }

    public Identifier without(String toRemove) {
        String newSuffix = suffix;
        for (char c : toRemove.toCharArray()) {
            newSuffix = newSuffix.replace(c, '-');
        }
        return new Identifier(newSuffix);
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

    @Override
    public String toString() {
        return suffix;
    }

    public static Identifier getPunctuation() {
        return new Identifier("").withPunctuation();
    }

    public static Identifier getNonAscii() {
        return new Identifier("").withNonAscii();
    }

    public static Identifier getFileSystemReserved() {
        return new Identifier("").withReservedFileSystemChars();
    }

    public static Identifier getXmlMarkup() {
        return new Identifier("").withMarkup();
    }

    public static Identifier getWhiteSpace() {
        return new Identifier("").withWhiteSpace();
    }
}
