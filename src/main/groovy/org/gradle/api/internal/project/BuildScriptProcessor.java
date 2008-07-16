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
import org.gradle.util.GradleUtil;
import org.gradle.util.GUtil;
import org.gradle.api.Project;
import org.gradle.groovy.scripts.IProjectScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class BuildScriptProcessor {
    private static Logger logger = LoggerFactory.getLogger(BuildScriptProcessor.class);

    String inMemoryScriptText;

    ImportsReader importsReader;

    CacheUsage cacheUsage;

    IScriptProcessor scriptProcessor;

    IProjectScriptMetaData projectScriptMetaData;

    public BuildScriptProcessor() {

    }

    public BuildScriptProcessor(IScriptProcessor scriptProcessor, IProjectScriptMetaData projectScriptMetaData,
                                ImportsReader importsReader, String inMemoryScriptText, CacheUsage cacheUsage) {
        this.scriptProcessor = scriptProcessor;
        this.projectScriptMetaData = projectScriptMetaData;
        this.importsReader = importsReader;
        this.inMemoryScriptText = inMemoryScriptText;
        this.cacheUsage = cacheUsage;
    }

    public Script createScript(AbstractProject project) {
        String imports = importsReader.getImports(project.getRootDir());
        Script projectScript;
        if (GUtil.isTrue(inMemoryScriptText)) {
            projectScript = scriptProcessor.createScriptFromText(inMemoryScriptText, imports, project.getBuildFileCacheName(),
                    project.getBuildScriptClassLoader(), ProjectScript.class);
        } else {
            projectScript = scriptProcessor.createScriptFromFile(
                    cacheDir(project),
                    buildFile(project),
                    imports,
                    cacheUsage,
                    project.getBuildScriptClassLoader(),
                    ProjectScript.class);
        }
        projectScriptMetaData.applyMetaData(projectScript, project);
        return projectScript;
    }

    private File buildFile(Project project) {
        return new File(project.getProjectDir(), project.getBuildFileName());
    }

    private File cacheDir(Project project) {
        return new File(project.getProjectDir(), Project.CACHE_DIR_NAME);
    }

    public IProjectScriptMetaData getProjectScriptMetaData() {
        return projectScriptMetaData;
    }

    public IScriptProcessor getScriptProcessor() {
        return scriptProcessor;
    }

    public CacheUsage getCacheUsage() {
        return cacheUsage;
    }

    public ImportsReader getImportsReader() {
        return importsReader;
    }

    public String getInMemoryScriptText() {
        return inMemoryScriptText;
    }
}
