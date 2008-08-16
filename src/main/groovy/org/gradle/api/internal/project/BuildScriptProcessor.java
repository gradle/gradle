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
import org.gradle.CacheUsage;
import org.gradle.api.Project;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.util.GUtil;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class BuildScriptProcessor {
    String inMemoryScriptText;

    ImportsReader importsReader;

    IScriptProcessor scriptProcessor;

    IProjectScriptMetaData projectScriptMetaData;

    public BuildScriptProcessor() {

    }

    public BuildScriptProcessor(IScriptProcessor scriptProcessor, IProjectScriptMetaData projectScriptMetaData,
                                ImportsReader importsReader, String inMemoryScriptText) {
        this.scriptProcessor = scriptProcessor;
        this.projectScriptMetaData = projectScriptMetaData;
        this.importsReader = importsReader;
        this.inMemoryScriptText = inMemoryScriptText;
    }

    public Script createScript(AbstractProject project) {
        Script projectScript;
        if (GUtil.isTrue(inMemoryScriptText)) {
            ScriptSource source = new StringScriptSource("embedded build script", inMemoryScriptText, project.getRootDir(), importsReader);
            projectScript = scriptProcessor.createScript(source, project.getBuildScriptClassLoader(), ProjectScript.class);
        } else {
            ScriptSource source = new FileScriptSource("build file", buildFile(project), importsReader);
            projectScript = scriptProcessor.createScript(source, project.getBuildScriptClassLoader(), ProjectScript.class);
        }
        projectScriptMetaData.applyMetaData(projectScript, project);
        return projectScript;
    }

    private File buildFile(Project project) {
        return new File(project.getProjectDir(), project.getBuildFileName());
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

    public String getInMemoryScriptText() {
        return inMemoryScriptText;
    }
}
