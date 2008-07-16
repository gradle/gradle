/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.groovy.scripts;

import groovy.lang.Script;
import org.gradle.groovy.scripts.EmptyScript;
import org.gradle.util.GFileUtils;
import org.gradle.util.ConfigureUtil;
import org.gradle.CacheUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultScriptProcessor implements IScriptProcessor {
    private static Logger logger = LoggerFactory.getLogger(DefaultScriptProcessor.class);

    private IScriptHandler scriptHandler;

    public DefaultScriptProcessor(IScriptHandler scriptHandler) {
        this.scriptHandler = scriptHandler;
    }

    public Script createScriptFromFile(File cacheDir, File buildFile, String scriptAttachement,
                               CacheUsage cacheUsage, ClassLoader classLoader, Class scriptBaseClass) {
        if (buildFile == null || !buildFile.isFile()) {
            return returnEmptyScript();
        }
        String cacheFileName = ConfigureUtil.dot2underscore(buildFile.getName());
        if (cacheUsage == CacheUsage.OFF) {
            return scriptHandler.createScript(concatenate(GFileUtils.readFileToString(buildFile), scriptAttachement), classLoader,
                    cacheFileName, scriptBaseClass);
        }

        if (cacheUsage == CacheUsage.ON) {
            Script cachedScript = scriptHandler.loadFromCache(buildFile.lastModified(), classLoader, cacheFileName, cacheDir);
            if (cachedScript != null) {
                return cachedScript;
            }
        }
        return scriptHandler.writeToCache(
                concatenate(GFileUtils.readFileToString(buildFile), scriptAttachement), 
                classLoader,
                cacheFileName,
                cacheDir,
                scriptBaseClass);
    }

    public Script createScriptFromText(String scriptText, String scriptAttachement,
                               String scriptName, ClassLoader classLoader, Class scriptBaseClass) {
        return scriptHandler.createScript(concatenate(scriptText, scriptAttachement), classLoader,
                    scriptName, scriptBaseClass);
    }

    private Script returnEmptyScript() {
        logger.info("No build file available. Using empty script!");
        return new EmptyScript();
    }

    public IScriptHandler getScriptHandler() {
        return scriptHandler;
    }

    public void setScriptHandler(IScriptHandler scriptHandler) {
        this.scriptHandler = scriptHandler;
    }

    private String concatenate(String part1, String part2) {
        return part1 + System.getProperty("line.separator") + part2; 
    }

}
