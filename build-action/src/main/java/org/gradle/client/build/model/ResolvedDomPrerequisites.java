package org.gradle.client.build.model;

import org.gradle.declarative.dsl.evaluation.InterpretationSequence;
import org.gradle.declarative.dsl.schema.AnalysisSchema;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public interface ResolvedDomPrerequisites extends Serializable {
    
    InterpretationSequence getSettingsInterpretationSequence();
    
    InterpretationSequence getProjectInterpretationSequence();

    AnalysisSchema getAnalysisSchema();

    File getRootDir();
    
    File getSettingsFile();

    List<File> getDeclarativeBuildFiles();
}
