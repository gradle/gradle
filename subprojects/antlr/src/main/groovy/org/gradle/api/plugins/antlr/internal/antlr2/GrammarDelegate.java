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

import antlr.collections.impl.IndexedVector;
import antlr.preprocessor.GrammarFile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Antlr defines its {@link antlr.preprocessor.Grammar} class as package-protected for some unfortunate reason. So this class acts as a delegate to the Antlr {@link antlr.preprocessor.Grammar} class,
 * hiding all the ugly necessary reflection code.
 */
public class GrammarDelegate {
    public static List<GrammarDelegate> extractGrammarDelegates(GrammarFile antlrGrammarFile) {
        List<GrammarDelegate> grammarDelegates = new ArrayList<GrammarDelegate>();
        Enumeration grammarFileGramars = antlrGrammarFile.getGrammars().elements();
        while (grammarFileGramars.hasMoreElements()) {
            grammarDelegates.add(new GrammarDelegate(grammarFileGramars.nextElement()));
        }
        return grammarDelegates;
    }

    private final String className;
    private final String importVocab;
    private final String exportVocab;
    private final GrammarDelegate superGrammarDelegate;

    public GrammarDelegate(Object antlrGrammarMetadata) {
        try {
            final Method getNameMethod = ANTLR_GRAMMAR_CLASS.getDeclaredMethod("getName", NO_ARG_SIGNATURE);
            getNameMethod.setAccessible(true);
            this.className = (String) getNameMethod.invoke(antlrGrammarMetadata, NO_ARGS);

            final Method getSuperGrammarMethod = ANTLR_GRAMMAR_CLASS.getMethod("getSuperGrammar", NO_ARG_SIGNATURE);
            getSuperGrammarMethod.setAccessible(true);
            final Object antlrSuperGrammarGrammarMetadata = getSuperGrammarMethod.invoke(antlrGrammarMetadata, NO_ARGS);
            this.superGrammarDelegate = antlrSuperGrammarGrammarMetadata == null ? null : new GrammarDelegate(antlrSuperGrammarGrammarMetadata);

            Method getOptionsMethod = ANTLR_GRAMMAR_CLASS.getMethod("getOptions", NO_ARG_SIGNATURE);
            getOptionsMethod.setAccessible(true);
            IndexedVector options = (IndexedVector) getOptionsMethod.invoke(antlrGrammarMetadata, NO_ARGS);

            Method getRHSMethod = ANTLR_OPTION_CLASS.getMethod("getRHS", NO_ARG_SIGNATURE);
            getRHSMethod.setAccessible(true);

            final Object importVocabOption = options == null ? null : options.getElement("importVocab");
            this.importVocab = importVocabOption == null ? null : vocabName((String) getRHSMethod.invoke(importVocabOption, NO_ARGS));

            final Object exportVocabOption = options == null ? null : options.getElement("exportVocab");
            this.exportVocab = exportVocabOption == null ? null : vocabName((String) getRHSMethod.invoke(exportVocabOption, NO_ARGS));
        } catch (Throwable t) {
            throw new IllegalStateException("Error accessing  Antlr grammar metadata", t);
        }
    }

    /**
     * Retrieves the unqualified name of the lexer/parser class.
     *
     * @return The unqualified lexer/parser class name.
     */
    public String getClassName() {
        return className;
    }

    /**
     * Retrieves the name of this vocabulary imported by this grammar.
     *
     * @return The gammar's imported vocabulary name.
     */
    public String getImportVocab() {
        return importVocab;
    }

    /**
     * Retrieves the name of this vocabulary exported by this grammar.
     *
     * @return The gammar's exported vocabulary name.
     */
    public String getExportVocab() {
        return exportVocab;
    }

    /**
     * Retrieves the grammar delegate associated with this grammars super grammar deduced during preprocessing from its extends clause.
     *
     * @return The super-grammar grammar delegate
     */
    public GrammarDelegate getSuperGrammarDelegate() {
        return superGrammarDelegate;
    }

    private GrammarMetadata associatedGrammarMetadata;

    public void associateWith(GrammarMetadata associatedGrammarMetadata) {
        this.associatedGrammarMetadata = associatedGrammarMetadata;
    }

    public GrammarMetadata getAssociatedGrammarMetadata() {
        return associatedGrammarMetadata;
    }

    private String vocabName(String vocabName) {
        if (vocabName == null) {
            return null;
        }
        vocabName = vocabName.trim();
        if (vocabName.endsWith(";")) {
            vocabName = vocabName.substring(0, vocabName.length() - 1);
        }
        return vocabName;
    }

    private static final Class ANTLR_GRAMMAR_CLASS;
    private static final Class ANTLR_OPTION_CLASS;

    static {
        ANTLR_GRAMMAR_CLASS = loadAntlrClass("antlr.preprocessor.Grammar");
        ANTLR_OPTION_CLASS = loadAntlrClass("antlr.preprocessor.Option");
    }

    public static final Class[] NO_ARG_SIGNATURE = new Class[0];
    public static final Object[] NO_ARGS = new Object[0];

    private static Class loadAntlrClass(String className) {
        try {
            return Class.forName(className, true, GrammarDelegate.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to locate Antlr class [" + className + "]", e);
        }
    }
}
