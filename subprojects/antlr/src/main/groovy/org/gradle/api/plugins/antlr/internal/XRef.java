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

import antlr.preprocessor.Hierarchy;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * Models cross-reference (x-ref) info about {@link GrammarFileMetadata grammar files} such as {@link #filesByPath},
 * {@link #filesByExportVocab} and {@link #filesByClassName}.
 */
public class XRef {
    private final Hierarchy antlrHierarchy;

    private LinkedHashMap<String, GrammarFileMetadata> filesByPath = new LinkedHashMap<String, GrammarFileMetadata>();
    private HashMap<String, GrammarFileMetadata> filesByExportVocab = new HashMap<String, GrammarFileMetadata>();
    private HashMap<String, GrammarFileMetadata> filesByClassName = new HashMap<String, GrammarFileMetadata>();

    public XRef(Hierarchy antlrHierarchy) {
        this.antlrHierarchy = antlrHierarchy;
    }

    public Object getAntlrHierarchy() {
        return antlrHierarchy;
    }

    /**
     * Adds a grammar file to this cross-reference.
     *
     * @param grammarFileMetadata The grammar file to add (and to be cross referenced).
     */
    void addGrammarFile(GrammarFileMetadata grammarFileMetadata) {
        filesByPath.put(grammarFileMetadata.getFilePath().getPath(), grammarFileMetadata);
        for (GrammarMetadata grammarMetadata : grammarFileMetadata.getGrammars()) {
            filesByClassName.put(grammarMetadata.getClassName(), grammarFileMetadata);
            if (grammarMetadata.getExportVocab() != null) {
                GrammarFileMetadata old = filesByExportVocab.put(grammarMetadata.getExportVocab(), grammarFileMetadata);
                if (old != null && old != grammarFileMetadata) {
                    System.out.println("[WARNING] : multiple grammars defined the same exportVocab : " + grammarMetadata
                            .getExportVocab());
                }
            }
        }
    }

    public Iterator<GrammarFileMetadata> iterateGrammarFiles() {
        return filesByPath.values().iterator();
    }

    /**
     * Locate the grammar file metadata by grammar file path.
     *
     * @param path The grammar file path.
     * @return The grammar file metadata.  May be null if none found.
     */
    public GrammarFileMetadata getGrammarFileByPath(String path) {
        return filesByPath.get(path);
    }

    /**
     * Locate the grammar file metadata by the name of a class generated from one of its included grammars.
     *
     * @param className The generated class name.
     * @return The grammar file metadata.  May be null if none found.
     */
    public GrammarFileMetadata getGrammarFileByClassName(String className) {
        return filesByClassName.get(className);
    }

    /**
     * Locate the grammar file metadata by the name of a vocabulary exported from one of its included grammars.
     *
     * @param vocabName The vocabulary name
     * @return The grammar file metadata.  May be null if none found.
     */
    public GrammarFileMetadata getGrammarFileByExportVocab(String vocabName) {
        return filesByExportVocab.get(vocabName);
    }
}
