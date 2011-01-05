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

import org.gradle.api.PathValidation
import org.gradle.api.Script
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileTree
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction
import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.logging.LoggingManager
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.tasks.WorkResult
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.util.ConfigureUtil
import org.gradle.process.ExecResult
import org.gradle.api.internal.file.*
import org.gradle.util.DeprecationLogger

abstract class DefaultScript extends BasicScript {
    private static final Logger LOGGER = Logging.getLogger(Script.class)
    private ServiceRegistry services
    private FileOperations fileOperations
    private LoggingManager loggingManager

    def void init(Object target, ServiceRegistry services) {
        super.init(target, services);
        this.services = services
        loggingManager = services.get(LoggingManager.class)
        if (target instanceof FileOperations) {
            fileOperations = target
        } else if (scriptSource.resource.file) {
            fileOperations = new DefaultFileOperations(new BaseDirConverter(scriptSource.resource.file.parentFile), null, null)
        } else {
            fileOperations = new DefaultFileOperations(new IdentityFileResolver(), null, null)
        }
    }

    FileResolver getFileResolver() {
        fileOperations.fileResolver
    }

    void apply(Closure closure) {
        ObjectConfigurationAction action = new DefaultObjectConfigurationAction(fileResolver, services.get(ScriptPluginFactory.class), scriptTarget)
        ConfigureUtil.configure(closure, action)
        action.execute()
    }

    void apply(Map options) {
        ObjectConfigurationAction action = new DefaultObjectConfigurationAction(fileResolver, services.get(ScriptPluginFactory.class), scriptTarget)
        ConfigureUtil.configureByMap(options, action)
        action.execute()
    }

    ScriptHandler getBuildscript() {
        return services.get(ScriptHandler.class);
    }

    void buildscript(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getBuildscript())
    }

    File file(Object path) {
        fileOperations.file(path)
    }

    File file(Object path, PathValidation validation) {
        fileOperations.file(path, validation)
    }

    URI uri(Object path) {
        fileOperations.uri(path)
    }

    ConfigurableFileCollection files(Object... paths) {
        fileOperations.files(paths)
    }

    ConfigurableFileCollection files(Object paths, Closure configureClosure) {
        fileOperations.files(paths, configureClosure)
    }

    String relativePath(Object path) {
        fileOperations.relativePath(path)
    }

    ConfigurableFileTree fileTree(Object baseDir) {
        fileOperations.fileTree(baseDir)
    }

    ConfigurableFileTree fileTree(Map args) {
        fileOperations.fileTree(args)
    }

    ConfigurableFileTree fileTree(Closure closure) {
        fileOperations.fileTree(closure)
    }

    FileTree zipTree(Object zipPath) {
        fileOperations.zipTree(zipPath)
    }

    FileTree tarTree(Object tarPath) {
        fileOperations.tarTree(tarPath)
    }

    WorkResult copy(Closure closure) {
        fileOperations.copy(closure)
    }

    CopySpec copySpec(Closure closure) {
        fileOperations.copySpec(closure)
    }

    File mkdir(Object path) {
        return fileOperations.mkdir(path);
    }

    boolean delete(Object... paths) {
        return fileOperations.delete(paths);
    }

    ExecResult javaexec(Closure closure) {
        return fileOperations.javaexec(closure);
    }

    ExecResult exec(Closure closure) {
        return fileOperations.exec(closure);
    }

    LoggingManager getLogging() {
        return loggingManager
    }

    public void captureStandardOutput(LogLevel level) {
        DeprecationLogger.nagUser('captureStandardOutput()', 'getLogging().captureStandardOutput()')
        logging.captureStandardOutput(level)
    }

    public void disableStandardOutputCapture() {
        DeprecationLogger.nagUser('disableStandardOutputCapture')
        logging.disableStandardOutputCapture()
    }

    public Logger getLogger() {
        return LOGGER;
    }

    def String toString() {
        return "script"
    }
}
