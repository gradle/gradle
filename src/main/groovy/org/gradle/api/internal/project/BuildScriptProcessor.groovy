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

package org.gradle.api.internal.project

import org.gradle.api.internal.project.DefaultProject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * @author Hans Dockter
 */
class BuildScriptProcessor {
    private static Logger logger = LoggerFactory.getLogger(BuildScriptProcessor)

    String inMemoryScriptText

    ScriptHandler scriptCacheHandler = new ScriptHandler()

    ImportsReader importsReader

    boolean useCache

    BuildScriptProcessor() {

    }

    BuildScriptProcessor(ImportsReader importsReader, String inMemoryScriptText, boolean useCache) {
        this.importsReader = importsReader
        this.inMemoryScriptText = inMemoryScriptText
        this.useCache = useCache
    }

    Script createScript(DefaultProject project) {
        File buildFile = buildFile(project)
        String scriptTextForNonCachedExecution = ''
        if (inMemoryScriptText) { scriptTextForNonCachedExecution = inMemoryScriptText }
        else if (!useCache) {
            if (buildFile.isFile()) {
                scriptTextForNonCachedExecution = buildFile.text
            } else {
               return returnEmptyScript()
            }
        }
        if (scriptTextForNonCachedExecution) {
            return scriptCacheHandler.createScript(project, buildScriptWithImports(project, scriptTextForNonCachedExecution))
        }
        if (!buildFile.isFile()) { return returnEmptyScript() }

        Script cachedScript = scriptCacheHandler.loadFromCache(project, buildFile.lastModified())
        if (cachedScript) { return cachedScript }

        scriptCacheHandler.writeToCache(project, buildScriptWithImports(project))
    }

    private File buildFile(DefaultProject project) {
        new File(project.projectDir, project.buildFileName)
    }

    private String buildScriptWithImports(DefaultProject project) {
        buildScriptWithImports(project, buildFile(project).text)
    }

    private String buildScriptWithImports(DefaultProject project, String scriptText) {
        String importsResult = importsReader.getImports(project.rootDir)
        scriptText + System.properties['line.separator'] + importsResult
    }

    private Script returnEmptyScript() {
        logger.info("No build file available. Using empty script!")
            return new EmptyScript()
    }
}

class EmptyScript extends Script {
    Object run() {
        null
    }
}