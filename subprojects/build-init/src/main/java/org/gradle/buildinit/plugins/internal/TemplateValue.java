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

package org.gradle.buildinit.plugins.internal;

import org.gradle.util.TextUtil;

public class TemplateValue {
    private final String value;

    public TemplateValue(String value) {
        this.value = value;
    }

    public String getGroovyComment() {
        return value.replace("\\", "\\\\");
    }

    public String getGroovyString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    result.append('\\').append('\\');
                    break;
                case '\'':
                    result.append('\\').append('\'');
                    break;
                case '\n':
                    result.append('\\').append('n');
                    break;
                case '\r':
                    result.append('\\').append('r');
                    break;
                case '\t':
                    result.append('\\').append('t');
                    break;
                case '\f':
                    result.append('\\').append('f');
                    break;
                case '\b':
                    result.append('\\').append('b');
                    break;
                default:
                    result.append(ch);
            }
        }
        return result.toString();
    }

    public String getStatement() {
        if (value.isEmpty()) {
            return "";
        } else {
            return value + TextUtil.getPlatformLineSeparator();
        }
    }

    public String getJavaStatement() {
        if (value.isEmpty()) {
            return "";
        } else {
            return value + ";" + TextUtil.getPlatformLineSeparator();
        }
    }

    public String getJavaIdentifier() {
        return value;
    }

    public String getRaw() {
        return value;
    }

    @Override
    public String toString() {
        return ">>>" + value + "<<<";
    }
}
