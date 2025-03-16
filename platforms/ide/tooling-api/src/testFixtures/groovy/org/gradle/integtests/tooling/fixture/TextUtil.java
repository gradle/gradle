/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.fixture;

import java.io.File;
import java.util.regex.Pattern;

public class TextUtil {

    /**
     * TODO - hack to avoid classloading issues. We should use org.gradle.util.internal.TextUtil
     *
     * Currently we can't use it reliably because it causes CNF issues with cross version integration tests running against tooling api versions less than 1.3.
     */
    public static String escapeString(Object obj) {
        return obj.toString().replaceAll("\\\\", "\\\\\\\\");
    }

    public static String normaliseFileSeparators(String path) {
        return path.replaceAll(Pattern.quote(File.separator), "/");
    }

    /**
     * Converts all line separators in the specified string to a single new line character.
     */
    public static String normaliseLineSeparators(String str) {
        return str == null ? null : convertLineSeparators(str, "\n");
    }

    /**
     * Converts all line separators in the specified string to the specified line separator.
     */
    public static String convertLineSeparators(String str, String sep) {
        return str == null ? null : str.replaceAll("\r\n|\r|\n", sep);
    }
}
