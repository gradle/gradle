package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.api.internal.DynamicObject;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.initialization.ScriptClassLoaderProvider;
import org.gradle.groovy.scripts.ScriptSource;
import groovy.lang.Script;

public interface ProjectInternal extends Project, ProjectIdentifier {
    ProjectInternal getParent();

    Project evaluate();

    ScriptSource getBuildScriptSource();

    void addChildProject(ProjectInternal childProject);

    IProjectRegistry<ProjectInternal> getProjectRegistry();

    DynamicObject getInheritedScope();

    void setScript(Script buildScript);

    Script getScript();

    StandardOutputRedirector getStandardOutputRedirector();

    BuildInternal getBuild();

    ScriptClassLoaderProvider getClassLoaderProvider();
}
