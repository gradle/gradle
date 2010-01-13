/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.groovy.scripts

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.file.BaseDirConverter
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.util.ConfigureUtil
import org.gradle.api.logging.Logging
import org.gradle.api.Script
import org.gradle.api.internal.file.FileOperations

abstract class DefaultScript extends BasicScript {
    private static final Logger LOGGER = Logging.getLogger(Script.class)
    private ServiceRegistry services
    private FileResolver resolver
    private ScriptHandler scriptHandler

    def void init(Object target, ServiceRegistry services) {
        super.init(target, services);
        this.services = services
        if (target instanceof FileOperations) {
            resolver = target.fileResolver
        } else if (scriptSource.sourceFile) {
            resolver = new BaseDirConverter(scriptSource.sourceFile.parentFile)
        } else {
            resolver = new IdentityFileResolver()
        }
        scriptHandler = services.get(ScriptHandler.class)
    }

    FileResolver getFileResolver() {
        resolver
    }

    void apply(Closure closure) {
        ObjectConfigurationAction action = new DefaultObjectConfigurationAction(fileResolver, services.get(ScriptPluginFactory.class), scriptTarget)
        ConfigureUtil.configure(closure, action)
        action.execute()
    }

    void apply(Map options) {
        ObjectConfigurationAction action = new DefaultObjectConfigurationAction(fileResolver, services.get(ScriptPluginFactory.class), scriptTarget)
        ConfigureUtil.configure(options, action)
        action.execute()
    }

    ScriptHandler getBuildscript() {
        return scriptHandler;
    }

    void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript())
    }

    File file(Object path) {
        if (scriptTarget instanceof FileOperations) {
            return scriptTarget.file(path)
        }
        return fileResolver.resolve(path)
    }

    ConfigurableFileCollection files(Object ... paths) {
        if (scriptTarget instanceof FileOperations) {
            return scriptTarget.files(paths)
        }
        fileResolver.resolveFiles(paths)
    }

    ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        if (scriptTarget instanceof FileOperations) {
            return scriptTarget.files(paths, configureClosure)
        }
        ConfigureUtil.configure(configureClosure, fileResolver.resolveFiles(paths))
    }

    public void captureStandardOutput(LogLevel level) {
        standardOutputRedirector.on(level);
    }

    public void disableStandardOutputCapture() {
        standardOutputRedirector.flush();
        standardOutputRedirector.off();
    }

    public Logger getLogger() {
        return LOGGER;
    }

    def String toString() {
        return "script"
    }
}
