/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource.core;

public class CharacterEncodingUtil {
    public static String extractCharacterEncoding(String contentType, String defaultEncoding) {
        if (contentType == null) {
            return defaultEncoding;
        }
        int pos = findFirstParameter(0, contentType);
        if (pos == -1) {
            return defaultEncoding;
        }
        StringBuilder paramName = new StringBuilder();
        StringBuilder paramValue = new StringBuilder();
        pos = findNextParameter(pos, contentType, paramName, paramValue);
        while (pos != -1) {
            if (paramName.toString().equals("charset") && paramValue.length() > 0) {
                return paramValue.toString();
            }
            pos = findNextParameter(pos, contentType, paramName, paramValue);
        }
        return defaultEncoding;
    }

    private static int findFirstParameter(int pos, String contentType) {
        int index = contentType.indexOf(';', pos);
        if (index < 0) {
            return -1;
        }
        return index + 1;
    }

    private static int findNextParameter(int pos, String contentType, StringBuilder paramName, StringBuilder paramValue) {
        if (pos >= contentType.length()) {
            return -1;
        }
        paramName.setLength(0);
        paramValue.setLength(0);
        int separator = contentType.indexOf("=", pos);
        if (separator < 0) {
            separator = contentType.length();
        }
        paramName.append(contentType.substring(pos, separator).trim());
        if (separator >= contentType.length() - 1) {
            return contentType.length();
        }

        int startValue = separator + 1;
        int endValue;
        if (contentType.charAt(startValue) == '"') {
            startValue++;
            int i = startValue;
            while (i < contentType.length()) {
                char ch = contentType.charAt(i);
                if (ch == '\\' && i < contentType.length() - 1 && contentType.charAt(i + 1) == '"') {
                    paramValue.append('"');
                    i += 2;
                } else if (ch == '"') {
                    break;
                } else {
                    paramValue.append(ch);
                    i++;
                }
            }
            endValue = i + 1;
        } else {
            endValue = contentType.indexOf(';', startValue);
            if (endValue < 0) {
                endValue = contentType.length();
            }
            paramValue.append(contentType.substring(startValue, endValue));
        }
        if (endValue < contentType.length() && contentType.charAt(endValue) == ';') {
            endValue++;
        }
        return endValue;
    }
}
