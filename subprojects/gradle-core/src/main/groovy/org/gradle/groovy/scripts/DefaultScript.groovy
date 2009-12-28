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
import org.gradle.configuration.ScriptObjectConfigurerFactory
import org.gradle.util.ConfigureUtil
import org.gradle.api.logging.Logging
import org.gradle.api.Script

abstract class DefaultScript extends BasicScript {
    private static final Logger LOGGER = Logging.getLogger(Script.class)
    private ServiceRegistry services
    private FileResolver resolver
    private ScriptHandler scriptHandler

    def void init(Object target, ServiceRegistry services) {
        super.init(target, services);
        this.services = services
        if (scriptSource.sourceFile) {
            resolver = new BaseDirConverter(scriptSource.sourceFile.parentFile)
        } else {
            resolver = new IdentityFileResolver()
        }
        scriptHandler = services.get(ScriptHandler.class)
    }

    void apply(Closure closure) {
        ObjectConfigurationAction action = new DefaultObjectConfigurationAction(resolver, services.get(ScriptObjectConfigurerFactory.class), scriptTarget)
        ConfigureUtil.configure(closure, action)
        action.execute()
    }

    ScriptHandler getBuildscript() {
        return scriptHandler;
    }

    void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript())
    }

    File file(Object path) {
        resolver.resolve(path)
    }

    ConfigurableFileCollection files(Object ... paths) {
        resolver.resolveFiles(paths)
    }

    ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, resolver.resolveFiles(paths))
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
