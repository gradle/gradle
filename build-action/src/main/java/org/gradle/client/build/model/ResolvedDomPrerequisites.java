package org.gradle.client.build.model;

import org.gradle.declarative.dsl.schema.AnalysisSchema;

import java.io.Serializable;

public interface ResolvedDomPrerequisites extends Serializable {

    AnalysisSchema getAnalysisSchema();

    String getBuildFilePath();

}
