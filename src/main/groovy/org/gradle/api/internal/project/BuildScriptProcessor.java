/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.project;

import groovy.lang.Script;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.ImportsScriptSource;
import org.gradle.groovy.scripts.ScriptSource;

/**
 * @author Hans Dockter
 */
public class BuildScriptProcessor {
    ImportsReader importsReader;

    IScriptProcessor scriptProcessor;

    IProjectScriptMetaData projectScriptMetaData;

    public BuildScriptProcessor() {

    }

    public BuildScriptProcessor(IScriptProcessor scriptProcessor, IProjectScriptMetaData projectScriptMetaData,
                                ImportsReader importsReader) {
        this.scriptProcessor = scriptProcessor;
        this.projectScriptMetaData = projectScriptMetaData;
        this.importsReader = importsReader;
    }

    public Script createScript(AbstractProject project) {
        ScriptSource source = new ImportsScriptSource(project.getBuildScriptSource(), importsReader, project.getRootDir());
        Script projectScript = scriptProcessor.createScript(source, project.getBuildScriptClassLoader(), ProjectScript.class);
        projectScriptMetaData.applyMetaData(projectScript, project);
        return projectScript;
    }

    public IProjectScriptMetaData getProjectScriptMetaData() {
        return projectScriptMetaData;
    }

    public IScriptProcessor getScriptProcessor() {
        return scriptProcessor;
    }

    public ImportsReader getImportsReader() {
        return importsReader;
    }
}
