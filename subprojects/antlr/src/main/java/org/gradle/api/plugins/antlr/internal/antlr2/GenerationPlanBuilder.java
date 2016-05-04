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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Builder for the properly order list of {@link GenerationPlan generation plans}.
 *
 * <p>IMPL NOTE : Uses recursive calls to achieve ordering.</p>
 */
public class GenerationPlanBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerationPlanBuilder.class);

    private final LinkedHashMap<String, GenerationPlan> generationPlans = new LinkedHashMap<String, GenerationPlan>();
    private final File outputDirectory;

    private XRef metadataXRef;

    public GenerationPlanBuilder(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public synchronized List<GenerationPlan> buildGenerationPlans(XRef metadataXRef) {
        this.metadataXRef = metadataXRef;

        Iterator<GrammarFileMetadata> grammarFiles = metadataXRef.iterateGrammarFiles();
        while (grammarFiles.hasNext()) {
            final GrammarFileMetadata grammarFileMetadata = grammarFiles.next();
            // NOTE : locateOrBuildGenerationPlan populates the generationPlans map
            locateOrBuildGenerationPlan(grammarFileMetadata);
        }

        return new ArrayList<GenerationPlan>(generationPlans.values());
    }

    private GenerationPlan locateOrBuildGenerationPlan(GrammarFileMetadata grammarFileMetadata) {
        GenerationPlan generationPlan = generationPlans.get(grammarFileMetadata.getFilePath().getPath());
        if (generationPlan == null) {
            generationPlan = buildGenerationPlan(grammarFileMetadata);
        }
        return generationPlan;
    }

    private GenerationPlan buildGenerationPlan(GrammarFileMetadata grammarFileMetadata) {
        File generationDirectory = isEmpty(grammarFileMetadata.getPackageName()) ? outputDirectory : new File(
                outputDirectory, grammarFileMetadata.getPackageName().replace('.', File.separatorChar));

        GenerationPlan generationPlan = new GenerationPlan(grammarFileMetadata.getFilePath(), generationDirectory);

        for (GrammarMetadata grammarMetadata : grammarFileMetadata.getGrammars()) {
            final File generatedParserFile = new File(outputDirectory, grammarMetadata.determineGeneratedParserPath());

            if (!generatedParserFile.exists()) {
                generationPlan.markOutOfDate();
            } else if (generatedParserFile.lastModified() < generationPlan.getSource().lastModified()) {
                generationPlan.markOutOfDate();
            }

            // see if the grammar if out-of-date by way of its super-grammar(s) as gleaned from parsing the grammar file
            if (!grammarMetadata.extendsStandardGrammar()) {
                final GrammarFileMetadata superGrammarGrammarFileMetadata = grammarMetadata.getSuperGrammarDelegate()
                        .getAssociatedGrammarMetadata().getGrammarFile();
                if (superGrammarGrammarFileMetadata != null) {
                    final GenerationPlan superGrammarGenerationPlan = locateOrBuildGenerationPlan(
                            superGrammarGrammarFileMetadata);
                    if (superGrammarGenerationPlan.isOutOfDate()) {
                        generationPlan.markOutOfDate();
                    } else if (superGrammarGenerationPlan.getSource().lastModified() > generatedParserFile
                            .lastModified()) {
                        generationPlan.markOutOfDate();
                    }
                }
            }

            // see if the grammar if out-of-date by way of its importVocab
            if (isNotEmpty(grammarMetadata.getImportVocab())) {
                final GrammarFileMetadata importVocabGrammarFileMetadata = metadataXRef.getGrammarFileByExportVocab(
                        grammarMetadata.getImportVocab());
                if (importVocabGrammarFileMetadata == null) {
                    LOGGER.warn("unable to locate grammar exporting specified import vocab ["
                            + grammarMetadata.getImportVocab() + "]");
                } else if (!importVocabGrammarFileMetadata.getFilePath().equals(grammarFileMetadata.getFilePath())) {
                    final GenerationPlan importVocabGrammarGenerationPlan = locateOrBuildGenerationPlan(
                            importVocabGrammarFileMetadata);
                    generationPlan.setImportVocabTokenTypesDirectory(
                            importVocabGrammarGenerationPlan.getGenerationDirectory());
                    if (importVocabGrammarGenerationPlan.isOutOfDate()) {
                        generationPlan.markOutOfDate();
                    } else if (importVocabGrammarGenerationPlan.getSource().lastModified() > generatedParserFile
                            .lastModified()) {
                        generationPlan.markOutOfDate();
                    }
                }
            }
        }

        generationPlans.put(generationPlan.getId(), generationPlan);
        return generationPlan;
    }

    private boolean isEmpty(String string) {
        return string == null || string.trim().length() == 0;
    }

    private boolean isNotEmpty(String string) {
        return !isEmpty(string);
    }
}
