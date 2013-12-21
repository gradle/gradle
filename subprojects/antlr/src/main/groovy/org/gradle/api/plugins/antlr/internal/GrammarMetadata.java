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

import antlr.Parser;
import antlr.TreeParser;

import java.io.File;

/**
 * Models a grammar defined within an Antlr grammar file.
 */
public class GrammarMetadata {
    private final GrammarFileMetadata grammarFileMetadata;
    private final GrammarDelegate grammarDelegate;

    public GrammarMetadata(GrammarFileMetadata grammarFileMetadata, GrammarDelegate grammarDelegate) {
        this.grammarFileMetadata = grammarFileMetadata;
        this.grammarDelegate = grammarDelegate;
        grammarDelegate.associateWith(this);
    }

    public GrammarFileMetadata getGrammarFile() {
        return grammarFileMetadata;
    }

    public String getClassName() {
        return grammarDelegate.getClassName();
    }

    public String getQualifiedClassName() {
        if (isEmpty(getPackageName())) {
            return getClassName();
        } else {
            return getPackageName() + '.' + getClassName();
        }
    }

    public GrammarDelegate getSuperGrammarDelegate() {
        return grammarDelegate.getSuperGrammarDelegate();
    }

    public boolean extendsStandardGrammar() {
        final String superGrammarClassName = getSuperGrammarDelegate().getClassName();
        return Parser.class.getName().equals(superGrammarClassName) || Parser.class.getSimpleName().equals(
                superGrammarClassName) || TreeParser.class.getName().equals(superGrammarClassName)
                || TreeParser.class.getSimpleName().equals(superGrammarClassName) || "Lexer".equals(
                superGrammarClassName);
    }

    public String getImportVocab() {
        return grammarDelegate.getImportVocab();
    }

    public String getExportVocab() {
        return grammarDelegate.getExportVocab();
    }

    public String getPackageName() {
        return getGrammarFile().getPackageName();
    }

    /**
     * Determine the relative path of the generated parser java file.
     *
     * @return The relative generated parser file path.
     */
    public String determineGeneratedParserPath() {
        if (isEmpty(getPackageName())) {
            return getClassName() + ".java";
        } else {
            return getPackageName().replace('.', File.separatorChar) + File.separatorChar + getClassName() + ".java";
        }
    }

    private boolean isEmpty(String packageName) {
        return packageName == null || packageName.trim().length() == 0;
    }
}
