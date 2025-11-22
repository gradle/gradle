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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.io.FileUtils.readLines;

/**
 * Preprocess an Antlr grammar file so that dependencies between grammars can be properly determined such that they can
 * be processed for generation in proper order later.
 */
public final class MetadataExtracter {

    private static final String HEADER = "header";
    private static final String PACKAGE = "package";
    private static final Pattern PACKAGE_PATTERN = compile(HEADER + " \\{\\s*" + PACKAGE + "\\s+(.+);\\s+}");

    public static XRef extractMetadata(Set<File> files) {
        try {
            Hierarchy hierarchy = new Hierarchy(new antlr.Tool());
            for (File grammarFileFile : files) { // let antlr preprocess the grammars. // todo: why can not extract this into dedicated method?
                try {
                    hierarchy.readGrammarFile(grammarFileFile.getPath());
                } catch (FileNotFoundException ignored) {
                    // should never happen
                }
            }
            XRef xref = new XRef(hierarchy);
            for (File grammarFileFile : files) { // process using the antlr preprocessor, results whenever possible. // todo: why can not extract this into dedicated method?
                xref.addGrammarFile(
                    new GrammarFileMetadata(
                        grammarFileFile,
                        hierarchy.getFile(grammarFileFile.getPath()),
                        getNormalizedPackageName(readLines(grammarFileFile, UTF_8).stream())));
            }
            return xref;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read antlr grammar file", e);
        }
    }

    static String getNormalizedPackageName(Stream<String> lines) {
        return lines
            .map(String::trim)
            .map(MetadataExtracter::extractPackageOrHeader)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private static String extractPackageOrHeader(String line) {
        if (line.startsWith(PACKAGE) && line.endsWith(";")) {
            return line.substring(PACKAGE.length() + 1, line.length() - 1).trim();
        }
        if (line.startsWith(HEADER)) {
            Matcher m = PACKAGE_PATTERN.matcher(line);
            if (m.matches()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

}
