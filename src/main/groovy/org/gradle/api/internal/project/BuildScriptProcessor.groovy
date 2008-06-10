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

import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.api.GradleScriptException
import org.gradle.api.internal.project.DefaultProject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.gradle.util.Clock

/**
 * @author Hans Dockter
 */
class BuildScriptProcessor {
    private static Logger logger = LoggerFactory.getLogger(BuildScriptProcessor)

    ClassLoader classLoader

    ImportsReader importsReader

    BuildScriptProcessor() {

    }

    BuildScriptProcessor(ImportsReader importsReader) {
        this.importsReader = importsReader
    }

    void evaluate(DefaultProject project, Map bindingVariables = [:]) {
        Binding binding = new Binding(bindingVariables)
        CompilerConfiguration conf = new CompilerConfiguration()
        conf.scriptBaseClass = 'org.gradle.api.internal.project.ProjectScript'
        try {
            String buildScript = buildScriptWithImports(project)
            logger.debug("Evaluated Build Script: " + buildScript)
            GroovyShell groovyShell = new GroovyShell(classLoader, binding, conf)
            Clock clock = new Clock()
            Script script = groovyShell.parse(buildScript, project.buildScriptFinder.buildFileName)
            logger.debug("Timing: Parsing the build script took " + clock.time)
            replaceMetaclass(script, project)
            project.projectScript = script
            clock.reset()
            script.run()
            logger.debug("Timing: Running the build script took " + clock.time)
        } catch (Throwable t) {
            throw new GradleScriptException(t, project.buildScriptFinder.buildFileName)
        }
        project.additionalProperties.putAll(binding.variables)
    }

    private void replaceMetaclass(Script script, DefaultProject project) {
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
    }

    private String buildScriptWithImports(DefaultProject project) {
        String importsResult = importsReader.getImports(project.rootDir)
        project.buildScriptFinder.getBuildScript(project) + System.properties['line.separator'] + importsResult
    }
}