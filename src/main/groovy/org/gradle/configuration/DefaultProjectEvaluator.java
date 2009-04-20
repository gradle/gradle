/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectScript;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.GradleScriptException;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.invocation.Build;
import org.gradle.api.initialization.Settings;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.ImportsScriptSource;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.BuildListener;
import org.gradle.StartParameter;
import org.gradle.BuildResult;
import groovy.lang.Script;

public class DefaultProjectEvaluator implements ProjectEvaluator, BuildListener {
    private final ImportsReader importsReader;
    private final IScriptProcessor scriptProcessor;
    private final IProjectScriptMetaData projectScriptMetaData;
    private ProjectEvaluationListener listener;

    public DefaultProjectEvaluator(ImportsReader importsReader, IScriptProcessor scriptProcessor,
                                   IProjectScriptMetaData projectScriptMetaData) {
        this.importsReader = importsReader;
        this.scriptProcessor = scriptProcessor;
        this.projectScriptMetaData = projectScriptMetaData;
    }

    public void evaluate(ProjectInternal project) {
        listener.beforeEvaluate(project);
        try {
            ScriptSource source = new ImportsScriptSource(project.getBuildScriptSource(), importsReader, project.getRootDir());
            Script buildScript = scriptProcessor.createScript(source, project.getBuildScriptClassLoader(), ProjectScript.class);
            projectScriptMetaData.applyMetaData(buildScript, project);

            project.setBuildScript(buildScript);
            project.getStandardOutputRedirector().on(LogLevel.QUIET);
            try {
                buildScript.run();
            } finally {
                project.getStandardOutputRedirector().flush();
            }
        } catch (Throwable t) {
            throw new GradleScriptException(String.format("A problem occurred evaluating %s.", project), t,
                    project.getBuildScriptSource());
        }
        listener.afterEvaluate(project);
    }

    public void buildStarted(StartParameter startParameter) {
    }

    public void settingsEvaluated(Settings settings) {
    }

    public void projectsLoaded(Build build) {
        listener = ((BuildInternal) build).getProjectEvaluationBroadcaster();
    }

    public void projectsEvaluated(Build build) {
    }

    public void taskGraphPopulated(TaskExecutionGraph graph) {
    }

    public void buildFinished(BuildResult result) {
    }
}
