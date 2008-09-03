package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.groovy.scripts.ScriptSource;

public interface ProjectInternal extends Project {
    Project evaluate();

    BuildScriptProcessor getBuildScriptProcessor();

    ScriptSource getBuildScriptSource();

    ClassLoader getBuildScriptClassLoader();

    Project addChildProject(String name);

    void setGradleUserHome(String gradleUserHome);
}
