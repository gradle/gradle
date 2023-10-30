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

import org.gradle.api.UncheckedIOException;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Preprocess an Antlr grammar file so that dependencies between grammars can be properly determined such that they can
 * be processed for generation in proper order later.
 */
public class MetadataExtracter {

    public XRef extractMetadata(Set<File> sources) {
        antlr.Tool tool = new antlr.Tool();
        antlr.preprocessor.Hierarchy hierarchy = new antlr.preprocessor.Hierarchy(tool);

        // first let antlr preprocess the grammars...
        for (File grammarFileFile : sources) {
            final String grammarFilePath = grammarFileFile.getPath();

            try {
                hierarchy.readGrammarFile(grammarFilePath);
            } catch (FileNotFoundException e) {
                // should never happen here
                throw new IllegalStateException("Received FileNotFoundException on already read file", e);
            }
        }

        // now, do our processing using the antlr preprocessor results whenever possible.
        XRef xref = new XRef(hierarchy);
        for (File grammarFileFile : sources) {

            // determine the package name :(
            String grammarPackageName = getPackageName(grammarFileFile);

            final String grammarFilePath = grammarFileFile.getPath();
            antlr.preprocessor.GrammarFile antlrGrammarFile = hierarchy.getFile(grammarFilePath);

            GrammarFileMetadata grammarFileMetadata = new GrammarFileMetadata(grammarFileFile, antlrGrammarFile,
                    grammarPackageName);

            xref.addGrammarFile(grammarFileMetadata);
        }

        return xref;
    }

    @Nullable
    private String getPackageName(File grammarFileFile) {
        try {
            return getPackageName(new FileReader(grammarFileFile));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read antlr grammar file", e);
        }
    }

    String getPackageName(Reader reader) throws IOException {
        String grammarPackageName = null;
        BufferedReader in = new BufferedReader(reader);
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("package") && line.endsWith(";")) {
                    grammarPackageName =  line.substring(8, line.length() - 1);
                }else if(line.startsWith("header")){
                    Pattern p = Pattern.compile("header \\{\\s*package\\s+(.+);\\s+\\}");
                    Matcher m = p.matcher(line);
                    if(m.matches()){
                        grammarPackageName = m.group(1);
                    }
                }

            }
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return grammarPackageName;
    }
}
