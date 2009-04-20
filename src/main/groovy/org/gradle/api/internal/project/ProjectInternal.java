package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.logging.StandardOutputLogging;
import org.gradle.api.internal.DynamicObject;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.configuration.ProjectEvaluator;
import groovy.lang.Script;

public interface ProjectInternal extends Project, ProjectIdentifier {
    ProjectInternal getParent();

    Project evaluate();

    ScriptSource getBuildScriptSource();

    ClassLoader getBuildScriptClassLoader();

    void addChildProject(ProjectInternal childProject);

    IProjectRegistry<ProjectInternal> getProjectRegistry();

    DynamicObject getInheritedScope();

    void setBuildScript(Script buildScript);

    StandardOutputRedirector getStandardOutputRedirector();
}
