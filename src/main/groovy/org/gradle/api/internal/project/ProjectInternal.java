package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;

public interface ProjectInternal extends Project {
    Project evaluate();

    BuildScriptProcessor getBuildScriptProcessor();

    ScriptSource getBuildScriptSource();

    ClassLoader getBuildScriptClassLoader();

    Project addChildProject(String name, File projectDir);

    void setGradleUserHome(String gradleUserHome);

    IProjectRegistry getProjectRegistry();

    void setBuildDirName(String buildDirName);
}
