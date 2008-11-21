package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;
import java.util.Map;

public interface ProjectInternal extends Project {
    ProjectInternal getParent();

    Project evaluate();

    BuildScriptProcessor getBuildScriptProcessor();

    ScriptSource getBuildScriptSource();

    ClassLoader getBuildScriptClassLoader();

    Project addChildProject(String name, File projectDir);

    void setGradleUserHome(String gradleUserHome);

    IProjectRegistry getProjectRegistry();

    void setBuildDirName(String buildDirName);

    Map<String, ?> getAdditionalProperties();
}
