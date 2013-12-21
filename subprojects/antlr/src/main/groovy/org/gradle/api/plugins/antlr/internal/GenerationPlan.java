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

import java.io.File;

/**
 * Models information relevant to generation of a particular Antlr grammar file.
 */
public class GenerationPlan {
    private final File source;
    private final File generationDirectory;

    private File importVocabTokenTypesDirectory;
    private boolean outOfDate;

    /**
     * Instantiates a generation plan.
     *
     * @param source The grammar file.
     * @param generationDirectory The directory into which generated lexers and parsers should be written, accounting for
     * declared package.
     */
    GenerationPlan(File source, File generationDirectory) {
        this.source = source;
        this.generationDirectory = generationDirectory;
    }

    public String getId() {
        return getSource().getPath();
    }

    public File getSource() {
        return source;
    }

    public File getGenerationDirectory() {
        return generationDirectory;
    }

    public File getImportVocabTokenTypesDirectory() {
        return importVocabTokenTypesDirectory;
    }

    void setImportVocabTokenTypesDirectory(File importVocabTokenTypesDirectory) {
        this.importVocabTokenTypesDirectory = importVocabTokenTypesDirectory;
    }

    /**
     * Is the grammar file modeled by this plan out of considered out of date?
     *
     * @return True if the grammar generation is out of date (needs regen); false otherwise.
     */
    public boolean isOutOfDate() {
        return outOfDate;
    }

    /**
     * Marks the plan as out of date.
     */
    void markOutOfDate() {
        this.outOfDate = true;
    }
}
