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

package org.gradle.internal.resource.transport.aws.s3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3ResourceNameExtractor {
    private static final Pattern FILENAME_PATTERN = Pattern.compile("[^/]+\\.*$");
    private static final Pattern DIRNAME_PATTERN = Pattern.compile("[^/]+/$");

    /**
     * Extracts the directory name from a common prefix
     */
    public static String extractDirectoryName(String key) {
        Matcher matcher = DIRNAME_PATTERN.matcher(key);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    /**
     * Extracts the filename of a file in S3.  Filename must contain a '.'
     */
    public static String extractResourceName(String key) {
        Matcher matcher = FILENAME_PATTERN.matcher(key);
        if (matcher.find()) {
            String group = matcher.group(0);
            return group.contains(".") ? group : null;
        }
        return null;
    }
}
