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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Models information about an Antlr grammar file, including the inidividual {@link #getGrammars grammars} (lexers,
 * parsers, etc) contained within it.
 */
public class GrammarFileMetadata {
    private final File filePath;
    private final antlr.preprocessor.GrammarFile antlrGrammarFile;
    private final String packageName;
    private List<GrammarMetadata> grammarMetadatas = new ArrayList<GrammarMetadata>();

    public GrammarFileMetadata(File filePath, antlr.preprocessor.GrammarFile antlrGrammarFile, String packageName) {
        this.filePath = filePath;
        this.antlrGrammarFile = antlrGrammarFile;
        this.packageName = packageName;

        List<GrammarDelegate> antlrGrammarDelegates = GrammarDelegate.extractGrammarDelegates(antlrGrammarFile);
        for (GrammarDelegate antlrGrammarDelegate : antlrGrammarDelegates) {
            GrammarMetadata grammarMetadata = new GrammarMetadata(this, antlrGrammarDelegate);
            grammarMetadatas.add(grammarMetadata);
        }
    }

    public File getFilePath() {
        return filePath;
    }

    public antlr.preprocessor.GrammarFile getAntlrGrammarFile() {
        return antlrGrammarFile;
    }

    public String getPackageName() {
        return packageName;
    }

    public List<GrammarMetadata> getGrammars() {
        return grammarMetadatas;
    }
}
