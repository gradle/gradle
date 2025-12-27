/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.plugins.antlr.internal.antlr2;

import antlr.preprocessor.Hierarchy;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedReader;
import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

/**
 * Preprocess an Antlr grammar file so that dependencies between grammars can be properly determined such that they can
 * be processed for generation in proper order later.
 */
public class MetadataExtractor {

    public static XRef extractMetadata(Set<File> sources) {
        Hierarchy hierarchy = new Hierarchy(new antlr.Tool()); // extracting into methods will break test somehow.
        // first let antlr preprocess the grammars...
        for (File grammarFile : sources) {
            try {
                hierarchy.readGrammarFile(grammarFile.getPath());
            } catch (FileNotFoundException e) {
                // should never happen here
                throw new IllegalStateException("Received FileNotFoundException on already read file", e);
            }
        }
        // now, do our processing using the antlr preprocessor results whenever possible.
        XRef xref = new XRef(hierarchy);
        for (File grammarFile : sources) {
            xref.addGrammarFile(
                new GrammarFileMetadata(
                    grammarFile,
                    hierarchy.getFile(grammarFile.getPath()),
                    getPackageName(grammarFile))
            );
        }
        return xref;
    }

    @Nullable
    private static String getPackageName(File grammarFile) {
        try {
            return getPackageName(newBufferedReader(grammarFile.toPath(), UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read antlr grammar file", e);
        }
    }

    @Nullable
    static String getPackageName(Reader reader) throws IOException {
        String grammarPackageName = null;
        try (BufferedReader in = new BufferedReader(reader)) {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("package") && line.endsWith(";")) {
                    grammarPackageName = line.substring(8, line.length() - 1);
                } else if (line.startsWith("header")) {
                    Pattern p = Pattern.compile("header \\{\\s*package\\s+(.+);\\s+}");
                    Matcher m = p.matcher(line);
                    if (m.matches()) {
                        grammarPackageName = m.group(1);
                    }
                }

            }
        } catch (IOException e) {
            throw throwAsUncheckedException(e);
        }
        return grammarPackageName;
    }
}
