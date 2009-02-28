package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.internal.DynamicObject;
import org.gradle.groovy.scripts.ScriptSource;

public interface ProjectInternal extends Project, ProjectIdentifier {
    ProjectInternal getParent();

    Project evaluate();

    BuildScriptProcessor getBuildScriptProcessor();

    ScriptSource getBuildScriptSource();

    ClassLoader getBuildScriptClassLoader();

    void addChildProject(ProjectInternal childProject);

    IProjectRegistry<ProjectInternal> getProjectRegistry();

    DynamicObject getInheritedScope();
}
