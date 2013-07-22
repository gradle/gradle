/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins.antlr.internal;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTree;

import java.io.*;

/**
 * Preprocess an Antlr grammar file so that dependencies between grammars can be properly determined such that they can
 * be processed for generation in proper order later.
 */
public class MetadataExtracter {

    public XRef extractMetadata(FileTree source) {
        antlr.Tool tool = new antlr.Tool();
        antlr.preprocessor.Hierarchy hierarchy = new antlr.preprocessor.Hierarchy(tool);

        // first let antlr preprocess the grammars...
        for (File grammarFileFile : source.getFiles()) {
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
        for (File grammarFileFile : source.getFiles()) {

            // determine the package name :(
            String grammarPackageName = null;
            try {
                BufferedReader in = new BufferedReader(new FileReader(grammarFileFile));
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("package") && line.endsWith(";")) {
                            grammarPackageName = line.substring(8, line.length() - 1);
                            break;
                        }
                    }
                } finally {
                    try {
                        in.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            final String grammarFilePath = grammarFileFile.getPath();
            antlr.preprocessor.GrammarFile antlrGrammarFile = hierarchy.getFile(grammarFilePath);

            GrammarFileMetadata grammarFileMetadata = new GrammarFileMetadata(grammarFileFile, antlrGrammarFile,
                    grammarPackageName);

            xref.addGrammarFile(grammarFileMetadata);
        }

        return xref;
    }
}
