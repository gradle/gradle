package org.gradle.client.build.model;

import org.gradle.declarative.dsl.schema.AnalysisSchema;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public interface ResolvedDomPrerequisites extends Serializable {

    AnalysisSchema getAnalysisSchema();

    File getRootDir();

    List<File> getDeclarativeBuildFiles();
}
