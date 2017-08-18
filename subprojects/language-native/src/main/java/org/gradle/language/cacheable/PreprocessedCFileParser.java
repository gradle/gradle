/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cacheable;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PreprocessedCFileParser {
    // # 41 "/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/../include/c++/v1/iostream" 2 3
    private static final String INCLUDE_IMPORT_PATTERN = "^# \\d{1,} \"([^\"]*)\" \\d{1,}( \\d{1,}?)*";
    private final Pattern includePattern;

    public PreprocessedCFileParser() {
        this.includePattern = Pattern.compile(INCLUDE_IMPORT_PATTERN, Pattern.CASE_INSENSITIVE);
    }

    public void parseFile(File file, Action<String> includeAction) {
        try {
            BufferedReader bf = new BufferedReader(new BufferedReader(new FileReader(file)));

            try {
                String line;
                while ((line = bf.readLine()) != null) {
                    Matcher m = includePattern.matcher(line.trim());

                    if (m.matches()) {
                        String includedFile = m.group(1);
                        includeAction.execute(includedFile);
                    }
                }
            } finally {
                IOUtils.closeQuietly(bf);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
