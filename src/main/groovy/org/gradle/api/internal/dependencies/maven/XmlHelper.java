/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.dependencies.maven;

/**
 * @author Hans Dockter
 */
public class XmlHelper {
    public static String openTag(String value) {
        return openTag(0, value);
    }

    public static String openTag(int indent, String value) {
        return getIndentString(indent) + "<" + value + ">";
    }

    public static String closeTag(String value) {
        return closeTag(0, value);
    }

    public static String closeTag(int indent, String value) {
        return getIndentString(indent) + "</" + value + ">";
    }

    public static String enclose(int indent, String tag, String text) {
        return getIndentString(indent) + openTag(tag) + text + closeTag(tag);
    }

    private static String getIndentString(int indent) {
        String indentString = "";
        for (int i = 0; i < indent; i++) {
            indentString += " ";
        }
        return indentString;
    }
}
