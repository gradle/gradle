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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.Project;
import org.gradle.util.GUtil;
import org.gradle.CacheUsage;
import org.apache.commons.io.FileUtils;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class BuildScriptProcessor {
    private static Logger logger = LoggerFactory.getLogger(BuildScriptProcessor.class);

    String inMemoryScriptText;

    ScriptHandler scriptHandler = new DefaultScriptHandler();

    ImportsReader importsReader;

    CacheUsage cacheUsage;

    public BuildScriptProcessor() {

    }

    public BuildScriptProcessor(ImportsReader importsReader, String inMemoryScriptText, CacheUsage cacheUsage) {
        this.importsReader = importsReader;
        this.inMemoryScriptText = inMemoryScriptText;
        this.cacheUsage = cacheUsage;
    }

    public Script createScript(Project project) {
        File buildFile = buildFile(project);
        String scriptTextForNonCachedExecution = "";
        if (GUtil.isTrue(inMemoryScriptText)) {
            scriptTextForNonCachedExecution = inMemoryScriptText;
        } else if (cacheUsage == CacheUsage.OFF) {
            if (buildFile.isFile()) {
                try {
                    scriptTextForNonCachedExecution = FileUtils.readFileToString(buildFile);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return returnEmptyScript();
            }
        }
        if (GUtil.isTrue(scriptTextForNonCachedExecution)) {
            return scriptHandler.createScript(project, buildScriptWithImports(project, scriptTextForNonCachedExecution));
        }
        if (!buildFile.isFile()) {
            return returnEmptyScript();
        }

        if (cacheUsage == CacheUsage.ON) {
            Script cachedScript = scriptHandler.loadFromCache(project, buildFile.lastModified());
            if (cachedScript != null) {
                return cachedScript;
            }
        }
        return scriptHandler.writeToCache(project, buildScriptWithImports(project));
    }

    private File buildFile(Project project) {
        return new File(project.getProjectDir(), project.getBuildFileName());
    }

    private String buildScriptWithImports(Project project) {
        try {
            return buildScriptWithImports(project, FileUtils.readFileToString(buildFile(project)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String buildScriptWithImports(Project project, String scriptText) {
        String importsResult = importsReader.getImports(project.getRootDir());
        return scriptText + System.getProperty("line.separator") + importsResult;
    }

    private Script returnEmptyScript() {
        logger.info("No build file available. Using empty script!");
        return new EmptyScript();
    }
}
