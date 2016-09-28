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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexBackedCSourceParser implements CSourceParser {
    private static final String INCLUDE_IMPORT_PATTERN = "#\\s*(include|import)\\s*((<[^>]+>)|(\"[^\"]+\")|(\\w+))";
    private final Pattern includePattern;

    public RegexBackedCSourceParser() {
        this.includePattern = Pattern.compile(INCLUDE_IMPORT_PATTERN, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public IncludeDirectives parseSource(File sourceFile) {
        return new DefaultIncludeDirectives(parseFile(sourceFile));
    }

    private List<Include> parseFile(File file) {
        List<Include> includes = Lists.newArrayList();
        try {
            BufferedReader bf = new BufferedReader(new PreprocessingReader(new BufferedReader(new FileReader(file))));

            try {
                String line;
                while ((line = bf.readLine()) != null) {
                    Matcher m = includePattern.matcher(line.trim());

                    if (m.matches()) {
                        boolean isImport = "import".equals(m.group(1));
                        String value = m.group(2);

                        includes.add(DefaultInclude.parse(value, isImport));
                    }
                }
            } finally {
                IOUtils.closeQuietly(bf);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return includes;
    }
}
