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

package org.gradle.api.internal.project

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import org.gradle.util.Clock
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

/**
 * @author Hans Dockter
 */
class DefaultScriptHandler implements ScriptHandler {
    static Logger logger = LoggerFactory.getLogger(DefaultScriptHandler)

    static final String GRADLE_DIR_NAME = '.gradle'

    Script createScript(Project project, String scriptText) {
        logger.debug("Parsing Script:\n$scriptText")
        Clock clock = new Clock()
        CompilerConfiguration configuration = createBaseCompilerConfiguration()
        GroovyShell groovyShell = new GroovyShell(project.buildScriptClassLoader, new Binding(), configuration)
        Script script = groovyShell.parse(scriptText, project.buildFileName)
        replaceMetaclass(script, project)
        logger.debug("Timing: Creating script took: " + clock.time)
        script
    }

    Script writeToCache(Project project, String scriptText) {
        Clock clock = new Clock()
        String buildFileCacheName = buildFileCacheName(project)
        File cacheDir = cacheDir(project, buildFileCacheName)
        FileUtils.deleteDirectory(cacheDir) 
        cacheDir.mkdirs()
        CompilerConfiguration configuration = createBaseCompilerConfiguration()
        configuration.setTargetDirectory(cacheDir)
        def unit = new CompilationUnit(configuration, null, new GroovyClassLoader(project.buildScriptClassLoader))
        unit.addSource(buildFileCacheName, new ByteArrayInputStream(scriptText.bytes))
        unit.compile()
        logger.info("Timing: Writing script to cache at $cacheDir.absolutePath took: " + clock.time)
        loadFromCache(project, 0)
    }

    CompilerConfiguration createBaseCompilerConfiguration() {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.scriptBaseClass = 'org.gradle.api.internal.project.ProjectScript'
        configuration
    }

    Script loadFromCache(Project project, long lastModified) {
        String buildFileCacheName = buildFileCacheName(project)
        if (cacheFile(project, buildFileCacheName).lastModified() < lastModified) {
            return null
        }
        Clock clock = new Clock()
        URLClassLoader urlClassLoader = new URLClassLoader([cacheDir(project, buildFileCacheName).toURI().toURL()] as URL[],
                project.buildScriptClassLoader)
        Script script
        try {
            script = urlClassLoader.loadClass(buildFileCacheName).newInstance()
        } catch (ClassNotFoundException e) {
            return null
        }
        replaceMetaclass(script, project)
        logger.info("Timing: Loading script from cache took: " + clock.time)
        script
    }

    private File cacheDir(Project project, String buildCacheFileName) {
        new File(project.projectDir, "$GRADLE_DIR_NAME/$buildCacheFileName")
    }

    private File cacheFile(Project project, String buildCacheFileName) {
        new File(cacheDir(project, buildCacheFileName), "${buildCacheFileName}.class")
    }

    private String buildFileCacheName(Project project) {
        project.getBuildFileCacheName();
    }

    private Script replaceMetaclass(Script script, Project project) {
        ExpandoMetaClass projectScriptExpandoMetaclass = new ExpandoMetaClass(script.class, false)
        projectScriptExpandoMetaclass.methodMissing = {String name, args ->
            logger.debug("Project: $project.path Method $name not found in script! Delegating to project.")
            project.invokeMethod(name, args)
        }
        projectScriptExpandoMetaclass.propertyMissing = {String name ->
            if (name == 'out') {
                return System.out
            }
            logger.debug("Project: $project.path Property $name not found in script! Delegating to project.")
            project."$name"
        }
        projectScriptExpandoMetaclass.setProperty = {String name, value ->
            logger.debug("Project: $project.path Property $name set a project property.")
            project."$name" = value
        }
        projectScriptExpandoMetaclass.initialize()
        script.metaClass = projectScriptExpandoMetaclass
        script
    }
}
