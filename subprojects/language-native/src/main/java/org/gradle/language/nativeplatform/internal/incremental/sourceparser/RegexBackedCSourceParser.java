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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexBackedCSourceParser implements CSourceParser {
    private static final String INCLUDE_IMPORT_PATTERN = "#\\s*(include|import)((\"|<|(\\s+\\S)).*)";
    private static final String MACRO_PATTERN = "#\\s*define\\s+(\\w+)(\\s+(.+)?)?";
    private final Pattern includePattern;
    private final Pattern macroPattern;

    public RegexBackedCSourceParser() {
        this.includePattern = Pattern.compile(INCLUDE_IMPORT_PATTERN, Pattern.CASE_INSENSITIVE);
        this.macroPattern = Pattern.compile(MACRO_PATTERN, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public IncludeDirectives parseSource(File sourceFile) {
        List<Include> includes = Lists.newArrayList();
        List<Macro> macros = Lists.newArrayList();
        try {
            BufferedReader bf = new BufferedReader(new PreprocessingReader(new BufferedReader(new FileReader(sourceFile))));
            try {
                String line;
                while ((line = bf.readLine()) != null) {
                    line = line.trim();
                    Matcher m = includePattern.matcher(line);

                    if (m.matches()) {
                        boolean isImport = "import".equals(m.group(1));
                        String value = m.group(2).trim();
                        includes.add(DefaultInclude.parse(value, isImport));
                        continue;
                    }

                    m = macroPattern.matcher(line);
                    if (m.matches()) {
                        String name = m.group(1);
                        String value = m.group(3);
                        Include include = DefaultInclude.parse(value == null ? "" : value, false);
                        if (include.getType() != IncludeType.OTHER) {
                            macros.add(new DefaultMacro(name, include.getType(), include.getValue()));
                        } else {
                            macros.add(new UnresolveableMacro(name));
                        }
                    }
                }
            } finally {
                IOUtils.closeQuietly(bf);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DefaultIncludeDirectives(ImmutableList.copyOf(includes), ImmutableList.copyOf(macros));
    }
}
